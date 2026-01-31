package net.skinmc.client.cape;

import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation EMPTY_CAPE_ID = ResourceLocation.fromNamespaceAndPath(SkinMCMod.MOD_ID, "textures/empty_cape");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SEC)).build();
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<ResourceLocation>> pending = new ConcurrentHashMap<>();

    public static PlayerCapeManager getInstance() { return Holder.INSTANCE; }
    private PlayerCapeManager() {}

    public Optional<ResourceLocation> getCapeTexture(UUID playerUuid) {
        if (playerUuid == null) return Optional.empty();
        CacheEntry entry = cache.get(playerUuid);
        if (entry != null && entry.expireAt > System.currentTimeMillis()) return Optional.ofNullable(entry.texture);
        if (entry != null) cache.remove(playerUuid);
        CompletableFuture<ResourceLocation> future = pending.computeIfAbsent(playerUuid, this::fetchCapeAsync);
        ResourceLocation now = future.getNow(null);
        if (now != null) return Optional.of(now);
        if (future.isCompletedExceptionally() || future.isCancelled()) return Optional.empty();
        return Optional.empty();
    }

    public Optional<ResourceLocation> getCapeTextureCachedOnly(UUID playerUuid) {
        if (playerUuid == null) return Optional.empty();
        CacheEntry entry = cache.get(playerUuid);
        if (entry == null || entry.expireAt <= System.currentTimeMillis()) return Optional.empty();
        return Optional.ofNullable(entry.texture);
    }

    public void prefetchCape(UUID playerUuid) {
        if (playerUuid == null || cache.containsKey(playerUuid)) return;
        pending.computeIfAbsent(playerUuid, this::fetchCapeAsync);
    }

    private CompletableFuture<ResourceLocation> fetchCapeAsync(UUID playerUuid) {
        String url = API_BASE + "/" + playerUuid;
        HttpRequest.Builder req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(TIMEOUT_SEC)).GET();
        if (API_AUTH_HEADER != null && !API_AUTH_HEADER.isBlank()) {
            if (API_AUTH_HEADER.contains(":")) {
                String[] p = API_AUTH_HEADER.split(":", 2);
                req.header(p[0].trim(), p[1].trim());
            } else req.header("Authorization", API_AUTH_HEADER);
        }

        CompletableFuture<ResourceLocation> result = new CompletableFuture<>();
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
            ResourceLocation id = (imageBytes != null && imageBytes.length > 0)
                    ? ResourceLocation.fromNamespaceAndPath(SkinMCMod.MOD_ID, CAPE_PREFIX + playerUuid.toString().replace("-", "_"))
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

    public static ResourceLocation getEmptyCapeTexture() { return EMPTY_CAPE_ID; }

    /** For SkinMCCapeResourceProvider: path must be skinmc_cape_<uuid_underscores>. */
    public byte[] getCapePngBytes(ResourceLocation id) {
        if (id == null || !SkinMCMod.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith(CAPE_PREFIX)) return null;
        String uuidStr = id.getPath().substring(CAPE_PREFIX.length()).replace('_', '-');
        UUID uuid;
        try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { return null; }
        CacheEntry entry = cache.get(uuid);
        return (entry != null && entry.expireAt > System.currentTimeMillis() && entry.pngBytes != null) ? entry.pngBytes : null;
    }

    public void clearCache() { cache.clear(); pending.clear(); }

    private static final class CacheEntry {
        final ResourceLocation texture;
        final byte[] pngBytes;
        final long expireAt;
        CacheEntry(ResourceLocation texture, byte[] pngBytes, long expireAt) { this.texture = texture; this.pngBytes = pngBytes; this.expireAt = expireAt; }
    }
    private static final class Holder { static final PlayerCapeManager INSTANCE = new PlayerCapeManager(); }
}
