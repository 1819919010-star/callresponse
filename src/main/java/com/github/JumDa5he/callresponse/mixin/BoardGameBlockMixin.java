package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.game.BoardGameManager;
import com.github.tartaricacid.touhoulittlemaid.block.BlockCChess;
import com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku;
import com.github.tartaricacid.touhoulittlemaid.block.BlockWChess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只接管三种棋自己的 startMaidSit，其他娱乐方块完全不受影响。 */
@Mixin({BlockGomoku.class, BlockCChess.class, BlockWChess.class})
public abstract class BoardGameBlockMixin {
    @Inject(method = "startMaidSit", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$seatMaidBySide(EntityMaid maid, BlockState state, Level level,
                                             BlockPos pos, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            BoardGameManager.seatMaid(maid, state, serverLevel, pos);
            ci.cancel();
        }
    }
}
