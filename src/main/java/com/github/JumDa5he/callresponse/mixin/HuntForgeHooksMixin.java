package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageContext;
import com.github.JumDa5he.callresponse.compat.hunt.HuntDamageContext;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** CommonHooks 返回点的最终保险，避免同优先级监听器再次取消或归零。 */
@Mixin(value = CommonHooks.class, priority = 4000, remap = false)
public abstract class HuntForgeHooksMixin {
    @Inject(method = "onLivingDamagePre", at = @At("RETURN"), cancellable = true, remap = false)
    private static void callresponse$restoreHuntDamage(LivingEntity target, DamageContainer container,
                                                        CallbackInfoReturnable<Float> cir) {
        if (HuntOrderManager.isHuntDamage(target, container.getSource())) {
            cir.setReturnValue(HuntDamageContext.rawDamage(target.getUUID(), container.getNewDamage()));
        } else if (target instanceof EntityMaid maid
                && OwnerDamageContext.hasActiveDamage(maid, container.getSource())) {
            cir.setReturnValue(OwnerDamageContext.rawDamage(maid, container.getNewDamage()));
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
                || target instanceof EntityMaid maid
                && OwnerDamageContext.hasActiveDamage(maid, source);
    }
}
