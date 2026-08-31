package com.github.JumDa5he.callresponse.compat.state;

import com.github.JumDa5he.callresponse.compat.bauble.MoreEatBauble;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionDevotedManager;
import com.github.JumDa5he.callresponse.compat.hunger.HungerManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 只处理本附属历史版本已知的移动速度污染。 */
public final class MaidPathRepair {
    private static final double TLM_DEFAULT_SPEED = 0.7D;
    private static final double EPSILON = 1.0E-5D;
    private static final double[] LEGACY_HUNGER_BASES = {0.50D, 0.55D, 0.65D, 0.85D};

    private MaidPathRepair() {
    }

    public static Result cleanupKnownSpeedPollution(EntityMaid maid, boolean hasCallResponseSaveEvidence,
                                                     boolean repairLegacyBase) {
        AttributeInstance speed = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return new Result(0, false, Double.NaN, Double.NaN);
        }
        double before = speed.getBaseValue();
        int removed = 0;
        removed += remove(speed, HungerManager.SPEED_EFFECT_ID);
        removed += remove(speed, MoreEatBauble.SPEED_MODIFIER_ID);
        removed += remove(speed, EmotionDevotedManager.DEVOTED_SPEED_MODIFIER_ID);

        boolean baseRepaired = false;
        if (repairLegacyBase && hasCallResponseSaveEvidence && isLegacyBase(before)) {
            speed.setBaseValue(TLM_DEFAULT_SPEED);
            baseRepaired = true;
        }
        return new Result(removed, baseRepaired, before, speed.getBaseValue());
    }

    private static int remove(AttributeInstance speed, net.minecraft.resources.Identifier id) {
        if (speed.getModifier(id) == null) {
            return 0;
        }
        speed.removeModifier(id);
        return 1;
    }

    private static boolean isLegacyBase(double value) {
        for (double candidate : LEGACY_HUNGER_BASES) {
            if (Math.abs(value - candidate) <= EPSILON) {
                return true;
            }
        }
        return false;
    }

    public record Result(int removedModifiers, boolean baseRepaired, double oldBase, double newBase) {
    }
}
