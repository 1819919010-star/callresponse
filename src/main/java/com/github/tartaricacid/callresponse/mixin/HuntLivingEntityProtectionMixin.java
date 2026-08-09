package com.github.tartaricacid.callresponse.mixin;

import com.github.tartaricacid.callresponse.compat.hunt.HuntDamageContext;
import com.github.tartaricacid.callresponse.compat.hunt.HuntOrderManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 LivingEntity 基础层建立原始伤害凭证，并关闭无敌帧、盾牌和图腾。
 * 所有生物狩猎目标共用一至三级逻辑，且只在来源确实是当前狩猎女仆时生效。
 */
@Mixin(value = LivingEntity.class, priority = 4000)
public abstract class HuntLivingEntityProtectionMixin {
    @Shadow
    protected float lastHurt;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void callresponse$enterHuntDamage(DamageSource source, float amount,
                                               CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;
        HuntDamageContext.enterNativeHurt(target, source, amount);
        if (HuntOrderManager.isHuntDamage(target, source)) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            this.lastHurt = 0.0F;
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void callresponse$exitHuntDamage(DamageSource source, float amount,
                                              CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;
        HuntOrderManager.recordNativeHurtResult(target, source, cir.getReturnValue());
        HuntDamageContext.exitNativeHurt(target, source);
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void callresponse$disableHuntTotem(DamageSource source,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (HuntOrderManager.isHuntDamage((LivingEntity) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isDamageSourceBlocked", at = @At("HEAD"), cancellable = true)
    private void callresponse$disableHuntShield(DamageSource source,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (HuntOrderManager.isHuntDamage((LivingEntity) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }
}
