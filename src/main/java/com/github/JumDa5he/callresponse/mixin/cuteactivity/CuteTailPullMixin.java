package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidAnimationMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.TailPullInteractionHandler", remap = false)
public abstract class CuteTailPullMixin {
    @Shadow(remap = false)
    public static boolean isInteractionActive(EntityMaid maid) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    public static void cancelInteraction(EntityMaid maid) {
        throw new AssertionError();
    }

    @Inject(method = "onTailPulled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void callresponse$blockRevengeTailReaction(EntityMaid maid, CallbackInfo ci) {
        if (CuteActivityBridge.isRevengeMaid(maid)) ci.cancel();
    }

    @Inject(method = "onMaidTick", at = @At("HEAD"), require = 0, remap = false)
    private static void callresponse$releaseOldTailReaction(MaidTickEvent event, CallbackInfo ci) {
        EntityMaid maid = event.getMaid();
        if (!CuteActivityBridge.isRevengeMaid(maid)) return;
        boolean active = isInteractionActive(maid);
        cancelInteraction(maid);
        if (active && (maid.animationId == 120 || maid.animationId == 135)) {
            maid.animationId = 0;
            maid.animationRecordTime = System.currentTimeMillis();
            NetworkHandler.sendToNearby(maid, new MaidAnimationMessage(maid.getId(), 0));
        }
    }
}
