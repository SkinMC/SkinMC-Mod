package net.skinmc.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.skinmc.client.profile.SkinMCProfileScreen;

import static dev.architectury.event.events.client.ClientCommandRegistrationEvent.argument;
import static dev.architectury.event.events.client.ClientCommandRegistrationEvent.literal;

/**
 * Registers /skinmc and /skinmc &lt;player&gt; entirely on the client.
 * This keeps the profile UI available on servers that do not run the SkinMC mod.
 */
public final class SkinMCCommands {

    public static void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("skinmc")
                        .executes(ctx -> openProfile(ctx, null))
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> openProfile(ctx, StringArgumentType.getString(ctx, "player"))))
        );
    }

    private static int openProfile(CommandContext<ClientCommandSourceStack> context, String targetName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return 0;

        Player target = minecraft.player;
        if (targetName != null) {
            target = minecraft.level.players().stream()
                    .filter(player -> player.getName().getString().equalsIgnoreCase(targetName))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                context.getSource().arch$sendFailure(
                        net.minecraft.network.chat.Component.literal("Player not found: " + targetName));
                return 0;
            }
        }

        SkinMCProfileScreen.open(target);
        return 1;
    }
}
