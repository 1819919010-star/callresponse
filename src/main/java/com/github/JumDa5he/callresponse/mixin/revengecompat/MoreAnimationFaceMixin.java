package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.github.JumDa5he.moreanimation.compat.event.FaceInteractionEvent", remap=false)
public abstract class MoreAnimationFaceMixin {
    @Inject(method="begin", at=@At("HEAD"), cancellable=true)
    private static void begin(net.minecraft.server.level.ServerPlayer player, EntityMaid maid, boolean camera, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) ci.cancel();
    }
}

