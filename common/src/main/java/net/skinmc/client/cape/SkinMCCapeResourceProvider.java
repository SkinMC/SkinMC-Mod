package net.skinmc.client.cape;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.skinmc.SkinMCMod;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/** Serves our cape textures to ResourceManager (1.21.4 loads capes via ResourceManager). If a future MC version uses TextureManager only, this + FallbackResourceManager mixin can be removed. */
public final class SkinMCCapeResourceProvider {

    private static final String CAPE_PREFIX = "skinmc_cape_";
    private static final String TEXTURE_PATH_PREFIX = "textures/";
    private static final String PNG_SUFFIX = ".png";

    public static Optional<Resource> getCapeResource(Identifier location) {
        if (location == null || !SkinMCMod.MOD_ID.equals(location.getNamespace())) {
            return Optional.empty();
        }
        String normalizedPath = normalizeCapePath(location.getPath());
        if (normalizedPath == null) return Optional.empty();

        Identifier normalizedId = Identifier.fromNamespaceAndPath(location.getNamespace(), normalizedPath);
        byte[] bytes = PlayerCapeManager.getInstance().getCapePngBytes(normalizedId);
        if (bytes == null || bytes.length == 0) return Optional.empty();
        return Optional.of(DummyPack.INSTANCE.createResource(() -> new ByteArrayInputStream(bytes)));
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

    private enum DummyPack implements PackResources {
        INSTANCE;

        @Override public IoSupplier<InputStream> getResource(PackType type, Identifier location) { return null; }
        @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {}
        @Override public Set<String> getNamespaces(PackType type) { return Collections.singleton(SkinMCMod.MOD_ID); }
        @Override public <T> T getMetadataSection(MetadataSectionType<T> type) { return null; }
        @Override public String packId() { return SkinMCMod.MOD_ID + ":cape"; }
        @Override public IoSupplier<InputStream> getRootResource(String... path) { return null; }
        @Override public PackLocationInfo location() { return new PackLocationInfo(packId(), Component.literal("SkinMC Cape"), PackSource.BUILT_IN, Optional.empty()); }
        @Override public Optional<net.minecraft.server.packs.repository.KnownPack> knownPackInfo() { return Optional.empty(); }
        @Override public void close() {}

        Resource createResource(IoSupplier<InputStream> streamSupplier) { return new Resource(this, streamSupplier); }
    }
}
