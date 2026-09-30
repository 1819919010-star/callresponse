package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Leave native TLM/YSM animations alone; decline only MoreAnimation's optional overlays/control. */
@Pseudo
@Mixin(targets="com.github.JumDa5he.moreanimation.compat.animation.MaidAnimationData", remap=false)
public abstract class MoreAnimationDataMixin {
    @Shadow public static void clearLocal(EntityMaid maid) { throw new AssertionError(); }
    @Shadow public static void clearTransientExpression(EntityMaid maid) { throw new AssertionError(); }
    @Inject(method="start", at=@At("HEAD"), cancellable=true)
    private static void start(EntityMaid maid, String action, int duration, int priority, boolean lock, CallbackInfoReturnable<Boolean> cir) {
        if (maid instanceof RevengeMaidEntity) cir.setReturnValue(false);
    }
    @Inject(method="clientStart", at=@At("HEAD"), cancellable=true)
    private static void client(EntityMaid maid, String action, int duration, int priority, boolean lock, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method="clientStartAt", at=@At("HEAD"), cancellable=true)
    private static void clientAt(EntityMaid maid, String action, long since, int duration, int priority, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method={"serverTick", "freeze"}, at=@At("HEAD"), cancellable=true)
    private static void tick(EntityMaid maid, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) {
            // These methods remove only this addon's own overlay state; never setNoAi(false) or restore navigation.
            clearLocal(maid); clearTransientExpression(maid); ci.cancel();
        }
    }
    @Inject(method={"shouldForceHuman", "shouldForceFox", "injuredAuto", "autoPet", "autoHug", "randomSleepPose",
            "isActive(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",
            "isTailInteractionActive", "isFaceInteractionActive"},
            at=@At("HEAD"), cancellable=true)
    private static void disabled(EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (maid instanceof RevengeMaidEntity) cir.setReturnValue(false);
    }
    @Inject(method={"activeAction", "effectiveExpression"}, at=@At("HEAD"), cancellable=true)
    private static void noOverlay(EntityMaid maid, CallbackInfoReturnable<String> cir) {
        if (maid instanceof RevengeMaidEntity) cir.setReturnValue("");
    }
    @Inject(method="enabledActions", at=@At("HEAD"), cancellable=true)
    private static void noActions(EntityMaid maid, String category, CallbackInfoReturnable<java.util.List<String>> cir) {
        if (maid instanceof RevengeMaidEntity) cir.setReturnValue(java.util.List.of());
    }
}
