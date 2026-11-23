package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class WanderingCampSleepMixin {
    @Unique private BlockPos callresponse$wakingCarpet;
    @ModifyArg(method="checkBedExists", at=@At(value="INVOKE",
            target="Lnet/neoforged/neoforge/event/EventHooks;canEntityContinueSleeping(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/player/Player$BedSleepingProblem;)Z",
            remap=false), index=1)
    private net.minecraft.world.entity.player.Player.BedSleepingProblem callresponse$registeredSurface(
            net.minecraft.world.entity.player.Player.BedSleepingProblem problem) {
        // Correct only the native missing-bed baseline BEFORE event dispatch. Unlike setting
        // true in an event listener, this preserves every subscriber's explicit false, at any priority.
        if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_POSSIBLE_HERE
                && (Object)this instanceof EntityMaid maid && WanderingCampData.sleepingOnBedding(maid)
                && CampSleepPlacement.eligible(maid)) return null;
        return problem;
    }
    @Inject(method="onSyncedDataUpdated", at=@At("TAIL"))
    private void callresponse$lateCampMetadata(net.minecraft.network.syncher.EntityDataAccessor<?> key, CallbackInfo ci) {
        // Initial tracked data can deliver SleepingPos before the camp metadata. Resolve once on metadata arrival.
        if ((Object)this instanceof EntityMaid maid && maid.level().isClientSide
                && ((CampMaid)maid).callresponse$isCampViewKey(key) && WanderingCampData.sleepingOnBedding(maid)) {
            BlockPos pos=WanderingCampData.head(maid);
            CampSleepPlacement.place(maid, pos);
        }
    }
    @Inject(method="setPosToBed", at=@At("HEAD"), cancellable=true)
    private void callresponse$placeOnCarpet(BlockPos pos, CallbackInfo ci) {
        if ((Object)this instanceof EntityMaid maid && CampSleepPlacement.place(maid, pos)) ci.cancel();
    }
    @Inject(method="stopSleeping", at=@At("HEAD"))
    private void callresponse$beforeWake(CallbackInfo ci) {
        callresponse$wakingCarpet = null;
        if ((Object)this instanceof EntityMaid maid && CampRoutineProfile.bound(maid).isPresent()
                && maid.position().distanceToSqr(new net.minecraft.world.phys.Vec3(
                        WanderingCampData.head(maid).getX()+.5, WanderingCampData.surfaceY(maid),
                        WanderingCampData.head(maid).getZ()+.5)) < 4)
            callresponse$wakingCarpet = maid.getSleepingPos().filter(WanderingCampData.head(maid)::equals).orElse(null);
    }
    @Inject(method="stopSleeping", at=@At("TAIL"))
    private void callresponse$groundWake(CallbackInfo ci) {
        if (callresponse$wakingCarpet != null && (Object)this instanceof EntityMaid maid) {
            // Native wake exits sleeping normally; replace only its bed-height fallback on this registered carpet.
            BlockPos p = callresponse$wakingCarpet;
            if (WanderingCampBrain.safeStand(maid,p)) CampSleepPlacement.place(maid,p);
            callresponse$wakingCarpet = null;
        }
    }
    @Inject(method="getBedOrientation", at=@At("HEAD"), cancellable=true)
    private void callresponse$registeredCarpet(CallbackInfoReturnable<Direction> cir) {
        if ((Object)this instanceof EntityMaid maid && WanderingCampData.active(maid) && maid.isSleeping()
                && maid.getSleepingPos().filter(WanderingCampData.head(maid)::equals).isPresent())
            CampRoutineProfile.bound(maid).ifPresent(p -> cir.setReturnValue(p.facing()));
    }
}
