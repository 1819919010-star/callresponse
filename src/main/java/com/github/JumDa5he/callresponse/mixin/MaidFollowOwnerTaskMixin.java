package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.brain.SeekFoodBehavior;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MaidFollowOwnerTask.class)
public abstract class MaidFollowOwnerTaskMixin {
    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$pauseFollowWhileBegging(ServerLevel level, EntityMaid maid,
                                                       CallbackInfoReturnable<Boolean> cir) {
        // 正式 Brain 找食物的优先级高于跟随；这里只让低优先级跟随礼让，
        // 不接管导航，也不直接写 Navigation/Path。
        if (MaidMovementControl.controlsPath(maid) || SeekFoodBehavior.isSeeking(maid)) {
            cir.setReturnValue(false);
        }
    }
}
