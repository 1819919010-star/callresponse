package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After ordinary hurt cleanup, skip only the diamond-sword hurt/head-hold tail branch. */
@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.LonelinessHandler", remap = false)
public abstract class CuteLonelinessBaoTouMixin {
    @Inject(method = "onLivingDamage", at = @At(value = "INVOKE",
            target = "Lcn/autoforged/maid_cute_activity/LonelinessHandler;handleWildMaidRetaliation(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            ordinal = 5, shift = At.Shift.AFTER), cancellable = true, remap = false)
    private static void callresponse$noRevengeHurtBaoTou(LivingDamageEvent.Pre event, CallbackInfo ci) {
        if (event.getEntity() instanceof EntityMaid maid && CuteActivityBridge.isRevengeMaid(maid)) ci.cancel();
    }
}
