package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlockEntity;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class RehabilitationCommand {
    private RehabilitationCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("rehabilitate").requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(ctx -> run(ctx.getSource(), EntityArgument.getEntity(ctx, "target"))));
    }

    private static int run(CommandSourceStack source, Entity target) {
        if (!(target instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)
                || !maid.isAlive() || maid.isRemoved() || !WineFoxFaction.isRevenge(maid)
                || !WineFoxSurrender.isSurrendered(maid)) {
            source.sendFailure(Component.translatable("command.callresponse.rehabilitate.invalid"));
            return 0;
        }
        UUID owner = WineFoxSurrender.captor(maid);
        if (owner == null) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.translatable("command.callresponse.rehabilitate.no_owner"));
                return 0;
            }
            owner = player.getUUID();
        }
        DarkIronCageBlockEntity cage = findCage(level, maid);
        // A displacement before the next cage tick must not let the command orphan a live cage.
        if (cage == null && MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE)) {
            source.sendFailure(Component.translatable("command.callresponse.rehabilitate.cage"));
            return 0;
        }
        EntityMaid replacement = WineFoxRehabilitation.convert(maid, owner, cage);
        if (replacement == null) {
            source.sendFailure(Component.translatable("command.callresponse.rehabilitate.failed"));
            return 0;
        }
        // /execute as changes the acting entity, but the original command source receives feedback.
        if (!(source.source instanceof ServerPlayer recipient) || !recipient.getUUID().equals(owner)) {
            source.sendSuccess(() -> Component.translatable("command.callresponse.rehabilitate.success",
                    WineFoxRehabilitation.modelName(replacement)), false);
        }
        return 1;
    }

    @Nullable
    private static DarkIronCageBlockEntity findCage(ServerLevel level, EntityMaid maid) {
        BlockPos feet = maid.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.below(2), feet.above())) {
            if (level.getBlockEntity(pos) instanceof DarkIronCageBlockEntity cage && cage.occupant() == maid)
                return cage;
        }
        return null;
    }
}
