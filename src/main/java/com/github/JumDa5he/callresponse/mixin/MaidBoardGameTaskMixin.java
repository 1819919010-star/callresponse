package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.game.BoardGameManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBoardGameTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让原版游戏工作把“尚有一个空席位”的棋盘继续视为可用。 */
@Mixin(MaidBoardGameTask.class)
public abstract class MaidBoardGameTaskMixin {
    @Inject(method = "isOccupied", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$useTwoParticipantSeats(ServerLevel level, BlockPos pos,
                                                     CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(BoardGameManager.isBoardFull(level, pos));
    }
}
