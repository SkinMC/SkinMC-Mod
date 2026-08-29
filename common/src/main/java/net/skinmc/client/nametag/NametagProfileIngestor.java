package net.skinmc.client.nametag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.skinmc.SkinMCMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forwards Mojang-signed public texture properties to Namet.ag.
 *
 * Requests are asynchronous and best-effort. Namet.ag can independently validate the signature,
 * and failures never affect Minecraft's own skin loading.
 */
public final class NametagProfileIngestor {

    private static final URI INGEST_URI = URI.create("https://ingest.namet.ag/");
    private static final String TEXTURES_PROPERTY = "textures";
    private static final String USER_AGENT = "SkinMC-Mod Namet.ag Ingestor";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(10);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMinutes(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    private static final int MAX_TEXTURE_VALUE_LENGTH = 32 * 1024;
    private static final int MAX_SIGNATURE_LENGTH = 8 * 1024;
    private static final int MAX_TRACKED_PROPERTIES = 4096;
    private static final long IN_FLIGHT = -1L;
    private static final long COMPLETE = Long.MAX_VALUE;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final HttpClient httpClient;
    private final Map<String, Long> submissionStates = new ConcurrentHashMap<>();
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public static NametagProfileIngestor getInstance() {
        return Holder.INSTANCE;
    }

    private NametagProfileIngestor() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Queues the first valid signed textures property on the profile, if one exists. */
    public void submit(GameProfile profile) {
        if (profile == null) return;

        for (Property property : profile.properties().get(TEXTURES_PROPERTY)) {
            if (isValid(property)) {
                submit(property);
                return;
            }
        }
    }

    private void submit(Property property) {
        String signature = property.signature();
        long now = System.currentTimeMillis();
        if (!claimAttempt(signature, now)) return;

        String json = GSON.toJson(new IngestRequest(property.value(), signature));
        HttpRequest request = HttpRequest.newBuilder(INGEST_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> completeAttempt(signature, response, error));
        } catch (RuntimeException error) {
            completeAttempt(signature, null, error);
        }
    }

    private boolean claimAttempt(String signature, long now) {
        while (true) {
            Long state = submissionStates.get(signature);
            if (state == null) {
                if (!makeRoomIfNeeded()) return false;
                if (submissionStates.putIfAbsent(signature, IN_FLIGHT) == null) return true;
            } else {
                if (state == IN_FLIGHT || state == COMPLETE || state > now) return false;
                if (submissionStates.replace(signature, state, IN_FLIGHT)) return true;
            }
        }
    }

    private void completeAttempt(String signature, HttpResponse<Void> response, Throwable error) {
        if (error == null && response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
            submissionStates.replace(signature, IN_FLIGHT, COMPLETE);
            if (SkinMCMod.LOGGER.isDebugEnabled()) {
                SkinMCMod.LOGGER.debug("[Namet.ag] Submitted a signed player texture profile");
            }
            return;
        }

        if (error == null && response != null && !isRetryable(response.statusCode())) {
            submissionStates.replace(signature, IN_FLIGHT, COMPLETE);
        } else {
            long retryAt = System.currentTimeMillis() + retryDelay(response).toMillis();
            submissionStates.replace(signature, IN_FLIGHT, retryAt);
        }

        logFailure(response, error);
    }

    private boolean makeRoomIfNeeded() {
        if (submissionStates.size() < MAX_TRACKED_PROPERTIES) return true;

        for (Map.Entry<String, Long> entry : submissionStates.entrySet()) {
            if (entry.getValue() != IN_FLIGHT && submissionStates.remove(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private void logFailure(HttpResponse<Void> response, Throwable error) {
        String detail = error != null
                ? error.getClass().getSimpleName() + ": " + error.getMessage()
                : "HTTP " + (response == null ? "unknown" : response.statusCode());

        if (failureLogged.compareAndSet(false, true)) {
            SkinMCMod.LOGGER.warn("[Namet.ag] Profile ingestion failed; skin loading will continue normally ({})", detail);
        } else if (SkinMCMod.LOGGER.isDebugEnabled()) {
            SkinMCMod.LOGGER.debug("[Namet.ag] Profile ingestion failed ({})", detail);
        }
    }

    private static boolean isValid(Property property) {
        if (property == null || !property.hasSignature()) return false;
        String value = property.value();
        String signature = property.signature();
        return value != null && !value.isBlank() && value.length() <= MAX_TEXTURE_VALUE_LENGTH
                && signature != null && !signature.isBlank() && signature.length() <= MAX_SIGNATURE_LENGTH;
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    private static Duration retryDelay(HttpResponse<?> response) {
        if (response == null) return DEFAULT_RETRY_DELAY;

        return response.headers().firstValue("Retry-After")
                .map(NametagProfileIngestor::parseRetryAfter)
                .orElse(DEFAULT_RETRY_DELAY);
    }

    private static Duration parseRetryAfter(String value) {
        try {
            long seconds = Long.parseLong(value);
            return boundedRetryDelay(Duration.ofSeconds(Math.max(0, seconds)));
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                return boundedRetryDelay(delay);
            } catch (DateTimeParseException ignoredDate) {
                return DEFAULT_RETRY_DELAY;
            }
        }
    }

    private static Duration boundedRetryDelay(Duration delay) {
        if (delay.compareTo(MIN_RETRY_DELAY) < 0) return MIN_RETRY_DELAY;
        if (delay.compareTo(MAX_RETRY_DELAY) > 0) return MAX_RETRY_DELAY;
        return delay;
    }

    private record IngestRequest(String value, String signature) {}

    private static final class Holder {
        private static final NametagProfileIngestor INSTANCE = new NametagProfileIngestor();
    }
}
