package com.github.JumDa5he.callresponse.mixin;

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
        if (MaidMovementControl.controlsSchedule(maid)) {
            ci.cancel();
        }
    }
}
