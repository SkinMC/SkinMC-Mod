package net.skinmc.client.cape;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.skinmc.SkinMCMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.skinmc.SkinMCMod.BASE_URL;

/** Fetches, refreshes, and serves SkinMC cape textures without blocking the render thread. */
public final class PlayerCapeManager {

    private static final String API_BASE = BASE_URL + "/api/v1/skinmcCape";
    private static final String API_AUTH_HEADER = null; // e.g. "Bearer KEY" if API requires auth

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REFRESH_DELAY = Duration.ofSeconds(60);
    private static final Duration MIN_REFRESH_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_REFRESH_DELAY = Duration.ofMinutes(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);
    private static final Duration REQUEST_TOKEN_REFILL_INTERVAL = Duration.ofSeconds(4);
    private static final Duration IDLE_REFRESH_CHECK_DELAY = Duration.ofSeconds(1);
    private static final int REQUEST_BURST_CAPACITY = 5;
    private static final int MAX_CAPE_BYTES = 2 * 1024 * 1024;

    private static final String CAPE_PREFIX = "skinmc_cape_";
    private static final String TEXTURE_PATH_PREFIX = "textures/";
    private static final String PNG_SUFFIX = ".png";
    private static final Identifier EMPTY_CAPE_ID = Identifier.fromNamespaceAndPath(SkinMCMod.MOD_ID, "empty_cape");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Identifier>> pending = new ConcurrentHashMap<>();
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private volatile long nextRefreshDispatchAt;
    private int availableRequestTokens = REQUEST_BURST_CAPACITY;
    private long nextTokenRefillAt;

    public static PlayerCapeManager getInstance() {
        return Holder.INSTANCE;
    }

    private PlayerCapeManager() {}

