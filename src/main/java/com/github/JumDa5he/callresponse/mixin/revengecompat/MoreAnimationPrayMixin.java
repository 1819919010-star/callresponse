package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.github.JumDa5he.moreanimation.compat.event.PrayEvent", remap=false)
public abstract class MoreAnimationPrayMixin {
    @Inject(method="loadedMaids", at=@At("RETURN"), cancellable=true)
    private static void candidates(net.minecraft.server.level.ServerLevel level,
            CallbackInfoReturnable<java.util.List<? extends EntityMaid>> cir) {
        cir.setReturnValue(cir.getReturnValue().stream().filter(m -> !(m instanceof RevengeMaidEntity)).toList());
    }
    @Inject(method="freezeAndFace", at=@At("HEAD"), cancellable=true)
    private static void freeze(EntityMaid maid, net.minecraft.core.BlockPos pos, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) ci.cancel();
    }
}

