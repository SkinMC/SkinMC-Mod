package net.skinmc.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.skinmc.SkinMCMod;
import net.skinmc.commands.SkinMCCommands;

public final class SkinMCModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SkinMCMod.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SkinMCCommands.register(dispatcher));
    }
}
