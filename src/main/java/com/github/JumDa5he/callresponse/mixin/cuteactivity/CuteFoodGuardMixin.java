package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.FoodGuardHandler", remap = false)
public abstract class CuteFoodGuardMixin {
    @Shadow(remap = false)
    public static void cancelGuard(EntityMaid maid) {
        throw new AssertionError();
    }

    @Inject(method = "triggerGuard", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$blockNewGuard(EntityMaid maid, LivingEntity eater, CallbackInfo ci) {
        if (CuteActivityBridge.isRevengeMaid(maid) || IntimidationManager.isIntimidated(maid)) ci.cancel();
    }

    @Inject(method = "onMaidTick", at = @At("HEAD"), remap = false)
    private static void callresponse$releaseActiveGuard(MaidTickEvent event, CallbackInfo ci) {
        EntityMaid maid = event.getMaid();
        if (CuteActivityBridge.isRevengeMaid(maid) || IntimidationManager.isIntimidated(maid)) cancelGuard(maid);
    }

    /** The third-party cleanup may release its guard state, but must not restore an old camp task. */
    @Inject(method = "restoreWorkMode", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$preserveCampTask(EntityMaid maid, @Coerce Object state, CallbackInfo ci) {
        if (CuteActivityBridge.isRevengeMaid(maid)) ci.cancel();
    }
}
