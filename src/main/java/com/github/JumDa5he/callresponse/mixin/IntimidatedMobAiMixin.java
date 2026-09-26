package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gate only autonomous AI. Entity ticking, physics, damage and NBT remain vanilla. */
@Mixin(Mob.class)
public abstract class IntimidatedMobAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void callresponse$pauseTemporaryAi(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (IntimidationManager.isIntimidated(mob)) ci.cancel();
    }
}
