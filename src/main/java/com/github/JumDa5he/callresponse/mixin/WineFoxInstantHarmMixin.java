package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Vanilla instant harm bypasses canBeAffected and Forge Applicable in splash/cloud paths. */
@Mixin(MobEffect.class)
public abstract class WineFoxInstantHarmMixin {
    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void callresponse$rejectWineFoxInstantHarm(@Nullable Entity direct, @Nullable Entity owner,
            LivingEntity target, int amplifier, double factor, CallbackInfo ci) {
        if ((Object) this == MobEffects.HARM && target instanceof WineFoxEntity) ci.cancel();
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void callresponse$rejectWineFoxHarmTick(LivingEntity target, int amplifier, CallbackInfo ci) {
        if ((Object) this == MobEffects.HARM && target instanceof WineFoxEntity) ci.cancel();
    }
}
