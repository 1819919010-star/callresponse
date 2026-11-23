package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.*;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="cn.autoforged.maid_cute_activity.MaidChatBubbles", remap=false)
public abstract class CuteCampDialogueMixin {
    // Before its bubble-limit code can remove our authorized dialogue. Image expressions still pass.
    @Inject(method="add", at=@At("HEAD"), cancellable=true)
    private static void callresponse$noCampExternalBubble(EntityMaid maid, IChatBubbleData bubble, CallbackInfo ci) {
        if (WanderingCampData.active(maid) && WanderingBehaviorAuthority.spokenBubble(bubble)) ci.cancel();
    }
}
