package net.skinmc.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.skinmc.SkinMCMod;
import net.skinmc.client.cape.PlayerCapeManager;
import net.skinmc.client.config.SkinMCConfig;
import net.skinmc.client.profile.SkinMCProfileClient;
import net.skinmc.client.profile.SkinMCProfileScreen;
import net.skinmc.client.profile.OpenProfilePacket;

public final class SkinMCModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SkinMCConfig.setConfigPath(FabricLoader.getInstance().getConfigDir().resolve("skinmc_mod.json"));
        SkinMCConfig.load();

        PlayerCapeManager.getInstance();
        SkinMCProfileClient.init();
        OpenProfilePacket.registerClientReceiver();

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && SkinMCConfig.isEnableShiftRightClickPlayer() && player.isShiftKeyDown() && entity instanceof Player target) {
                SkinMCProfileScreen.open(target);
            }
            return InteractionResult.PASS;
        });

        if (SkinMCMod.LOGGER.isDebugEnabled()) {
            SkinMCMod.LOGGER.debug("[SkinMC Cape] SkinMC cape system initialized (Fabric)");
        }
    }
}
