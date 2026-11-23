package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

/** Request-time tombstone plus execution-time check; adoption never revives an old reply. */
@Mixin(value={LLMCallback.class, AutoGenSettingCallback.class, TTSCallback.class}, remap=false)
public abstract class WanderingCampAIResponseMixin {
    @Unique private WanderingBehaviorAuthority.ExternalReply callresponse$reply;
    @Unique private EntityMaid callresponse$maid() {
        return (Object)this instanceof LLMCallback llm ? llm.getMaid() : ((TTSCallback)(Object)this).getMaid();
    }
    @Inject(method="<init>", at=@At("TAIL"))
    private void capture(CallbackInfo ci) {
        callresponse$reply = WanderingBehaviorAuthority.externalReply(callresponse$maid());
    }
    @Inject(method="onSuccess", at=@At("HEAD"), cancellable=true)
    private void response(CallbackInfo ci) {
        if (!callresponse$reply.valid()) ci.cancel();
    }
    // AutoGenSetting inherits onFailure, and TTS does not have onFunctionCall.
    @Inject(method={"onFailure", "onFunctionCall"}, at=@At("HEAD"), cancellable=true, require=0)
    private void otherResponse(CallbackInfo ci) {
        if (!callresponse$reply.valid()) ci.cancel();
    }
    @Redirect(method={"onSuccess", "onFailure", "onFunctionCall", "runOnServerThread"}, at=@At(value="INVOKE",
            target="Lnet/minecraft/server/MinecraftServer;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", remap=true))
    private CompletableFuture<Void> queued(MinecraftServer server, Runnable delivery) {
        return server.submit(callresponse$reply.guard(delivery));
    }
}
