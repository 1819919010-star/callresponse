package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.github.JumDa5he.moreanimation.compat.animation.GameLostAnimation", remap=false)
public abstract class MoreAnimationDecisionMixin {
    @Shadow public static void clear(EntityMaid maid) { throw new AssertionError(); }
    @Inject(method="serverTick", at=@At("HEAD"), cancellable=true)
    private static void tick(EntityMaid maid, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) { clear(maid); ci.cancel(); }
    }
    @Inject(method="canClaim", at=@At("HEAD"), cancellable=true)
    private static void claim(EntityMaid maid, String name, CallbackInfoReturnable<Boolean> cir) {
        if (maid instanceof RevengeMaidEntity) cir.setReturnValue(false);
    }
}

