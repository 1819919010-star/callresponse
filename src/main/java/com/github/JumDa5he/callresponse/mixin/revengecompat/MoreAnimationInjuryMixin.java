package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.github.JumDa5he.moreanimation.compat.event.BrokenLegEvent", remap=false)
public abstract class MoreAnimationInjuryMixin {
    @Shadow private static void finish(EntityMaid maid) { throw new AssertionError(); }
    @Inject(method="begin", at=@At("HEAD"), cancellable=true)
    private static void begin(EntityMaid maid, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method="serverTick", at=@At("HEAD"), cancellable=true)
    private static void tick(EntityMaid maid, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) { finish(maid); ci.cancel(); }
    }
}

