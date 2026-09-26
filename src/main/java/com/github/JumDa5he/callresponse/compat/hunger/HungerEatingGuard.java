package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.JumDa5he.callresponse.compat.bauble.BaubleDetector;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 统一管理禁食饰品和过饱保护，所有女仆主动进食入口都通过这里判断。 */
public final class HungerEatingGuard {
    private static final float OVERFED_TRIGGER = 90.0F;
    private static final float OVERFED_RELEASE = 85.0F;
    private static final long OVERFED_CONFIRM_TICKS = 20L * 3L;

    private static final Map<UUID, Long> overfedSince = new ConcurrentHashMap<>();
    private static final Set<UUID> overfedBlocked = ConcurrentHashMap.newKeySet();

    private HungerEatingGuard() {
    }

    public static void tick(EntityMaid maid, long gameTime) {
        UUID maidId = maid.getUUID();
        float hunger = HungerData.get(maid);
        if (hunger < OVERFED_RELEASE) {
            overfedSince.remove(maidId);
            overfedBlocked.remove(maidId);
            return;
        }
        if (overfedBlocked.contains(maidId)) {
            return;
        }
        if (hunger > OVERFED_TRIGGER) {
            long since = overfedSince.computeIfAbsent(maidId, ignored -> gameTime);
            if (gameTime < since || gameTime - since >= OVERFED_CONFIRM_TICKS) {
                overfedBlocked.add(maidId);
            }
        } else {
            // 尚未锁定时掉回 90 或以下，重新计算连续 3 秒。
            overfedSince.remove(maidId);
        }
    }

    public static boolean isBlocked(EntityMaid maid) {
        return BaubleDetector.hasNoEat(maid) || overfedBlocked.contains(maid.getUUID());
    }

    public static boolean isOverfedBlocked(EntityMaid maid) {
        return overfedBlocked.contains(maid.getUUID());
    }

    public static void clear(EntityMaid maid) {
        overfedSince.remove(maid.getUUID());
        overfedBlocked.remove(maid.getUUID());
    }
}
