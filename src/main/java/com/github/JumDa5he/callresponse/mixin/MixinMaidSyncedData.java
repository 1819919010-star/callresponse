package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.npc.MaidReviveEventData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.state.MaidPathRepair;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityMaid.class)
public abstract class MixinMaidSyncedData extends Mob {
    private static final String SAVE_KEY = "CallResponseSyncData";

    private MixinMaidSyncedData() {
        super(null, null);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void callresponse$defineSyncedData(CallbackInfo ci) {
        this.getEntityData().define(EmotionData.EMOTION_KEY, new CompoundTag());
        this.getEntityData().define(HungerData.HUNGER_KEY, HungerData.DEFAULT_HUNGER);
        this.getEntityData().define(WanderingMaidData.SPECIAL_SYNC, false);
        this.getEntityData().define(TradingMaidData.TRADING_SYNC, false);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void callresponse$saveSyncedData(CompoundTag tag, CallbackInfo ci) {
        CompoundTag data = new CompoundTag();
        data.put("Emotions", this.getEntityData().get(EmotionData.EMOTION_KEY));
        data.putFloat("Hunger", this.getEntityData().get(HungerData.HUNGER_KEY));
        tag.put(SAVE_KEY, data);
        MaidReviveEventData.writeAdditionalSaveData((EntityMaid) (Object) this, tag);
        MaidMovementControl.sanitizeSave((EntityMaid) (Object) this, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void callresponse$loadSyncedData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(SAVE_KEY)) {
            CompoundTag data = tag.getCompound(SAVE_KEY);
            this.getEntityData().set(EmotionData.EMOTION_KEY, data.getCompound("Emotions"));
            this.getEntityData().set(HungerData.HUNGER_KEY, data.getFloat("Hunger"));
        }
        MaidReviveEventData.readAdditionalSaveData((EntityMaid) (Object) this, tag);
        WanderingMaidData.restoreSyncedFlag((EntityMaid) (Object) this);
        TradingMaidData.restoreSyncedFlag((EntityMaid) (Object) this);
        MaidPathRepair.cleanupKnownSpeedPollution((EntityMaid) (Object) this,
                tag.contains(SAVE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND), true);
        MaidMovementControl.recoverOnLoad((EntityMaid) (Object) this);
    }
}
