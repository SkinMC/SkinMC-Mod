package net.skinmc.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.skinmc.SkinMCMod;
import net.skinmc.commands.SkinMCCommands;
import net.skinmc.neoforge.client.SkinMCModNeoForgeClient;

@Mod(SkinMCMod.MOD_ID)
public final class SkinMCModNeoForge {
    public SkinMCModNeoForge(IEventBus modEventBus) {
        SkinMCMod.init();
        modEventBus.addListener(SkinMCModNeoForgeClient::onAddLayers);
        modEventBus.addListener(RegisterCommandsEvent.class, e -> SkinMCCommands.register(e.getDispatcher()));
        modEventBus.addListener(PlayerInteractEvent.EntityInteract.class, SkinMCModNeoForgeClient::onEntityInteract);
    }
}
