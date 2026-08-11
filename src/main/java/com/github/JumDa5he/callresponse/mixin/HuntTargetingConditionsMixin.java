package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Treats the exact hunt-order target as hostile in the vanilla/TLM combat target check.
 * Range, life state, visibility and line of sight remain unchanged.
 */
@Mixin(value = TargetingConditions.class, priority = 4000)
public abstract class HuntTargetingConditionsMixin {
    @Redirect(
            method = "test",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean callresponse$treatHuntTargetAsEnemy(LivingEntity attacker, Entity target) {
        if (attacker instanceof EntityMaid maid
                && target instanceof LivingEntity livingTarget
                && HuntOrderManager.isHuntTarget(maid, livingTarget)) {
            return false;
        }
        return attacker.isAlliedTo(target);
    }
}
