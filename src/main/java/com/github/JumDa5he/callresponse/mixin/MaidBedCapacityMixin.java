package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBedTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBrains;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原床已标记 OCCUPIED 时，只有配置仍有余量才允许额外女仆共享床位。 */
@Mixin(MaidBedTask.class)
public abstract class MaidBedCapacityMixin {
    @Inject(method = "findBed", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$findBedWithCapacity(ServerLevel level, EntityMaid maid,
                                                  CallbackInfoReturnable<BlockPos> cir) {
        FacilityCapacityManager.BedSearchResult result =
                FacilityCapacityManager.findCapacityAwareBed(level, maid);
        if (result.handled()) {
            cir.setReturnValue(result.pos());
        }
    }

    @Inject(method = "start", at = @At("HEAD"), remap = false)
    private void callresponse$shareOccupiedMaidBed(ServerLevel level, EntityMaid maid, long gameTime,
                                                   CallbackInfo ci) {
        maid.getBrain().getMemory(InitBrains.TARGET_POS.get()).ifPresent(target ->
                FacilityCapacityManager.tryUseOccupiedBed(level, maid, target.currentBlockPosition()));
    }

}
