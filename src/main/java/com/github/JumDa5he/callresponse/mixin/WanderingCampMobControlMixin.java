package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class WanderingCampMobControlMixin {
    @Inject(method="setTarget", at=@At("HEAD"), cancellable=true)
    private void callresponse$nonCombatTarget(LivingEntity target, CallbackInfo ci) {
        if (target != null && (Object)this instanceof EntityMaid maid && CampPolicy.nonCombat(maid)) ci.cancel();
    }
    @Inject(method="dropCustomDeathLoot", at=@At("HEAD"), cancellable=true)
    private void callresponse$avoidPartialEquipmentDrops(CallbackInfo ci) {
        // Equipment will be transferred intact once by TLM dropEquipment. In particular,
        // do not first randomly drop/damage a weapon through Mob's normal drop chance.
        if ((Object)this instanceof EntityMaid maid && !maid.level().isClientSide
                && WanderingCampData.active(maid)) ci.cancel();
    }
}
