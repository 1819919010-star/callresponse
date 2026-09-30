package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SchedulePos.class)
public abstract class SchedulePosMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$pauseScheduleWhileControlled(EntityMaid maid, CallbackInfo ci) {
        if (maid instanceof com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity
                || MaidMovementControl.controlsSchedule(maid)
                || (!maid.level().isClientSide && BetrayalOutpostMaidData.isOutpostMaid(maid))) {
            ci.cancel();
        }
    }
    @Inject(method = "restrictTo", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$independentCampRange(EntityMaid maid, CallbackInfo ci) {
        // Also covers SchedulePos.load/clear/setHomeModeEnable, not only periodic tick.
        if (maid instanceof com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity) ci.cancel();
    }
}
