package net.skinmc.client.profile;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client-only: registers keybind and handles key press to open SkinMC profile.
 * Call init() from Fabric/NeoForge client init.
 */
public final class SkinMCProfileClient {

    public static final String KEY_CATEGORY = "skinmc_mod.key.category";
    public static final String KEY_OPEN_PROFILE = "skinmc_mod.key.open_profile";

    public static final KeyMapping OPEN_PROFILE_KEY = new KeyMapping(
            KEY_OPEN_PROFILE,
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            com.mojang.blaze3d.platform.InputConstants.KEY_P,
            KEY_CATEGORY
    );

    public static void init() {
        KeyMappingRegistry.register(OPEN_PROFILE_KEY);
        ClientTickEvent.CLIENT_POST.register(minecraft -> {
            while (OPEN_PROFILE_KEY.consumeClick()) {
                openProfileForCurrentTarget(minecraft);
            }
        });
    }

    /**
     * If the player is looking at another Player, open that player's profile; otherwise open local player's profile.
     */
    public static void openProfileForCurrentTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        Player target = getTargetedPlayer(mc);
        if (target != null) {
            SkinMCProfileScreen.open(target);
        } else {
            SkinMCProfileScreen.open(mc.player);
        }
    }

    /**
     * Returns the player the local player is looking at, or null.
     */
    public static Player getTargetedPlayer(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) return null;
        EntityHitResult entityHit = (EntityHitResult) mc.hitResult;
        Entity e = entityHit.getEntity();
        return e instanceof Player ? (Player) e : null;
    }
}
