package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampData;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** These are the Cute Activity branches that directly replace tasks, targets or navigation. */
@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.LonelinessHandler", remap = false)
public abstract class CuteLonelinessCombatMixin {
    private static boolean callresponse$blockCombat(EntityMaid maid) {
        return CuteActivityBridge.isRevengeMaid(maid) || IntimidationManager.isIntimidated(maid)
                || WanderingCampData.active(maid);
    }

    @Inject(method = "handleWildMaidRetaliation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$noRetaliation(EntityMaid maid, LivingDamageEvent.Pre event, CallbackInfo ci) {
        if (callresponse$blockCombat(maid)) ci.cancel();
    }

    @Inject(method = "scanWildMaidCombatTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$noWildScan(EntityMaid maid, @Coerce Object state, long time, CallbackInfo ci) {
        callresponse$clearCampState(maid, state);
        if (callresponse$blockCombat(maid)) ci.cancel();
    }

    @Inject(method = "manageWildMaidCombat", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$noDirectWildCombat(EntityMaid maid, @Coerce Object state, long time, CallbackInfo ci) {
        callresponse$clearCampState(maid, state);
        if (callresponse$blockCombat(maid)) ci.cancel();
    }

    @Inject(method = "enterWildMaidAttackMode", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$keepCurrentTask(EntityMaid maid, @Coerce Object state, CallbackInfo ci) {
        callresponse$clearCampState(maid, state);
        if (callresponse$blockCombat(maid)) ci.cancel();
    }

    @Inject(method = "restoreWildMaidAttackMode", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$doNotRestoreOldCampTask(EntityMaid maid, @Coerce Object state, CallbackInfo ci) {
        callresponse$clearCampState(maid, state);
        if (CuteActivityBridge.isRevengeMaid(maid) || WanderingCampData.active(maid)) ci.cancel();
    }

    @Inject(method = "tryTriggerBegForMercyTalk", at = @At("HEAD"), cancellable = true, remap = false)
    private static void callresponse$noRevengeMercyTalk(EntityMaid maid, @Coerce Object state, long time, CallbackInfo ci) {
        if (CuteActivityBridge.isRevengeMaid(maid) || WanderingCampData.active(maid)) ci.cancel();
    }
    @Inject(method="manageFightArenaCombat", at=@At("HEAD"), cancellable=true)
    private static void callresponse$noCampArenaCombat(EntityMaid maid, @Coerce Object state, long time, CallbackInfo ci) {
        if (WanderingCampData.active(maid)) { callresponse$clearCampState(maid, state); ci.cancel(); }
    }
    @Inject(method="broadcastMessage", at=@At("HEAD"), cancellable=true)
    private static void callresponse$noCampExternalChat(EntityMaid maid, net.minecraft.network.chat.Component message, CallbackInfo ci) {
        if (WanderingCampData.active(maid)) ci.cancel();
    }
    private static void callresponse$clearCampState(EntityMaid maid, Object state) {
        if (WanderingCampData.active(maid) && state instanceof WanderingBehaviorAuthority.WildCombatState combat)
            combat.callresponse$discardCampCombat();
    }
}
