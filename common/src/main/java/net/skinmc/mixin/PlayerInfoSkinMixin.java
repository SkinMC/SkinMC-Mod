package net.skinmc.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.skinmc.client.cape.PlayerCapeManager;
import net.skinmc.client.config.CapeDisplay;
import net.skinmc.client.config.SkinMCConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Overrides getSkin() to use SkinMC cape (or empty) so vanilla CapeLayer renders it. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {

    @Shadow public abstract com.mojang.authlib.GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void skinmc_mod$overrideCape(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin original = cir.getReturnValue();
        if (original == null) return;
        CapeDisplay display = SkinMCConfig.getCapeDisplay();
        if (!display.useSkinMCCapes()) return;
        java.util.UUID playerId = getProfile().getId();
        if (playerId == null) return;

        PlayerCapeManager manager = PlayerCapeManager.getInstance();
        manager.prefetchCape(playerId);
        Optional<ResourceLocation> skinMcCape = manager.getCapeTextureCachedOnly(playerId);
        ResourceLocation capeTexture = null;
        if (skinMcCape.isPresent()) {
            ResourceLocation candidate = skinMcCape.get();
            if (candidate != null && manager.getCapePngBytes(candidate) != null) capeTexture = candidate;
        }
        if (capeTexture == null && !display.hideDefaultWhenNoSkinMC()) return;

        PlayerSkin modified = skinmc_mod$withCape(original, capeTexture);
        if (modified != null) cir.setReturnValue(modified);
    }

    @Unique
    private static PlayerSkin skinmc_mod$withCape(PlayerSkin skin, ResourceLocation capeTexture) {
        try {
            return new PlayerSkin(skin.texture(), skin.textureUrl(), capeTexture, skin.elytraTexture(), skin.model(), skin.secure());
        } catch (Exception e) {
            return null;
        }
    }
}
