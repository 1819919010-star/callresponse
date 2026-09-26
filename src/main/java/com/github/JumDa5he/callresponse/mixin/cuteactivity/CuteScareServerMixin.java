package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block real-hurt scare both when queued and immediately before a queued packet is sent. */
@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.ScareHandler", remap = false)
public abstract class CuteScareServerMixin {
    @Inject(method = "onLivingAttack", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void callresponse$ignoreRevengeAttack(LivingAttackEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof EntityMaid maid && CuteActivityBridge.isRevengeMaid(maid)) ci.cancel();
    }

    @Inject(method = "sendScare", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void callresponse$ignoreQueuedRevengeScare(EntityMaid maid, int ticks, CallbackInfo ci) {
        if (CuteActivityBridge.isRevengeMaid(maid)) ci.cancel();
    }
}
