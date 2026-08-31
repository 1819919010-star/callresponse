package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidJoyTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBrains;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Joy 保留原始首席 SitId，第二名起复制其精确位置创建独立 EntitySit。 */
@Mixin(MaidJoyTask.class)
public abstract class MaidJoyCapacityMixin {
    @Inject(method = "isOccupied", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$useJoyCapacity(ServerLevel level, BlockPos pos,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (FacilityCapacityManager.hasCapacityOverride(level, pos)) {
            cir.setReturnValue(!FacilityCapacityManager.hasVacancy(level, pos));
        }
    }

    @Inject(method = "start", at = @At("HEAD"), remap = false)
    private void callresponse$seatAtExtendedJoy(ServerLevel level, EntityMaid maid, long gameTime,
                                                CallbackInfo ci) {
        maid.getBrain().getMemory(InitBrains.TARGET_POS.get()).ifPresent(target ->
                FacilityCapacityManager.trySeatExtraJoy(level, target.currentBlockPosition(), maid));
    }
}
