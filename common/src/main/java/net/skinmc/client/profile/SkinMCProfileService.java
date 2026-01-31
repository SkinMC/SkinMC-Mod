package net.skinmc.client.profile;

import net.skinmc.SkinMCMod;

import java.net.URI;

import static net.skinmc.SkinMCMod.BASE_URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches SkinMC passport profile by UUID. Async, time-based cache (60s), never blocks game/render thread.
 * Expects API response with nested xp, country object, role, verified, profile_url, etc.
 */
public final class SkinMCProfileService {

    private static final String API_BASE = BASE_URL + "/api/v1/profile/passport";
    private static final int TIMEOUT_SEC = 10;
    private static final long CACHE_EXPIRE_MS = 60_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
            .build();
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerProfile>> pending = new ConcurrentHashMap<>();

    private static final SkinMCProfileService INSTANCE = new SkinMCProfileService();

    public static SkinMCProfileService getInstance() { return INSTANCE; }

    private SkinMCProfileService() {}

    public CompletableFuture<PlayerProfile> fetchProfile(UUID playerUuid) {
        if (playerUuid == null) return CompletableFuture.completedFuture(null);
        CacheEntry entry = cache.get(playerUuid);
        if (entry != null && entry.expireAt > System.currentTimeMillis())
            return CompletableFuture.completedFuture(entry.profile);
        if (entry != null) cache.remove(playerUuid);
        return pending.computeIfAbsent(playerUuid, this::fetchAsync);
    }

    public PlayerProfile getCachedOnly(UUID playerUuid) {
        if (playerUuid == null) return null;
        CacheEntry entry = cache.get(playerUuid);
        if (entry == null || entry.expireAt <= System.currentTimeMillis()) return null;
        return entry.profile;
    }

    private CompletableFuture<PlayerProfile> fetchAsync(UUID playerUuid) {
        String url = API_BASE + "?profile_uuid=" + playerUuid.toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .GET()
                .build();

        CompletableFuture<PlayerProfile> result = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> r = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) return null;
                String body = r.body();
                return body != null ? parsePassport(body, playerUuid) : null;
            } catch (Exception e) {
                SkinMCMod.LOGGER.warn("[SkinMC Profile] Fetch failed {}: {}", playerUuid, e.getMessage());
                return null;
            }
        }).whenComplete((profile, ex) -> {
            if (ex != null) profile = null;
            long expireAt = System.currentTimeMillis() + CACHE_EXPIRE_MS;
            cache.put(playerUuid, new CacheEntry(profile, expireAt));
            pending.remove(playerUuid);
            result.complete(profile);
        });
        return result;
    }

    private static PlayerProfile parsePassport(String json, UUID uuid) {
        try {
            String username = extractString(json, "username");
            if (username == null) username = extractString(json, "name");
            String avatarUrl = extractString(json, "avatar_url");
            String bio = extractString(json, "bio");
            String role = extractString(json, "role");
            String profileUrl = extractString(json, "profile_url");
            boolean isOnline = extractBool(json, "is_online");
            boolean verified = extractBool(json, "verified");

            PlayerProfile.Country country = parseCountry(json);
            PlayerProfile.XpInfo xpInfo = parseXp(json);

            return new PlayerProfile(uuid, username, avatarUrl, bio,
                    role, profileUrl, isOnline, verified,
                    country, xpInfo);
        } catch (Exception e) {
            SkinMCMod.LOGGER.warn("[SkinMC Profile] Parse failed: {}", e.getMessage());
            return null;
        }
    }

    private static PlayerProfile.Country parseCountry(String json) {
        String countryJson = extractSubObject(json, "country");
        if (countryJson == null) return null;
        String code = extractString(countryJson, "code");
        String name = extractString(countryJson, "name");
        if (code == null && name == null) return null;
        return new PlayerProfile.Country(code, name);
    }

    private static PlayerProfile.XpInfo parseXp(String json) {
        String xpJson = extractSubObject(json, "xp");
        if (xpJson == null) return null;
        int total = extractInt(xpJson, "total");
        double progressToNext = extractDouble(xpJson, "progress_to_next");
        int xpInCurrentLevel = extractInt(xpJson, "xp_in_current_level");
        int xpNeededForNextLevel = extractInt(xpJson, "xp_needed_for_next_level");
        int currentLevelId = 0;
        int nextLevelId = 0;
        String currentLevelJson = extractSubObject(xpJson, "current_level");
        if (currentLevelJson != null) currentLevelId = extractInt(currentLevelJson, "id");
        String nextLevelJson = extractSubObject(xpJson, "next_level");
        if (nextLevelJson != null) nextLevelId = extractInt(nextLevelJson, "id");
        return new PlayerProfile.XpInfo(total, currentLevelId, nextLevelId,
                xpInCurrentLevel, xpNeededForNextLevel, progressToNext);
    }

    private static String extractSubObject(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == ':')) start++;
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first != '{' && first != '[') return null;
        char open = first;
        char close = first == '{' ? '}' : ']';
        int depth = 1;
        int end = start + 1;
        while (end < json.length() && depth > 0) {
            char c = json.charAt(end);
            if (c == open) depth++;
            else if (c == close) depth--;
            end++;
        }
        if (depth != 0) return null;
        return json.substring(start, end);
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return null;
        int start = json.indexOf('"', colon + 1);
        if (start == -1) return null;
        int end = start + 1;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') { end += 2; continue; }
            if (c == '"') break;
            end++;
        }
        if (end >= json.length()) return null;
        String value = json.substring(start + 1, end).replace("\\/", "/");
        return "null".equals(value) || value.isEmpty() ? null : value;
    }

    private static int extractInt(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return 0;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return 0;
        int start = colon + 1;
        while (start < json.length() && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == ':')) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == start) return 0;
        try {
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double extractDouble(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return 0.0;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return 0.0;
        int start = colon + 1;
        while (start < json.length() && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == ':')) start++;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == 'e' || c == 'E') end++;
            else break;
        }
        if (end == start) return 0.0;
        try {
            return Double.parseDouble(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean extractBool(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return false;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return false;
        int start = colon + 1;
        while (start < json.length() && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == ':')) start++;
        if (start + 4 <= json.length() && "true".equalsIgnoreCase(json.substring(start, start + 4))) return true;
        if (start + 5 <= json.length() && "false".equalsIgnoreCase(json.substring(start, Math.min(start + 5, json.length())))) return false;
        return false;
    }

    public void clearCache() { cache.clear(); pending.clear(); }

    private static final class CacheEntry {
        final PlayerProfile profile;
        final long expireAt;
        CacheEntry(PlayerProfile profile, long expireAt) { this.profile = profile; this.expireAt = expireAt; }
    }
}
