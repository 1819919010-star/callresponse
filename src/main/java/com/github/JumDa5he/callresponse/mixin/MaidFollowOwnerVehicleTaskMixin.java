package com.github.JumDa5he.callresponse.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerVehicleTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 固定座位不参与主人真实载具的同步下车逻辑；船、矿车等其他载具保持 TLM 原行为。
 */
@Mixin(MaidFollowOwnerVehicleTask.class)
public abstract class MaidFollowOwnerVehicleTaskMixin {
    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$keepMaidOnFixedSeat(ServerLevel level, EntityMaid maid,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (maid.getVehicle() instanceof EntitySit) {
            cir.setReturnValue(false);
        }
    }
}
