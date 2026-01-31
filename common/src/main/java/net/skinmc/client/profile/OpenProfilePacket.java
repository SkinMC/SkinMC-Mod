package net.skinmc.client.profile;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.skinmc.SkinMCMod;

import java.util.UUID;

/**
 * S2C packet: server tells client to open SkinMC profile screen for a player.
 */
public final class OpenProfilePacket {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SkinMCMod.MOD_ID, "open_profile");

    public static void registerClientReceiver() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ID, (buf, context) -> {
            UUID uuid = buf.readUUID();
            String displayName = buf.readUtf(64);
            context.queue(() -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null) mc.execute(() -> mc.setScreen(new SkinMCProfileScreen(uuid, displayName)));
            });
        });
    }

    public static void sendToClient(net.minecraft.server.level.ServerPlayer player, UUID targetUuid, String targetName) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        buf.writeUUID(targetUuid);
        buf.writeUtf(targetName != null ? targetName : "", 64);
        NetworkManager.sendToPlayer(player, ID, buf);
    }
}
