package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageContext;
import com.github.JumDa5he.callresponse.compat.hunt.HuntDamageContext;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ForgeHooks 返回点的最终保险，避免同优先级监听器再次取消或归零。 */
@Mixin(value = ForgeHooks.class, priority = 4000, remap = false)
public abstract class HuntForgeHooksMixin {
    @Inject(method = "onLivingAttack", at = @At("RETURN"), cancellable = true, remap = false)
    private static void callresponse$forceHuntAttack(LivingEntity target, DamageSource source,
                                                      float amount, CallbackInfoReturnable<Boolean> cir) {
        if (isHuntOrScopedDamage(target, source)) {
            HuntDamageContext.captureAttack(target.getUUID(), source, amount);
            target.invulnerableTime = 0;
            cir.setReturnValue(true);
        } else if (target instanceof EntityMaid maid
                && OwnerDamageContext.hasActiveDamage(maid, source)) {
            if (OwnerDamageContext.isUltimate(maid, source)) {
                target.invulnerableTime = 0;
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onLivingHurt", at = @At("RETURN"), cancellable = true, remap = false)
    private static void callresponse$restoreHuntHurt(LivingEntity target, DamageSource source,
                                                      float amount, CallbackInfoReturnable<Float> cir) {
        if (isHuntOrScopedDamage(target, source)) {
            cir.setReturnValue(HuntDamageContext.rawDamage(target.getUUID(), amount));
        } else if (target instanceof EntityMaid maid
                && OwnerDamageContext.isUltimate(maid, source)) {
            cir.setReturnValue(OwnerDamageContext.rawDamage(maid, amount));
        }
    }

    @Inject(method = "onLivingDamage", at = @At("RETURN"), cancellable = true, remap = false)
    private static void callresponse$restoreHuntDamage(LivingEntity target, DamageSource source,
                                                        float amount, CallbackInfoReturnable<Float> cir) {
        if (isHuntOrScopedDamage(target, source)) {
            cir.setReturnValue(HuntDamageContext.rawDamage(target.getUUID(), amount));
        } else if (target instanceof EntityMaid maid
                && OwnerDamageContext.isUltimate(maid, source)) {
            cir.setReturnValue(OwnerDamageContext.rawDamage(maid, amount));
        }
    }

    @Inject(method = "onLivingDeath", at = @At("RETURN"), cancellable = true, remap = false)
    private static void callresponse$forceHuntDeath(LivingEntity target, DamageSource source,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (isProtectedBypass(target, source)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isProtectedBypass(LivingEntity target, DamageSource source) {
        return HuntOrderManager.isHuntDamage(target, source)
                || HuntDamageContext.hasActiveDamage(target, source)
                || target instanceof EntityMaid maid
                && OwnerDamageContext.isUltimate(maid, source);
    }

    private static boolean isHuntOrScopedDamage(LivingEntity target, DamageSource source) {
        return HuntOrderManager.isHuntDamage(target, source)
                || HuntDamageContext.hasActiveDamage(target, source);
    }
}
