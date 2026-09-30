package com.github.JumDa5he.callresponse.compat.outpost;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 调试用：在脚下生成一座复仇女仆据点，可选强制出现 GLY 彩蛋。 */
public final class OutpostDebugCommand {
    private OutpostDebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .executes(context -> run(context, false))
                .then(Commands.argument("force_gly", BoolArgumentType.bool())
                        .executes(context -> run(context,
                                BoolArgumentType.getBool(context, "force_gly"))));
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean forceGly)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        boolean generated = BetrayalOutpostManager.debugGenerate(level, player.blockPosition(), player, forceGly);
        if (generated) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[callresponse] 已生成 betrayal_maid_outpost" + (forceGly ? "（强制 GLY）" : "")), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(
                "[callresponse] 生成失败：检查 outpost_anchor 和 outpost_spawn_marker 是否完整"));
        return 0;
    }
}
