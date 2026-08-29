package net.skinmc.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.resources.SkinManager;
import net.skinmc.client.nametag.NametagProfileIngestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures signed texture properties at the same point vanilla consumes them. */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin {

    @Inject(
            method = "get(Lcom/mojang/authlib/GameProfile;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD")
    )
    private void skinmc_mod$submitSignedProfile(GameProfile profile, CallbackInfoReturnable<?> cir) {
        NametagProfileIngestor.getInstance().submit(profile);
    }
}
