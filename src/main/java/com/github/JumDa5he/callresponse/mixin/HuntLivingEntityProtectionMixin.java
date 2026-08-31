package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageContext;
import com.github.JumDa5he.callresponse.compat.hunt.HuntDamageContext;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntRawHealth;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = 4000)
public abstract class HuntLivingEntityProtectionMixin {
    @Shadow
    protected float lastHurt;

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void callresponse$enterHuntDamage(ServerLevel level, DamageSource source, float amount,
                                               CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;
        HuntDamageContext.enterNativeHurt(target, source, amount);
        if (isProtectedBypass(target, source)) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            this.lastHurt = 0.0F;
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void callresponse$writeOwnerDamageHealth(float health, CallbackInfo ci) {
        if ((Object) this instanceof EntityMaid maid && OwnerDamageContext.hasActiveDamage(maid)) {
            HuntRawHealth.write(maid, Math.min(health,
                    OwnerDamageContext.desiredHealth(maid, health)));
            ci.cancel();
        }
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void callresponse$exitHuntDamage(ServerLevel level, DamageSource source, float amount,
                                              CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;
        HuntOrderManager.recordNativeHurtResult(target, source, cir.getReturnValue());
        HuntDamageContext.exitNativeHurt(target, source);
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void callresponse$disableHuntTotem(DamageSource source,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (isProtectedBypass((LivingEntity) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
    private void callresponse$disableProtectedItemBlocking(ServerLevel level, DamageSource source, float amount,
                                                            CallbackInfoReturnable<Float> cir) {
        if (isProtectedBypass((LivingEntity) (Object) this, source)) {
            cir.setReturnValue(amount);
        }
    }

    private static boolean isProtectedBypass(LivingEntity target, DamageSource source) {
        return HuntOrderManager.isHuntDamage(target, source)
                || target instanceof EntityMaid maid
                && OwnerDamageContext.hasActiveDamage(maid, source);
    }
}
