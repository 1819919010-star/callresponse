package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=ChatBubbleManager.class, remap=false)
public abstract class WanderingCampBubbleMixin {
    @Shadow @Final private EntityMaid maid;
    @Inject(method="getChatBubbleDataCollection", at=@At("RETURN"))
    private void bind(CallbackInfoReturnable<ChatBubbleDataCollection> cir) {
        WanderingBehaviorAuthority.bindBubbles(cir.getReturnValue(), maid);
    }
    @Inject(method="addChatBubble", at=@At("HEAD"), cancellable=true)
    private void speech(IChatBubbleData bubble, CallbackInfoReturnable<Long> cir) {
        if (WanderingBehaviorAuthority.spokenBubble(bubble) && WanderingBehaviorAuthority.rejectsDialogue(maid))
            cir.setReturnValue(-1L);
    }
    @Inject(method="addLLMChatText", at=@At("HEAD"), cancellable=true)
    private void response(CallbackInfo ci) {
        if (WanderingBehaviorAuthority.rejectsDialogue(maid)) ci.cancel();
    }
}
