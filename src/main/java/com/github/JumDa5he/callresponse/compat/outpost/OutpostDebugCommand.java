package com.github.JumDa5he.callresponse.compat.outpost;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Permission-gated reference debug placement, using the normal saved spawn plan. */
public final class OutpostDebugCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2))
                .executes(context -> run(context, false))
                .then(Commands.argument("force_gly", BoolArgumentType.bool())
                        .executes(context -> run(context, BoolArgumentType.getBool(context, "force_gly"))));
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean forceGly)
            throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        boolean success = BetrayalOutpostManager.debugGenerate(player.serverLevel(), player.blockPosition(), player, forceGly);
        if (success) context.getSource().sendSuccess(() -> Component.translatable(
                "command.callresponse.outpost.generated", forceGly), false);
        else context.getSource().sendFailure(Component.translatable("command.callresponse.outpost.failed"));
        return success ? 1 : 0;
    }
}
