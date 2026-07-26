package com.github.tartaricacid.callresponse.compat.hunger;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

public class HungerData {

    private static final String HUNGER_TAG = "MaidHunger";
    private static final float MAX_HUNGER = 100.0f;
    private static final float MIN_HUNGER = 0.0f;
    private static final float DEFAULT_HUNGER = 50.0f; // 默认正常值

    public static float get(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        if (tag.contains(HUNGER_TAG)) {
            return tag.getFloat(HUNGER_TAG);
        } else {
            // 首次获取时初始化为默认值并保存
            set(maid, DEFAULT_HUNGER);
            return DEFAULT_HUNGER;
        }
    }

    public static void set(EntityMaid maid, float value) {
        CompoundTag tag = maid.getPersistentData();
        float clamped = Math.max(MIN_HUNGER, Math.min(MAX_HUNGER, value));
        tag.putFloat(HUNGER_TAG, clamped);
    }

    public static void add(EntityMaid maid, float delta) {
        float current = get(maid);
        set(maid, current + delta);
    }

    // 获取饱食度区间（用于移速等）
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