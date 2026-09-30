package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets={"com.github.JumDa5he.moreanimation.compat.event.EarPullEvent",
        "com.github.JumDa5he.moreanimation.compat.event.TailPullEvent",
        "com.github.JumDa5he.moreanimation.compat.event.TailDragInteractionEvent"}, remap=false)
public abstract class MoreAnimationManualMixin {
    // These targets expose different names with the same signature; the plugin checks each target.
    @Inject(method={"triggerEarPull", "triggerTailPull", "begin"}, at=@At("HEAD"), cancellable=true, require=0)
    private static void start(net.minecraft.server.level.ServerPlayer player, EntityMaid maid, CallbackInfo ci) {
        if (maid instanceof RevengeMaidEntity) ci.cancel();
    }
}

