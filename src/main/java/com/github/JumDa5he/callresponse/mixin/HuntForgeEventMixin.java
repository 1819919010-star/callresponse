package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.JumDa5he.callresponse.compat.hunt.HuntEventIntrospection;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 通用阻止狩猎命中被任何事件取消。 */
@Mixin(value = ICancellableEvent.class, priority = 4000, remap = false)
public interface HuntForgeEventMixin {
    @Inject(method = "setCanceled", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$keepHuntEventUncancelled(boolean cancel, CallbackInfo ci) {
        Event event = (Event) (Object) this;
        if (cancel && (HuntEventIntrospection.belongsToHuntDamage(event)
                || OwnerDamageSource.mayBypassCancellation(event))) {
            ci.cancel();
        }
    }
}
