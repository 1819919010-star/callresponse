package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.hunger.HungerManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MaidFollowOwnerTask.class)
public abstract class MaidFollowOwnerTaskMixin {
    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void callresponse$pauseFollowWhileControlled(ServerLevel level, EntityMaid maid,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (MaidMovementControl.controlsPath(maid) || HungerManager.isBeggingForFood(maid)) {
            cir.setReturnValue(false);
        }
    }
}
