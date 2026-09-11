package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob 默认会把第一个 Mob 乘客当成控制者并关闭自身 MOVE/JUMP/LOOK 控制。
 * 公主抱的乘客只是被抱者，不能取得 carrier 的控制权。
 */
@Mixin(Mob.class)
public abstract class PrincessCarryControllerMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void callresponse$keepPrincessCarryCarrierMovable(CallbackInfoReturnable<LivingEntity> cir) {
        Mob self = (Mob) (Object) this;
        if (self instanceof EntityMaid carrier && PrincessCarryManager.isMaidCarrySession(carrier)) {
            cir.setReturnValue(null);
        }
    }
}
