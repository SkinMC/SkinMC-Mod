package net.skinmc.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.skinmc.client.cape.SkinMCCapeResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Serves SkinMC cape textures from cache so ResourceManager finds them (avoids "Missing resource"). */
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerSkinMCMixin {

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void skinmc_mod$serveCapeTexture(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        SkinMCCapeResourceProvider.getCapeResource(location).ifPresent(r -> { cir.setReturnValue(Optional.of(r)); cir.cancel(); });
    }
}
