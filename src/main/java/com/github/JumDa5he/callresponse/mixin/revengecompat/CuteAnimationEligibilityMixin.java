package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="cn.autoforged.maid_cute_activity.AnimBookData", remap=false)
public abstract class CuteAnimationEligibilityMixin {
    @Inject(method="isExcluded", at=@At("HEAD"), cancellable=true)
    private static void excluded(EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (maid instanceof RevengeMaidEntity) cir.setReturnValue(true);
    }
}

