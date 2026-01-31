package net.skinmc.neoforge.client;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.skinmc.SkinMCMod;
import net.skinmc.client.cape.PlayerCapeManager;
import net.skinmc.client.config.SkinMCConfig;
import net.skinmc.client.config.SkinMCConfigScreen;
import net.skinmc.client.profile.OpenProfilePacket;
import net.skinmc.client.profile.SkinMCProfileClient;
import net.skinmc.client.profile.SkinMCProfileScreen;
import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

/**
 * Initializes SkinMC config and config screen on NeoForge client.
 * Cape override is done via PlayerInfoSkinMixin (getSkin).
 */
public final class SkinMCModNeoForgeClient {

    private static boolean configScreenRegistered;

    @SuppressWarnings("unchecked")
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        SkinMCConfig.setConfigPath(FMLPaths.CONFIGDIR.get().resolve("skinmc_mod.json"));
        SkinMCConfig.load();

        if (!configScreenRegistered) {
            configScreenRegistered = true;
            Supplier<IConfigScreenFactory> factory = () -> (minecraft, modListScreen) -> new SkinMCConfigScreen(modListScreen);
            ModList.get().getModContainerById(SkinMCMod.MOD_ID).ifPresent(container ->
                    container.registerExtensionPoint(IConfigScreenFactory.class, factory)
            );
        }

        PlayerCapeManager.getInstance();
        SkinMCProfileClient.init();
        OpenProfilePacket.registerClientReceiver();

        if (SkinMCMod.LOGGER.isDebugEnabled()) {
            SkinMCMod.LOGGER.debug("[SkinMC Cape] SkinMC cape system initialized (NeoForge)");
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() && SkinMCConfig.isEnableShiftRightClickPlayer() && event.getEntity().isShiftKeyDown() && event.getTarget() instanceof Player target) {
            SkinMCProfileScreen.open(target);
        }
    }
}
