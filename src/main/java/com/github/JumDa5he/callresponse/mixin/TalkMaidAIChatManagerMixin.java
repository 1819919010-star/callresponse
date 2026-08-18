package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionPrompt;
import com.github.JumDa5he.callresponse.compat.talk.TalkDialogueBridge;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MaidAIChatManager.class, remap = false)
public abstract class TalkMaidAIChatManagerMixin {
    @Redirect(method = "normalChat",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatManager;addUserHistory(Ljava/lang/String;)V"))
    private void callresponse$doNotPersistTalkPrompt(MaidAIChatManager manager, String message) {
        if (!TalkDialogueBridge.isTalkPrompt(message)) {
            manager.addUserHistory(EmotionPrompt.stripEmotionBlockForHistory(message));
        }
    }
}
