package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also cover callers that insert directly rather than through ChatBubbleManager. */
@Mixin(value=ChatBubbleDataCollection.class, remap=false)
public abstract class WanderingCampBubbleCollectionMixin {
    @Inject(method="add", at=@At("HEAD"), cancellable=true)
    private void speech(IChatBubbleData bubble, CallbackInfoReturnable<Long> cir) {
        if (WanderingBehaviorAuthority.spokenBubble(bubble) && WanderingBehaviorAuthority.rejectsBubble(this))
            cir.setReturnValue(-1L);
    }
}
