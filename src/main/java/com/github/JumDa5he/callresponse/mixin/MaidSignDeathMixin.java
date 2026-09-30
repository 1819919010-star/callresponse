package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.sign.MaidSignManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MaidDeathEvent is cancellable; only refund after the actual death decision. */
@Mixin(EntityMaid.class)
public abstract class MaidSignDeathMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void callresponse$dropSignBeforeFilm(CallbackInfo ci) {
        MaidSignManager.dropOnDeath((EntityMaid) (Object) this);
    }
}
