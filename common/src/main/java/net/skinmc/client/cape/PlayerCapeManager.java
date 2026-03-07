package net.skinmc.client.cape;

import net.minecraft.resources.Identifier;
import net.skinmc.SkinMCMod;

import java.net.URI;

import static net.skinmc.SkinMCMod.BASE_URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Fetches SkinMC capes, caches PNG bytes + texture ID. Used by PlayerInfoSkinMixin and SkinMCCapeResourceProvider. */
public final class PlayerCapeManager {

    private static final String API_BASE = BASE_URL + "/api/v1/skinmcCape";
    private static final String API_AUTH_HEADER = null; // e.g. "Bearer KEY" if API requires auth
    private static final int TIMEOUT_SEC = 10;
    private static final long CACHE_EXPIRE_MS = 30 * 60 * 1000;

    private static final String CAPE_PREFIX = "skinmc_cape_";
    private static final String TEXTURE_PATH_PREFIX = "textures/";
    private static final String PNG_SUFFIX = ".png";
    private static final Identifier EMPTY_CAPE_ID = Identifier.fromNamespaceAndPath(SkinMCMod.MOD_ID, "empty_cape");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SEC)).build();
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Identifier>> pending = new ConcurrentHashMap<>();

    public static PlayerCapeManager getInstance() { return Holder.INSTANCE; }
    private PlayerCapeManager() {}

    public Optional<Identifier> getCapeTexture(UUID playerUuid) {
        if (playerUuid == null) return Optional.empty();
        CacheEntry entry = cache.get(playerUuid);
        if (entry != null && entry.expireAt > System.currentTimeMillis()) return Optional.ofNullable(entry.texture);
        if (entry != null) cache.remove(playerUuid);
        CompletableFuture<Identifier> future = pending.computeIfAbsent(playerUuid, this::fetchCapeAsync);
        Identifier now = future.getNow(null);
        if (now != null) return Optional.of(now);
        if (future.isCompletedExceptionally() || future.isCancelled()) return Optional.empty();
        return Optional.empty();
    }

    public Optional<Identifier> getCapeTextureCachedOnly(UUID playerUuid) {
        if (playerUuid == null) return Optional.empty();
        CacheEntry entry = cache.get(playerUuid);
        if (entry == null) return Optional.empty();
        if (entry.expireAt <= System.currentTimeMillis()) {
            cache.remove(playerUuid, entry);
            return Optional.empty();
        }
        return Optional.ofNullable(entry.texture);
    }

    public void prefetchCape(UUID playerUuid) {
        if (playerUuid == null) return;
        CacheEntry entry = cache.get(playerUuid);
        if (entry != null && entry.expireAt > System.currentTimeMillis()) return;
        if (entry != null) cache.remove(playerUuid, entry);
        pending.computeIfAbsent(playerUuid, this::fetchCapeAsync);
    }

    private CompletableFuture<Identifier> fetchCapeAsync(UUID playerUuid) {
        String url = API_BASE + "/" + playerUuid;
        HttpRequest.Builder req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(TIMEOUT_SEC)).GET();
        if (API_AUTH_HEADER != null && !API_AUTH_HEADER.isBlank()) {
            if (API_AUTH_HEADER.contains(":")) {
                String[] p = API_AUTH_HEADER.split(":", 2);
                req.header(p[0].trim(), p[1].trim());
            } else req.header("Authorization", API_AUTH_HEADER);
        }

        CompletableFuture<Identifier> result = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<byte[]> r = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) return null;
                byte[] body = r.body();
                return (body != null && body.length > 0) ? resolveCapeImageBytes(body, playerUuid) : null;
            } catch (Exception e) {
                SkinMCMod.LOGGER.warn("[SkinMC Cape] Fetch failed {}: {}", playerUuid, e.getMessage());
                return null;
            }
        }).whenComplete((imageBytes, ex) -> {
            if (ex != null) imageBytes = null;
            long expireAt = System.currentTimeMillis() + CACHE_EXPIRE_MS;
            Identifier id = (imageBytes != null && imageBytes.length > 0)
                    ? Identifier.fromNamespaceAndPath(SkinMCMod.MOD_ID, CAPE_PREFIX + playerUuid.toString().replace("-", "_"))
                    : null;
            cache.put(playerUuid, new CacheEntry(id, imageBytes, expireAt));
            pending.remove(playerUuid);
            result.complete(id);
        });
        return result;
    }

    private byte[] resolveCapeImageBytes(byte[] body, UUID playerUuid) {
        try {
            String str = new String(body, 0, Math.min(body.length, 2048), java.nio.charset.StandardCharsets.UTF_8);
            if (str.trim().startsWith("{")) {
                String url = extractUrlFromJson(str);
                if (url != null && !url.isBlank()) return fetchImageBytesFromUrl(url);
            }
        } catch (Exception ignored) {}
        if (body.length >= 8 && ((body[0] == (byte) 0x89 && body[1] == 'P' && body[2] == 'N' && body[3] == 'G') || (body[0] == (byte) 0xFF && body[1] == (byte) 0xD8))) {
            return body;
        }
        return null;
    }

    private static String extractUrlFromJson(String json) {
        for (String key : new String[]{"\"url\"", "\"texture\"", "\"capeUrl\"", "\"cape_url\""}) {
            int idx = json.indexOf(key);
            if (idx == -1) continue;
            int colon = json.indexOf(':', idx), start = json.indexOf('"', colon + 1), end = start == -1 ? -1 : json.indexOf('"', start + 1);
            if (end != -1) return json.substring(start + 1, end).replace("\\/", "/");
        }
        return null;
    }

    private byte[] fetchImageBytesFromUrl(String imageUrl) {
        try {
            HttpResponse<byte[]> r = httpClient.send(HttpRequest.newBuilder().uri(URI.create(imageUrl)).timeout(Duration.ofSeconds(TIMEOUT_SEC)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) return null;
            byte[] b = r.body();
            return (b != null && b.length > 0) ? b : null;
        } catch (Exception e) {
            SkinMCMod.LOGGER.warn("[SkinMC Cape] URL fetch failed: {}", e.getMessage());
            return null;
        }
    }

    public static Identifier getEmptyCapeTexture() { return EMPTY_CAPE_ID; }

    /**
     * For SkinMCCapeResourceProvider and texture lookups:
     * accepts either "<modid>:skinmc_cape_<uuid>" or "<modid>:textures/skinmc_cape_<uuid>.png".
     */
    public byte[] getCapePngBytes(Identifier id) {
        if (id == null || !SkinMCMod.MOD_ID.equals(id.getNamespace())) return null;
        String normalizedPath = normalizeCapePath(id.getPath());
        if (normalizedPath == null) return null;
        String uuidStr = normalizedPath.substring(CAPE_PREFIX.length()).replace('_', '-');
        UUID uuid;
        try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { return null; }
        CacheEntry entry = cache.get(uuid);
        return (entry != null && entry.expireAt > System.currentTimeMillis() && entry.pngBytes != null) ? entry.pngBytes : null;
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

    public void clearCache() { cache.clear(); pending.clear(); }

    private static final class CacheEntry {
        final Identifier texture;
        final byte[] pngBytes;
        final long expireAt;
        CacheEntry(Identifier texture, byte[] pngBytes, long expireAt) { this.texture = texture; this.pngBytes = pngBytes; this.expireAt = expireAt; }
    }
    private static final class Holder { static final PlayerCapeManager INSTANCE = new PlayerCapeManager(); }
}
