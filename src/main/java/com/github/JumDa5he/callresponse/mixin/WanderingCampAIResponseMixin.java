package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Capture request eligibility once, then recheck every deferred delivery against the same epoch. */
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
    // AutoGenSetting inherits onFailure; TTS has no function calls. These are not optional core guards.
    @Inject(method={"onFailure", "onFunctionCall"}, at=@At("HEAD"), cancellable=true, require=0)
    private void otherResponse(CallbackInfo ci) {
        if (!callresponse$reply.valid()) ci.cancel();
    }
    @Redirect(method={"onSuccess", "onFailure", "runOnServerThread"}, at=@At(value="INVOKE",
            target="Lnet/minecraft/server/MinecraftServer;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", remap=true))
    private CompletableFuture<Void> queued(MinecraftServer server, Runnable delivery) {
        return server.submit(callresponse$reply.guard(delivery));
    }
    // The actual 1.21 TLM LLM path uses Supplier + Executor for tool callbacks, unlike 1.20.
    @Redirect(method="onFunctionCall", at=@At(value="INVOKE",
            target="Lnet/minecraft/server/MinecraftServer;submit(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;", remap=true), require=0)
    private <T> CompletableFuture<T> queuedFunction(MinecraftServer server, Supplier<T> delivery) {
        return server.submit(callresponse$reply.guardSupplier(delivery));
    }
    @Inject(method="serverExecutor", at=@At("RETURN"), cancellable=true, require=0)
    private void queuedContinuation(CallbackInfoReturnable<Executor> cir) {
        Executor executor = cir.getReturnValue();
        var reply = callresponse$reply;
        cir.setReturnValue(delivery -> executor.execute(reply.guard(delivery)));
    }
}
