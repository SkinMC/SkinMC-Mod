package net.skinmc.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.skinmc.client.profile.OpenProfilePacket;

/**
 * Registers /skinmc and /skinmc <player>. Server sends S2C packet so client opens profile screen.
 */
public final class SkinMCCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("skinmc")
                        .requires(s -> s.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> openProfile(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> openProfile(ctx, EntityArgument.getPlayer(ctx, "player"))))
        );
    }

    private static int openProfile(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer sourcePlayer = (ServerPlayer) ctx.getSource().getEntity();
        if (sourcePlayer == null) return 0;
        ServerPlayer actualTarget = target != null ? target : sourcePlayer;
        OpenProfilePacket.sendToClient(sourcePlayer, actualTarget.getUUID(), actualTarget.getName().getString());
        return 1;
    }
}
