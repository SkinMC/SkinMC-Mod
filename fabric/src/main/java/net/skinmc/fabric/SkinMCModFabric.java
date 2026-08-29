package net.skinmc.fabric;

import net.fabricmc.api.ModInitializer;
import net.skinmc.SkinMCMod;

public final class SkinMCModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SkinMCMod.init();
    }
}
