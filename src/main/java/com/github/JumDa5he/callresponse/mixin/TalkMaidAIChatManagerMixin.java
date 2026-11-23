package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionPrompt;
import com.github.JumDa5he.callresponse.compat.broadcast.EventDialogueBridge;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.JumDa5he.callresponse.compat.talk.TalkDialogueBridge;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority;
import net.minecraft.server.MinecraftServer;
import java.util.concurrent.CompletableFuture;

@Mixin(value = MaidAIChatManager.class, remap = false)
public abstract class TalkMaidAIChatManagerMixin {
    @Inject(method={"chat", "tryToChat", "normalChat", "onSettingIsEmpty", "tts", "onPlaySoundLocal"},
            at=@At("HEAD"), cancellable=true)
    private void callresponse$noCampExternalRequest(CallbackInfo ci) {
        if (WanderingBehaviorAuthority.rejectsDialogue(((MaidAIChatManager)(Object)this).getMaid())) ci.cancel();
    }
    @Redirect(method="onPlaySoundLocal", at=@At(value="INVOKE",
            target="Lnet/minecraft/server/MinecraftServer;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", remap=true))
    private CompletableFuture<Void> callresponse$guardQueuedVoice(MinecraftServer server, Runnable delivery) {
        var reply = WanderingBehaviorAuthority.externalReply(((MaidAIChatManager)(Object)this).getMaid());
        return server.submit(reply.guard(delivery));
    }
    @Redirect(method = "normalChat",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatManager;addUserHistory(Ljava/lang/String;)V"))
    private void callresponse$doNotPersistTalkPrompt(MaidAIChatManager manager, String message) {
        // Potion whispers are temporary context. Keeping their one-shot marker in user history
        // would cause a later ordinary chat callback to match an already completed event.
        if (EventDialogueBridge.isPotionRequest(EventDialogueBridge.findRequest(
                java.util.List.of(LLMMessage.userChat(manager.getMaid(), message))), manager.getMaid())) return;
        if (!TalkDialogueBridge.isTalkPrompt(message)) {
            manager.addUserHistory(EmotionPrompt.stripEmotionBlockForHistory(message));
        }
    }
}
