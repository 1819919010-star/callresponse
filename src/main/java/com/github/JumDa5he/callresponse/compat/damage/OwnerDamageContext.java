package com.github.JumDa5he.callresponse.compat.damage;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.UUID;

/**
 * 主人伤害专用的线程内凭证。
 * 与狩猎凭证完全隔离，只保存本次原始 DamageSource、原始伤害和预期生命值。
 */
public final class OwnerDamageContext {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private OwnerDamageContext() {
    }

    private static final class Frame {
        private final UUID maidId;
        private final DamageSource source;
        private final float rawDamage;
        private final float desiredHealth;
        private final ProtectionBreakLevel level;
        private boolean deathStarted;

        private Frame(UUID maidId, DamageSource source, float rawDamage, float desiredHealth,
                      ProtectionBreakLevel level) {
            this.maidId = maidId;
            this.source = source;
            this.rawDamage = rawDamage;
            this.desiredHealth = desiredHealth;
            this.level = level;
        }
    }

    public static void begin(EntityMaid maid, DamageSource source, float rawDamage,
                             ProtectionBreakLevel level) {
        float normalized = normalizeDamage(rawDamage);
        FRAMES.get().push(new Frame(maid.getUUID(), source, normalized,
                Math.max(0.0F, maid.getHealth() - normalized), level));
    }

    public static void end(EntityMaid maid, DamageSource source) {
        Deque<Frame> frames = FRAMES.get();
        Iterator<Frame> iterator = frames.iterator();
        while (iterator.hasNext()) {
            Frame frame = iterator.next();
            if (frame.maidId.equals(maid.getUUID()) && sameSource(frame.source, source)) {
                iterator.remove();
                break;
            }
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    public static boolean hasActiveDamage(EntityMaid maid) {
        return frame(maid, null) != null;
    }

    public static boolean hasActiveDamage(EntityMaid maid, DamageSource source) {
        return frame(maid, source) != null;
    }

    public static boolean isSuppressingProtection() {
        return FRAMES.get().stream().anyMatch(frame -> frame.level == ProtectionBreakLevel.ULTIMATE);
    }

    public static boolean isBasic(EntityMaid maid, DamageSource source) {
        Frame frame = frame(maid, source);
        return frame != null && frame.level == ProtectionBreakLevel.BASIC;
    }

    public static boolean isUltimate(EntityMaid maid) {
        Frame frame = frame(maid, null);
        return frame != null && frame.level == ProtectionBreakLevel.ULTIMATE;
    }

    public static boolean isUltimate(EntityMaid maid, DamageSource source) {
        Frame frame = frame(maid, source);
        return frame != null && frame.level == ProtectionBreakLevel.ULTIMATE;
    }

    public static float rawDamage(EntityMaid maid, float fallback) {
        Frame frame = frame(maid, null);
        return frame == null ? normalizeDamage(fallback) : frame.rawDamage;
    }

    public static float desiredHealth(EntityMaid maid, float fallback) {
        Frame frame = frame(maid, null);
        return frame == null ? fallback : frame.desiredHealth;
    }

    public static void markDeathStarted(EntityMaid maid, DamageSource source) {
        Frame frame = frame(maid, source);
        if (frame != null) {
            frame.deathStarted = true;
        }
    }

    public static boolean wasDeathStarted(EntityMaid maid) {
        Frame frame = frame(maid, null);
        return frame != null && frame.deathStarted;
    }

    public static float normalizeDamage(float damage) {
        if (Float.isNaN(damage) || damage <= 0.0F) {
            return 0.0F;
        }
        return Float.isInfinite(damage) ? Float.MAX_VALUE : damage;
    }

    private static Frame frame(EntityMaid maid, DamageSource source) {
        for (Frame frame : FRAMES.get()) {
            if (frame.maidId.equals(maid.getUUID())
                    && (source == null || sameSource(frame.source, source))) {
                return frame;
            }
        }
        return null;
    }

    private static boolean sameSource(DamageSource first, DamageSource second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.typeHolder() != second.typeHolder()) {
            return false;
        }
        return first.getEntity() == second.getEntity()
                && first.getDirectEntity() == second.getDirectEntity();
    }
}
