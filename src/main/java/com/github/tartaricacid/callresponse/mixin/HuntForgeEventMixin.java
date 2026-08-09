package com.github.tartaricacid.callresponse.mixin;

import com.github.tartaricacid.callresponse.compat.hunt.HuntEventIntrospection;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 通用阻止狩猎命中被任何 Forge 事件取消或设为 DENY。 */
@Mixin(value = Event.class, priority = 4000, remap = false)
public abstract class HuntForgeEventMixin {
    @Inject(method = "setCanceled", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$keepHuntEventUncancelled(boolean cancel, CallbackInfo ci) {
        if (cancel && HuntEventIntrospection.belongsToHuntDamage((Event) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setResult", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$keepHuntEventAllowed(Event.Result result, CallbackInfo ci) {
        if (result == Event.Result.DENY
                && HuntEventIntrospection.belongsToHuntDamage((Event) (Object) this)) {
            ci.cancel();
        }
    }
}
