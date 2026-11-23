package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.UUID;

@Mixin(EntityMaid.class)
public abstract class WanderingCampMaidMixin extends TamableAnimal implements CampMaid {
    protected WanderingCampMaidMixin(EntityType<? extends TamableAnimal> type, Level level) { super(type, level); }
    @Unique private static final EntityDataAccessor<CompoundTag> callresponse$CAMP =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.COMPOUND_TAG);
    @Unique private boolean callresponse$taming;
    @Unique private int callresponse$pickupBefore;
    @Unique private int callresponse$pickupPoints;
    @Unique private UUID callresponse$donor;
    @Unique private net.minecraft.world.item.ItemStack callresponse$gift = net.minecraft.world.item.ItemStack.EMPTY;
    @Unique private int callresponse$inventoryBefore;
    @Unique private UUID callresponse$damageAttacker;
    @Unique private net.minecraft.world.damagesource.DamageSource callresponse$deathSource;
    @Shadow(remap=false) private boolean alreadyDropped;
    @Override public CompoundTag callresponse$campView() { return entityData.get(callresponse$CAMP); }
    @Override public void callresponse$campView(CompoundTag tag) { entityData.set(callresponse$CAMP, tag); }
    @Override public boolean callresponse$campTaming() { return callresponse$taming; }
    @Override public void callresponse$campTaming(boolean authorized) { callresponse$taming = authorized; }
    @Override public UUID callresponse$campDamageAttacker() { return callresponse$damageAttacker; }
    @Override public boolean callresponse$campDeathCommitted() { return this.dead; }
    @Override public boolean callresponse$isCampViewKey(EntityDataAccessor<?> key) { return key.equals(callresponse$CAMP); }

    @Inject(method="hurt", at=@At("HEAD"))
    private void callresponse$originalThreat(net.minecraft.world.damagesource.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // Existing maid-to-maid damage compatibility can replace the event source with generic damage.
        // Retain only the original attacker's UUID for this actual damage call, not a second damage path.
        callresponse$damageAttacker = source.getEntity() == null ? null : source.getEntity().getUUID();
    }

    @Inject(method="defineSynchedData", at=@At("TAIL"))
    private void callresponse$define(CallbackInfo ci) { entityData.define(callresponse$CAMP, new CompoundTag()); }
    @Inject(method={"setTask", "setSchedule", "setHomeModeEnable"},
            at=@At("HEAD"), cancellable=true, remap=false)
    private void callresponse$campDecision(CallbackInfo ci) {
        if (CampPolicy.blocksDomesticDecision((EntityMaid)(Object)this)) ci.cancel();
    }
    @Inject(method="die", at=@At("HEAD"))
    private void callresponse$deathSource(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        callresponse$deathSource = source;
    }
    @Inject(method="dropEquipment()V", at=@At("TAIL"))
    private void callresponse$dropActualCampInventory(CallbackInfo ci) {
        EntityMaid maid = (EntityMaid)(Object)this;
        if (!level().isClientSide && WanderingCampData.active(maid) && this.dead) {
            WanderingCampDeathDrops.transfer(maid);
            WanderingCampManager.deathDialogue(maid, callresponse$deathSource == null ? maid.damageSources().generic() : callresponse$deathSource);
            alreadyDropped = true; // TLM remove(KILLED) must not run the equipment stage again.
        }
    }
    @Inject(method="mobInteract", at=@At("HEAD"), cancellable=true)
    private void callresponse$invitation(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        EntityMaid maid = (EntityMaid)(Object)this;
        if (WanderingCampData.active(maid) && player.getItemInHand(hand).is(Items.CAKE))
            cir.setReturnValue(WanderingCampManager.invite(maid, player, hand));
    }
    @Inject(method="tameMaid", at=@At("HEAD"), cancellable=true, remap=false)
    private void callresponse$gateNativeTame(net.minecraft.world.item.ItemStack stack, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (WanderingCampData.active((EntityMaid)(Object)this) && !callresponse$taming) cir.setReturnValue(InteractionResult.PASS);
    }
    @Inject(method="startSleeping", at=@At("HEAD"), cancellable=true)
    private void callresponse$boundSleep(net.minecraft.core.BlockPos pos, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid)(Object)this;
        if (!maid.level().isClientSide && CampRoutineProfile.bound(maid).isPresent()
                && (maid.isSleeping() || !CampSleepPlacement.permitsStart(maid, pos))) ci.cancel();
    }
    @Inject(method="onMaidSleep", at=@At("HEAD"), cancellable=true, remap=false)
    private void callresponse$carpetHeight(CallbackInfo ci) {
        EntityMaid maid = (EntityMaid)(Object)this;
        if (CampSleepPlacement.maintain(maid)) {
            // Position is established once by setPosToBed. Do not fight physics/lerp every tick.
            ci.cancel();
        }
    }
    @Inject(method="pickupItem", at=@At("HEAD"), remap=false)
    private void callresponse$beforePickup(ItemEntity item, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        callresponse$pickupBefore = 0;
        EntityMaid maid = (EntityMaid)(Object)this;
        if (!simulate && !level().isClientSide && WanderingCampData.active(maid)
                && (item.getItem().isEdible() || item.getItem().is(Items.CAKE))) {
            callresponse$pickupBefore = item.getItem().getCount();
            callresponse$pickupPoints = WanderingCampManager.unwantedFood(item.getItem()) ? 0 : item.getItem().is(Items.CAKE) ? 10 : 1;
            CompoundTag itemData = item.saveWithoutId(new CompoundTag());
            callresponse$donor = itemData.hasUUID("Thrower") ? itemData.getUUID("Thrower") : null;
            callresponse$gift = item.getItem().copy();
            callresponse$inventoryBefore = callresponse$countGift(maid);
        }
    }
    @Inject(method="pickupItem", at=@At("RETURN"), remap=false)
    private void callresponse$afterPickup(ItemEntity item, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (callresponse$pickupBefore > 0) {
            int transferred = callresponse$pickupBefore - (item.isAlive() ? item.getItem().getCount() : 0);
            transferred = Math.min(transferred, callresponse$countGift((EntityMaid)(Object)this) - callresponse$inventoryBefore);
            callresponse$pickupBefore = 0;
            EntityMaid maid = (EntityMaid)(Object)this;
            if (transferred > 0 && WanderingCampData.active(maid)) {
                if (callresponse$pickupPoints == 0) WanderingCampManager.say(maid, "unwanted_food", 4);
                else WanderingCampData.credit(maid, callresponse$donor, transferred * callresponse$pickupPoints);
            }
        }
    }
    @Unique private int callresponse$countGift(EntityMaid maid) {
        var inv = maid.getAvailableInv(false);
        int total = 0;
        for (int i=0; i<inv.getSlots(); i++)
            if (net.minecraft.world.item.ItemStack.isSameItemSameTags(inv.getStackInSlot(i), callresponse$gift)) total += inv.getStackInSlot(i).getCount();
        return total;
    }
    @Inject(method="canAttack", at=@At("HEAD"), cancellable=true)
    private void callresponse$nonCombat(LivingEntity other, CallbackInfoReturnable<Boolean> cir) {
        if (CampPolicy.nonCombat((EntityMaid)(Object)this)) cir.setReturnValue(false);
    }
    @Inject(method="doHurtTarget", at=@At("HEAD"), cancellable=true)
    private void callresponse$noOutgoingAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (CampPolicy.nonCombat((EntityMaid)(Object)this)) cir.setReturnValue(false);
    }

}
