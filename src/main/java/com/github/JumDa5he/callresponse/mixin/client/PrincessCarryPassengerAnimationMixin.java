package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalPassenger;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 抱人的女仆不能误命中“自己正被乘坐”的普通载人姿势。 */
@Mixin(value = ConditionalPassenger.class, remap = false)
public abstract class PrincessCarryPassengerAnimationMixin {
    @Inject(method = "doTest", at = @At("HEAD"), cancellable = true)
    private void callresponse$skipPassengerAnimation(Mob mob, CallbackInfoReturnable<String> cir) {
        if (mob instanceof EntityMaid carrier && PrincessCarryManager.isMaidCarrySession(carrier)) {
            cir.setReturnValue("");
        }
    }
}
