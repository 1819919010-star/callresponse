package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindHomeMealTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBrains;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只在野餐垫原版四席已满时补入扩展席，并让搜索逻辑看到额外容量。 */
@Mixin(MaidFindHomeMealTask.class)
public abstract class MaidFindHomeMealCapacityMixin {
    @Inject(method = "isOccupied", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$usePicnicCapacity(ServerLevel level, BlockPos pos,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (FacilityCapacityManager.hasCapacityOverride(level, pos)) {
            cir.setReturnValue(!FacilityCapacityManager.hasVacancy(level, pos));
        }
    }

    @Inject(method = "start", at = @At("HEAD"), remap = false)
    private void callresponse$seatAtExtendedPicnic(ServerLevel level, EntityMaid maid, long gameTime,
                                                   CallbackInfo ci) {
        maid.getBrain().getMemory(InitBrains.TARGET_POS.get()).ifPresent(target ->
                FacilityCapacityManager.trySeatExtraPicnic(level, target.currentBlockPosition(), maid));
    }

    @Inject(method = "start", at = @At("TAIL"), remap = false)
    private void callresponse$facePicnicBasket(ServerLevel level, EntityMaid maid, long gameTime,
                                               CallbackInfo ci) {
        if (maid.getVehicle() != null) {
            FacilityCapacityManager.facePicnicCenter(level, maid,
                    maid.getVehicle().blockPosition());
        }
    }
}
