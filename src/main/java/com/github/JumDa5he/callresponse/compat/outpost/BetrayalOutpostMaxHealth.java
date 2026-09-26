package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.mixin.accessor.RangedAttributeAccessor;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/** 只在原有 MAX_HEALTH 上限不足时提供一个对 float 同步较安全的全局上限。 */
public final class BetrayalOutpostMaxHealth {
    public static final double SAFE_FALLBACK_LIMIT = 1_000_000.0D;
    private static boolean logged;

    private BetrayalOutpostMaxHealth() {
    }

    public static double ensureAndGetLimit() {
        double current = currentLimit();
        if (current < SAFE_FALLBACK_LIMIT && Attributes.MAX_HEALTH.value() instanceof RangedAttribute ranged) {
            double previous = current;
            ((RangedAttributeAccessor) (Object) ranged).callresponse$setMaxValue(SAFE_FALLBACK_LIMIT);
            current = currentLimit();
            if (!logged) {
                CallResponseMod.LOGGER.info("MAX_HEALTH 允许上限已从 {} 提高到 {}",
                        Math.round(previous), Math.round(current));
                logged = true;
            }
        }
        return current;
    }

    /**
     * 用 sanitizeValue 读取实际生效上限，因此 AttributeFix/Apotheosis 若已改得更高，
     * 这里会直接沿用它们的结果，不写回、不调低。
     */
    public static double currentLimit() {
        double limit = Attributes.MAX_HEALTH.value().sanitizeValue(Double.MAX_VALUE);
        return Double.isFinite(limit) && limit > 0.0D ? limit : SAFE_FALLBACK_LIMIT;
    }
}
