package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/** Vanilla 1.21 instant harm is implemented by this concrete effect, bypassing canBeAffected. */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class WineFoxInstantHarmMixin {
    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void callresponse$rejectWineFoxInstantHarm(@Nullable Entity direct, @Nullable Entity owner,
            LivingEntity target, int amplifier, double factor, CallbackInfo ci) {
        if ((Object) this == MobEffects.HARM.value() && target instanceof WineFoxEntity) ci.cancel();
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void callresponse$rejectWineFoxHarmTick(LivingEntity target, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == MobEffects.HARM.value() && target instanceof WineFoxEntity) cir.setReturnValue(true);
    }
}
