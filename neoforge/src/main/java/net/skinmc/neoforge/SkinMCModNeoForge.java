package net.skinmc.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.skinmc.SkinMCMod;
import net.skinmc.neoforge.client.SkinMCModNeoForgeClient;

@Mod(value = SkinMCMod.MOD_ID, dist = Dist.CLIENT)
public final class SkinMCModNeoForge {
    public SkinMCModNeoForge(ModContainer container) {
        SkinMCMod.init();
        SkinMCModNeoForgeClient.initialize(container);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class,
                SkinMCModNeoForgeClient::onEntityInteract);
    }
}
