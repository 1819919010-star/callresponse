package com.github.JumDa5he.callresponse.compat.hunt;

import com.github.JumDa5he.callresponse.mixin.accessor.LivingEntityHealthAccessorMixin;
import com.github.JumDa5he.callresponse.mixin.accessor.SynchedEntityDataAccessorMixin;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * 直接写入原版 LivingEntity 的同步生命数据项。
 * 这条路径不调用 setHealth 或 SynchedEntityData.set，因此不会再次进入任何伤害限制器。
 */
public final class HuntRawHealth {
    private HuntRawHealth() {
    }

    @SuppressWarnings("unchecked")
    public static void write(LivingEntity target, float requestedHealth) {
        float health = Mth.clamp(requestedHealth, 0.0F, target.getMaxHealth());
        EntityDataAccessor<Float> healthAccessor =
                LivingEntityHealthAccessorMixin.callresponse$getHealthAccessor();
        SynchedEntityData entityData = target.getEntityData();
        SynchedEntityDataAccessorMixin rawData = (SynchedEntityDataAccessorMixin) (Object) entityData;
        SynchedEntityData.DataItem<?> untypedItem = null;
        for (SynchedEntityData.DataItem<?> item : rawData.callresponse$getItemsById()) {
            if (item.getAccessor().id() == healthAccessor.id()) {
                untypedItem = item;
                break;
            }
        }
        if (untypedItem == null || !(untypedItem.getValue() instanceof Float)) {
            throw new IllegalStateException("Missing vanilla living-entity health data item");
        }

        SynchedEntityData.DataItem<Float> healthItem = (SynchedEntityData.DataItem<Float>) untypedItem;
        if (Float.compare(healthItem.getValue(), health) == 0) {
            return;
        }
        healthItem.setValue(health);
        target.onSyncedDataUpdated(healthAccessor);
        healthItem.setDirty(true);
        rawData.callresponse$setDirty(true);
    }
}