    /** Registers the cross-platform refresh tick once. */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            ClientTickEvent.CLIENT_POST.register(this::tick);
        }
    }

    public Optional<Identifier> getCapeTexture(UUID playerUuid) {
        prefetchCape(playerUuid);
        return getCapeTextureCachedOnly(playerUuid);
    }

    public Optional<Identifier> getCapeTextureCachedOnly(UUID playerUuid) {
        if (playerUuid == null) return Optional.empty();
        CacheEntry entry = cache.get(playerUuid);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.texture);
    }

    /** Starts the initial lookup. Existing entries are refreshed by the paced client tick. */
    public void prefetchCape(UUID playerUuid) {
        if (playerUuid == null || cache.containsKey(playerUuid)) return;
        queued.add(playerUuid);
    }

    private void tick(Minecraft minecraft) {
        long now = System.nanoTime();
        if (now - nextRefreshDispatchAt < 0) return;
        refillRequestTokens(now);
        if (availableRequestTokens == 0) {
            nextRefreshDispatchAt = nextTokenRefillAt;
            return;
        }

        UUID localPlayerId = minecraft.getUser().getProfileId();
        if (localPlayerId != null) {
            if (!cache.containsKey(localPlayerId) && requestFetch(localPlayerId)) {
                queued.remove(localPlayerId);
                consumeRequestToken(now);
                return;
            }
            if (tryRefresh(localPlayerId, now)) {
                consumeRequestToken(now);
                return;
            }
        }

        for (UUID playerUuid : queued) {
            if (playerUuid.equals(localPlayerId)) continue;
            if (cache.containsKey(playerUuid)) {
                queued.remove(playerUuid);
            } else if (requestFetch(playerUuid)) {
                queued.remove(playerUuid);
                consumeRequestToken(now);
                return;
            }
        }

        for (Map.Entry<UUID, CacheEntry> entry : cache.entrySet()) {
            if (!entry.getKey().equals(localPlayerId) && isDue(entry.getValue(), now) && requestFetch(entry.getKey())) {
                consumeRequestToken(now);
                return;
            }
        }

        nextRefreshDispatchAt = now + IDLE_REFRESH_CHECK_DELAY.toNanos();
    }

    private void refillRequestTokens(long now) {
        long interval = REQUEST_TOKEN_REFILL_INTERVAL.toNanos();
        if (nextTokenRefillAt == 0) {
            nextTokenRefillAt = now + interval;
            return;
        }
        if (now - nextTokenRefillAt < 0) return;

        long elapsedIntervals = 1 + (now - nextTokenRefillAt) / interval;
        availableRequestTokens = (int) Math.min(REQUEST_BURST_CAPACITY, availableRequestTokens + elapsedIntervals);
        nextTokenRefillAt += elapsedIntervals * interval;
    }

    private void consumeRequestToken(long now) {
        availableRequestTokens--;
        nextRefreshDispatchAt = availableRequestTokens == 0
                ? nextTokenRefillAt
                : now;
    }

    private boolean tryRefresh(UUID playerUuid, long now) {
        CacheEntry entry = cache.get(playerUuid);
        return entry != null && isDue(entry, now) && requestFetch(playerUuid);
    }

    private static boolean isDue(CacheEntry entry, long now) {
        return now - entry.refreshAt >= 0;
    }

    private boolean requestFetch(UUID playerUuid) {
        CompletableFuture<Identifier> gate = new CompletableFuture<>();
        if (pending.putIfAbsent(playerUuid, gate) != null) return false;

        fetchCapeAsync(playerUuid).whenComplete((fetchResult, error) -> {
            try {
                FetchResult result = error == null
                        ? fetchResult
                        : FetchResult.failure(error.getClass().getSimpleName() + ": " + error.getMessage());
                gate.complete(applyFetchResult(playerUuid, result));
            } catch (RuntimeException applyError) {
                gate.completeExceptionally(applyError);
            } finally {
                pending.remove(playerUuid, gate);
            }
        });
        return true;
    }

    private CompletableFuture<FetchResult> fetchCapeAsync(UUID playerUuid) {
        HttpRequest request = buildApiRequest(playerUuid);
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> resolveApiResponse(response, playerUuid));
    }

    private HttpRequest buildApiRequest(UUID playerUuid) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/" + playerUuid))
                .timeout(REQUEST_TIMEOUT)
                .GET();
        if (API_AUTH_HEADER != null && !API_AUTH_HEADER.isBlank()) {
            if (API_AUTH_HEADER.contains(":")) {
                String[] parts = API_AUTH_HEADER.split(":", 2);
                request.header(parts[0].trim(), parts[1].trim());
            } else {
                request.header("Authorization", API_AUTH_HEADER);
            }
        }
        return request.build();
    }

    private CompletableFuture<FetchResult> resolveApiResponse(HttpResponse<byte[]> response, UUID playerUuid) {
        Duration refreshDelay = refreshDelay(response);
        int statusCode = response.statusCode();
        if (statusCode == 204 || statusCode == 404) {
            return CompletableFuture.completedFuture(FetchResult.authoritative(null, refreshDelay));
        }
        if (statusCode != 200) {
            return CompletableFuture.completedFuture(FetchResult.failure("HTTP " + statusCode));
        }

        byte[] body = response.body();
        if (isSupportedImage(body)) {
            return CompletableFuture.completedFuture(FetchResult.authoritative(body, refreshDelay));
        }

        String imageUrl = extractImageUrl(body);
        if (imageUrl == null) {
            return CompletableFuture.completedFuture(FetchResult.failure("invalid cape payload for " + playerUuid));
        }

        return fetchImageBytesFromUrl(imageUrl)
                .thenApply(bytes -> bytes == null
                        ? FetchResult.failure("cape image download failed for " + playerUuid)
                        : FetchResult.authoritative(bytes, refreshDelay));
    }

    private CompletableFuture<byte[]> fetchImageBytesFromUrl(String imageUrl) {
        final URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException error) {
            return CompletableFuture.completedFuture(null);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> response.statusCode() == 200 && isSupportedImage(response.body())
                        ? response.body()
                        : null);
    }

    private Identifier applyFetchResult(UUID playerUuid, FetchResult result) {
        long now = System.nanoTime();
        CacheEntry previous = cache.get(playerUuid);

        if (!result.authoritative) {
            long retryAt = now + RETRY_DELAY.toNanos();
            CacheEntry retained = previous == null
                    ? new CacheEntry(null, null, retryAt)
                    : new CacheEntry(previous.texture, previous.pngBytes, retryAt);
            cache.put(playerUuid, retained);
            logFailure(playerUuid, result.failure);
            return retained.texture;
        }

        byte[] imageBytes = result.imageBytes;
        Identifier texture = imageBytes == null
                ? null
                : Identifier.fromNamespaceAndPath(SkinMCMod.MOD_ID, CAPE_PREFIX + playerUuid.toString().replace("-", "_"));
        CacheEntry updated = new CacheEntry(texture, imageBytes, now + result.refreshDelay.toNanos());
        cache.put(playerUuid, updated);

        boolean changed = previous != null && !Arrays.equals(previous.pngBytes, imageBytes);
        if (changed && previous.texture != null) {
            invalidateTexture(previous.texture);
        }
        if (changed && SkinMCMod.LOGGER.isDebugEnabled()) {
            SkinMCMod.LOGGER.debug("[SkinMC Cape] Refreshed changed cape for {}", playerUuid);
        }
        return texture;
    }

    private void invalidateTexture(Identifier texture) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            try {
                // ResourceTexture renders and caches the resolved textures/<path>.png ID,
                // not the logical ClientAsset ID stored in PlayerSkin.
                Identifier texturePath = texture.withPath(
                        path -> TEXTURE_PATH_PREFIX + path + PNG_SUFFIX
                );
                minecraft.getTextureManager().release(texturePath);
                // Also release the logical ID for compatibility with loaders or
                // Minecraft versions that register ClientAsset textures directly.
                minecraft.getTextureManager().release(texture);
            } catch (RuntimeException error) {
                SkinMCMod.LOGGER.warn("[SkinMC Cape] Could not reload refreshed texture {}: {}", texture, error.getMessage());
            }
        });
    }

    private void logFailure(UUID playerUuid, String detail) {
        if (failureLogged.compareAndSet(false, true)) {
            SkinMCMod.LOGGER.warn("[SkinMC Cape] Fetch failed for {}: {}; cached cape retained", playerUuid, detail);
        } else if (SkinMCMod.LOGGER.isDebugEnabled()) {
            SkinMCMod.LOGGER.debug("[SkinMC Cape] Fetch failed for {}: {}", playerUuid, detail);
        }
    }

    private static Duration refreshDelay(HttpResponse<?> response) {
        String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
        for (String directive : cacheControl.split(",")) {
            String value = directive.trim().toLowerCase(java.util.Locale.ROOT);
            if (!value.startsWith("max-age=")) continue;
            try {
                return clampRefreshDelay(Duration.ofSeconds(Long.parseLong(value.substring("max-age=".length()))));
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        return DEFAULT_REFRESH_DELAY;
    }

    private static Duration clampRefreshDelay(Duration delay) {
        if (delay.compareTo(MIN_REFRESH_DELAY) < 0) return MIN_REFRESH_DELAY;
        if (delay.compareTo(MAX_REFRESH_DELAY) > 0) return MAX_REFRESH_DELAY;
        return delay;
    }

    private static boolean isSupportedImage(byte[] bytes) {
        if (bytes == null || bytes.length < 8 || bytes.length > MAX_CAPE_BYTES) return false;
        boolean png = bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpeg = bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8;
        return png || jpeg;
    }

    private static String extractImageUrl(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            String json = new String(body, 0, Math.min(body.length, 2048), StandardCharsets.UTF_8);
            if (!json.trim().startsWith("{")) return null;
            for (String key : new String[]{"\"url\"", "\"texture\"", "\"capeUrl\"", "\"cape_url\""}) {
                int keyIndex = json.indexOf(key);
                if (keyIndex == -1) continue;
                int colon = json.indexOf(':', keyIndex);
                int start = colon == -1 ? -1 : json.indexOf('"', colon + 1);
                int end = start == -1 ? -1 : json.indexOf('"', start + 1);
                if (end != -1) return json.substring(start + 1, end).replace("\\/", "/");
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    public static Identifier getEmptyCapeTexture() {
        return EMPTY_CAPE_ID;
    }

    /**
     * Accepts either "&lt;modid&gt;:skinmc_cape_&lt;uuid&gt;" or
     * "&lt;modid&gt;:textures/skinmc_cape_&lt;uuid&gt;.png".
     */
    public byte[] getCapePngBytes(Identifier id) {
        if (id == null || !SkinMCMod.MOD_ID.equals(id.getNamespace())) return null;
        String normalizedPath = normalizeCapePath(id.getPath());
        if (normalizedPath == null) return null;
        String uuidString = normalizedPath.substring(CAPE_PREFIX.length()).replace('_', '-');
        final UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException error) {
            return null;
        }
        CacheEntry entry = cache.get(uuid);
        return entry == null ? null : entry.pngBytes;
    }

    private static String normalizeCapePath(String path) {
        if (path == null || path.isBlank()) return null;
        String normalized = path;
        if (normalized.startsWith(TEXTURE_PATH_PREFIX)) {
            normalized = normalized.substring(TEXTURE_PATH_PREFIX.length());
        }
        if (normalized.endsWith(PNG_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - PNG_SUFFIX.length());
        }
        return normalized.startsWith(CAPE_PREFIX) ? normalized : null;
    }

    public void clearCache() {
        cache.values().stream()
                .map(entry -> entry.texture)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(this::invalidateTexture);
        cache.clear();
        pending.clear();
        queued.clear();
    }

    private record CacheEntry(Identifier texture, byte[] pngBytes, long refreshAt) {}

    private record FetchResult(boolean authoritative, byte[] imageBytes, Duration refreshDelay, String failure) {
        static FetchResult authoritative(byte[] imageBytes, Duration refreshDelay) {
            return new FetchResult(true, imageBytes, refreshDelay, null);
        }

        static FetchResult failure(String detail) {
            return new FetchResult(false, null, RETRY_DELAY, detail == null ? "unknown error" : detail);
        }
    }

    private static final class Holder {
        private static final PlayerCapeManager INSTANCE = new PlayerCapeManager();
    }
}
