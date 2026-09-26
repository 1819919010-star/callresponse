package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21 Cute Activity 在伤害 Pre 入口触发 scare。 */
@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.ScareHandler", remap = false)
public abstract class CuteScareServerMixin {
    @Inject(method = "onLivingDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$ignoreRevengeAttack(LivingDamageEvent.Pre event, CallbackInfo ci) {
        if (event.getEntity() instanceof EntityMaid maid && CuteActivityBridge.isRevengeMaid(maid)) ci.cancel();
    }
}
