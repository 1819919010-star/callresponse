package com.github.JumDa5he.callresponse.mixin;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅让原版睡眠 API 将女仆床识别为玩家可睡的床；右键入口由 Forge 事件提供。 */
@Mixin(BlockMaidBed.class)
public abstract class MaidBedPlayerBedSupportMixin {
    @Inject(method = "isBed", at = @At("RETURN"), cancellable = true, remap = false)
    private void callresponse$allowPlayerSleep(BlockState state, BlockGetter level, BlockPos pos,
                                                Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player) cir.setReturnValue(true);
    }
}
