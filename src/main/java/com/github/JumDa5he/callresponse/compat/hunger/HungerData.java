package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public class HungerData {

    public static final float MAX_HUNGER = 100.0f;
    public static final float MIN_HUNGER = 0.0f;
    public static final float DEFAULT_HUNGER = 50.0f;

    // ===== 同步数据 key（通过 SynchedEntityData 自动同步到客户端） =====
    public static final EntityDataAccessor<Float> HUNGER_KEY =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.FLOAT);

    public static float get(EntityMaid maid) {
        return maid.getEntityData().get(HUNGER_KEY);
    }

    public static void set(EntityMaid maid, float value) {
        float clamped = Math.max(MIN_HUNGER, Math.min(MAX_HUNGER, value));
        maid.getEntityData().set(HUNGER_KEY, clamped);
    }

    public static void add(EntityMaid maid, float delta) {
        float current = get(maid);
        set(maid, current + delta);
    }

    public static HungerLevel getLevel(EntityMaid maid) {
        float hunger = get(maid);
        if (hunger <= 9) return HungerLevel.STARVING;
        if (hunger <= 25) return HungerLevel.VERY_HUNGRY;
        if (hunger <= 40) return HungerLevel.HUNGRY;
        if (hunger <= 74) return HungerLevel.SATISFIED;
        if (hunger <= 90) return HungerLevel.FULL;
        return HungerLevel.OVERFED;
    }

    public enum HungerLevel {
        STARVING, VERY_HUNGRY, HUNGRY, SATISFIED, FULL, OVERFED
    }
}
