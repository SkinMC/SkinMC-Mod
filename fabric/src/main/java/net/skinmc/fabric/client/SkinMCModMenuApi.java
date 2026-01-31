package net.skinmc.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import net.skinmc.client.config.SkinMCConfigScreen;

/**
 * Registers SkinMC config screen with Mod Menu (optional dependency).
 */
public final class SkinMCModMenuApi implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SkinMCConfigScreen::new;
    }
}
