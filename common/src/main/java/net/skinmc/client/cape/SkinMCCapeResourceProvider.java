package net.skinmc.client.cape;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    public static Optional<Resource> getCapeResource(ResourceLocation location) {
        if (location == null || !SkinMCMod.MOD_ID.equals(location.getNamespace()) || !location.getPath().startsWith(CAPE_PREFIX)) {
            return Optional.empty();
        }
        byte[] bytes = PlayerCapeManager.getInstance().getCapePngBytes(location);
        if (bytes == null || bytes.length == 0) return Optional.empty();
        return Optional.of(DummyPack.INSTANCE.createResource(() -> new ByteArrayInputStream(bytes)));
    }

    private enum DummyPack implements PackResources {
        INSTANCE;

        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) { return null; }
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
