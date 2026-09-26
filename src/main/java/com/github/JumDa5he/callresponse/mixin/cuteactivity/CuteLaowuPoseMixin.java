package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.LaowuPoseHandler", remap = false)
public abstract class CuteLaowuPoseMixin {
    @Shadow(remap = false)
    public static void cancelPose(EntityMaid maid) {
        throw new AssertionError();
    }

    @Inject(method = "onLivingDamage", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void callresponse$blockNewPose(LivingDamageEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof EntityMaid maid
                && (CuteActivityBridge.isRevengeMaid(maid) || IntimidationManager.isIntimidated(maid))) ci.cancel();
    }

    @Inject(method = "onMaidTick", at = @At("HEAD"), require = 0, remap = false)
    private static void callresponse$releaseOnlyOwnedPose(MaidTickEvent event, CallbackInfo ci) {
        EntityMaid maid = event.getMaid();
        if (CuteActivityBridge.isRevengeMaid(maid) || IntimidationManager.isIntimidated(maid)) cancelPose(maid);
    }
}
