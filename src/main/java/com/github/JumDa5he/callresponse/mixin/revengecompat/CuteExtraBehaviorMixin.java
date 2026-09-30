package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeAddonIsolation;

/** Cancel these add-on callbacks, NOT the Forge event or ordinary maid tick. */
@Pseudo
@Mixin(targets={"cn.autoforged.maid_cute_activity.LonelinessHandler",
        "cn.autoforged.maid_cute_activity.CompanionHandler",
        "cn.autoforged.maid_cute_activity.SleepWithYouHandler",
        "cn.autoforged.maid_cute_activity.BegFoodHandler",
        "cn.autoforged.maid_cute_activity.AfraidWeaponHandler",
        "cn.autoforged.maid_cute_activity.MoreExpressionHandler",
        "cn.autoforged.maid_cute_activity.MoreDanceHandler",
        "cn.autoforged.maid_cute_activity.ComfortOwnerHandler",
        "cn.autoforged.maid_cute_activity.EatTogetherHandler",
        "cn.autoforged.maid_cute_activity.WeatherAnimHandler",
        "cn.autoforged.maid_cute_activity.InterruptSleepHandler",
        "cn.autoforged.maid_cute_activity.MaidBellHandler",
        "cn.autoforged.maid_cute_activity.PetReactionHandler",
        "cn.autoforged.maid_cute_activity.TailBrushHandler",
        "cn.autoforged.maid_cute_activity.TailCircleHandler",
        "cn.autoforged.maid_cute_activity.SlapHandler",
        "cn.autoforged.maid_cute_activity.PullearHandler",
        "cn.autoforged.maid_cute_activity.WbhurtHandler",
        "cn.autoforged.maid_cute_activity.PullwbHandler",
        "cn.autoforged.maid_cute_activity.LeashHandler",
        "cn.autoforged.maid_cute_activity.LoveCakeHandler"}, remap=false, priority=900)
public abstract class CuteExtraBehaviorMixin {
    @Inject(method="onMaidTick", at=@At("HEAD"), cancellable=true, require=0)
    private static void tick(MaidTickEvent event, CallbackInfo ci) {
        if (event.getMaid() instanceof RevengeMaidEntity maid) {
            RevengeAddonIsolation.releaseCuteOwnedState(maid);
            ci.cancel();
        }
    }
    @Inject(method={"onLivingDamage(Lnet/minecraftforge/event/entity/living/LivingDamageEvent;)V",
            "onLivingDamagePre(Lnet/minecraftforge/event/entity/living/LivingDamageEvent;)V",
            "onLivingDamagePost(Lnet/minecraftforge/event/entity/living/LivingDamageEvent;)V"},
            at=@At("HEAD"), cancellable=true, require=0)
    private static void damage(LivingDamageEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method="onLivingAttack(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V", at=@At("HEAD"), cancellable=true, require=0)
    private static void attack(LivingAttackEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method="onKnockback(Lnet/minecraftforge/event/entity/living/LivingKnockBackEvent;)V", at=@At("HEAD"), cancellable=true, require=0)
    private static void knockback(LivingKnockBackEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method="onEntityInteract(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V", at=@At("HEAD"), cancellable=true, require=0)
    private static void interact(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        if (event.getTarget() instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method="onAttackEntity(Lnet/minecraftforge/event/entity/player/AttackEntityEvent;)V", at=@At("HEAD"), cancellable=true, require=0)
    private static void hit(AttackEntityEvent event, CallbackInfo ci) {
        if (event.getTarget() instanceof RevengeMaidEntity) ci.cancel();
    }
}
