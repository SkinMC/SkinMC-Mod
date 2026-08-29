package net.skinmc.neoforge.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.skinmc.SkinMCMod;
import net.skinmc.client.cape.PlayerCapeManager;
import net.skinmc.client.config.SkinMCConfig;
import net.skinmc.client.config.SkinMCConfigScreen;
import net.skinmc.client.profile.SkinMCProfileClient;
import net.skinmc.client.profile.SkinMCProfileScreen;
import net.minecraft.world.entity.player.Player;

/**
 * Initializes SkinMC config and config screen on NeoForge client.
 * Cape override is done via PlayerInfoSkinMixin (getSkin).
 */
public final class SkinMCModNeoForgeClient {

    public static void initialize(ModContainer container) {
        SkinMCConfig.setConfigPath(FMLPaths.CONFIGDIR.get().resolve("skinmc_mod.json"));
        SkinMCConfig.load();

        IConfigScreenFactory configScreenFactory =
                (minecraft, modListScreen) -> new SkinMCConfigScreen(modListScreen);
        container.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);

        PlayerCapeManager.getInstance().initialize();
        SkinMCProfileClient.init();

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
