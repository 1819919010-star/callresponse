package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.JumDa5he.callresponse.compat.bauble.BaubleDetector;
import com.github.JumDa5he.callresponse.compat.bauble.NoEatBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

public class HungerData {

    public static final String HUNGER_TAG = "MaidHunger";
    public static final float MAX_HUNGER = 100.0f;
    public static final float MIN_HUNGER = 0.0f;
    public static final float DEFAULT_HUNGER = 50.0f; // 默认正常值

    public static float get(EntityMaid maid) {
        return maid.getData(InitAttachTypes.SYNCED_HUNGER);
    }

    public static void set(EntityMaid maid, float value) {
        float minimum = BaubleDetector.hasNoEat(maid) ? NoEatBauble.MIN_HUNGER : MIN_HUNGER;
        float clamped = Math.clamp(value, minimum, MAX_HUNGER);
        maid.setData(InitAttachTypes.SYNCED_HUNGER, clamped);
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
