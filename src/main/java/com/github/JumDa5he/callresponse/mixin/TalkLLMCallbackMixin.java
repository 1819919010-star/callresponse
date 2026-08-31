package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.broadcast.BroadcastDialogueTracker;
import com.github.JumDa5he.callresponse.compat.broadcast.ChatTextSanitizer;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionActiveDialogue;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.talk.TalkDialogueBridge;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.UUID;

@Mixin(value = LLMCallback.class, remap = false)
public abstract class TalkLLMCallbackMixin {
    @Shadow public boolean needAddTools;
    @Final
    @Shadow protected EntityMaid maid;
    @Shadow protected long waitingChatBubbleId;
    @Unique private UUID callresponse$talkRequestId;
    @Unique private UUID callresponse$broadcastPlayerId;

    @Inject(method = "<init>(Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatManager;Ljava/util/List;Z)V",
            at = @At("TAIL"))
    private void callresponse$markTalkRequest(MaidAIChatManager manager, List<LLMMessage> messages,
                                              boolean subagents, CallbackInfo ci) {
        callresponse$talkRequestId = TalkDialogueBridge.findRequestId(messages);
        callresponse$broadcastPlayerId = BroadcastDialogueTracker.findPlayerId(messages);
        if (callresponse$talkRequestId != null) {
            needAddTools = false;
        }
    }

    /** 只清理本附属发起的回复，并在本体写历史、TTS、气泡之前替换参数。 */
    @ModifyVariable(method = "onSuccess", at = @At("HEAD"), argsOnly = true)
    private ResponseChat callresponse$sanitizeOwnReplies(ResponseChat response) {
        if (callresponse$talkRequestId == null && callresponse$broadcastPlayerId == null) {
            return response;
        }
        return ChatTextSanitizer.sanitize(response);
    }

    @Redirect(method = "onSuccess",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatManager;addAssistantHistory(Ljava/lang/String;)V"))
    private void callresponse$doNotPersistTalkReply(MaidAIChatManager manager, String text) {
        if (callresponse$talkRequestId == null) {
            manager.addAssistantHistory(text);
        }
    }

    @Inject(method = "onSuccess", at = @At("TAIL"))
    private void callresponse$captureTalkReply(ResponseChat response, CallbackInfo ci) {
        if (callresponse$talkRequestId != null && !response.getChatText().isBlank()) {
            TalkDialogueBridge.complete(callresponse$talkRequestId, maid, response.getChatText());
        }
        if (callresponse$talkRequestId == null && callresponse$broadcastPlayerId != null
                && !response.getChatText().isBlank()) {
            EmotionData.recordLastChatReply(maid, callresponse$broadcastPlayerId,
                    response.getChatText());
            EmotionActiveDialogue.recordVisibleSpeech(maid, response.getChatText());
        }
    }

    @Inject(method = "onSuccess", at = @At("HEAD"), cancellable = true)
    private void callresponse$discardLateTalkReply(ResponseChat response, CallbackInfo ci) {
        if (callresponse$talkRequestId != null && !TalkDialogueBridge.isPending(callresponse$talkRequestId)) {
            if (maid.level() instanceof ServerLevel level) {
                level.getServer().submit(() -> maid.getChatBubbleManager().removeChatBubble(waitingChatBubbleId));
            }
            ci.cancel();
        }
    }

    @Inject(method = "shouldCacheTokenUsage", at = @At("HEAD"), cancellable = true)
    private void callresponse$doNotAffectNormalChatTokenHistory(CallbackInfoReturnable<Boolean> cir) {
        if (callresponse$talkRequestId != null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onFailure", at = @At("TAIL"))
    private void callresponse$captureTalkFailure(HttpRequest request, Throwable throwable,
                                                 int errorCode, CallbackInfo ci) {
        if (callresponse$talkRequestId != null) {
            TalkDialogueBridge.fail(callresponse$talkRequestId, maid);
        }
    }
}
