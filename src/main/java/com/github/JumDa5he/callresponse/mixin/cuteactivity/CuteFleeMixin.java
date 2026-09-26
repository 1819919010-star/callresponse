package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.FleeHandler", remap = false)
public abstract class CuteFleeMixin {
    @Shadow(remap = false)
    public static void cancelFlee(EntityMaid maid) {
        throw new AssertionError();
    }

    @Inject(method = "canTrigger", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void callresponse$doNotStartFlee(EntityMaid maid, @Coerce Object state, long time,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (CuteActivityBridge.isRevengeMaid(maid) || IntimidationManager.isIntimidated(maid))
            cir.setReturnValue(false);
    }

    @Inject(method = "onMaidTick", at = @At("HEAD"), require = 0, remap = false)
    private static void callresponse$finishExistingFlee(MaidTickEvent event, CallbackInfo ci) {
        EntityMaid maid = event.getMaid();
        if (IntimidationManager.isIntimidated(maid)) cancelFlee(maid);
    }
}
