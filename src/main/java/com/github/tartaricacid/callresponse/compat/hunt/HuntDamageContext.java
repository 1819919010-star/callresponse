package com.github.tartaricacid.callresponse.compat.hunt;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 狩猎伤害的线程内凭证。凭证保存原始 DamageSource 实例和进入 hurt 时的原始数值，
 * 供 Forge、TLM 饰品和最终生命值结算共同读取；不依赖任何附属模组类型。
 */
public final class HuntDamageContext {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Map<UUID, CapturedDamage>> CAPTURED =
            ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<IdentityHashMap<LivingEntity, Deque<Boolean>>> NATIVE_HURT_SCOPES =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<Long> FORCED_HIT_SEQUENCE = ThreadLocal.withInitial(() -> 0L);

    private HuntDamageContext() {
    }

    private record Frame(UUID targetId, float rawDamage, DamageSource source) {
    }

    private record CapturedDamage(float rawDamage, DamageSource source) {
    }

    public static float normalizeDamage(float damage) {
        if (Float.isNaN(damage) || damage <= 0.0F) {
            return 0.0F;
        }
        return Float.isInfinite(damage) ? Float.MAX_VALUE : damage;
    }

    public static void begin(UUID targetId, float rawDamage, DamageSource source) {
        FRAMES.get().push(new Frame(targetId, normalizeDamage(rawDamage), source));
    }

    public static void end() {
        Deque<Frame> frames = FRAMES.get();
        if (!frames.isEmpty()) {
            frames.pop();
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    private static void end(UUID targetId, DamageSource source) {
        Deque<Frame> frames = FRAMES.get();
        Iterator<Frame> iterator = frames.iterator();
        while (iterator.hasNext()) {
            Frame frame = iterator.next();
            if (frame.targetId().equals(targetId) && frame.source() == source) {
                iterator.remove();
                break;
            }
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    /** 在 LivingEntity.hurt 最外层建立凭证，并清除原版受伤频率状态。 */
    public static void enterNativeHurt(LivingEntity target, DamageSource source, float rawDamage) {
        boolean ownsFrame = false;
        if (HuntOrderManager.isHuntDamage(target, source)) {
            ownsFrame = !hasActive(target.getUUID(), source);
            if (ownsFrame) {
                begin(target.getUUID(), rawDamage, source);
                HuntOrderManager.queueForcedHit(target, source, rawDamage);
            }
        }
        NATIVE_HURT_SCOPES.get()
                .computeIfAbsent(target, ignored -> new ArrayDeque<>())
                .push(ownsFrame);
    }

    public static void exitNativeHurt(LivingEntity target, DamageSource source) {
        IdentityHashMap<LivingEntity, Deque<Boolean>> scopes = NATIVE_HURT_SCOPES.get();
        Deque<Boolean> targetScopes = scopes.get(target);
        if (targetScopes == null || targetScopes.isEmpty()) {
            return;
        }
        boolean ownsFrame = targetScopes.pop();
        if (targetScopes.isEmpty()) {
            scopes.remove(target);
        }
        if (scopes.isEmpty()) {
            NATIVE_HURT_SCOPES.remove();
        }
        if (ownsFrame) {
            end(target.getUUID(), source);
        }
    }

    public static boolean isSuppressingProtection() {
        return !FRAMES.get().isEmpty();
    }

    public static boolean hasActive(UUID targetId) {
        for (Frame frame : FRAMES.get()) {
            if (frame.targetId().equals(targetId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActive(UUID targetId, DamageSource source) {
        for (Frame frame : FRAMES.get()) {
            if (frame.targetId().equals(targetId) && sameSource(frame.source(), source)) {
                return true;
            }
        }
        return false;
    }

    /** LivingAttack 是原始攻击数值的权威入口；每次命中都覆盖，避免高射速命中相互污染。 */
    public static void captureAttack(UUID targetId, DamageSource source, float rawDamage) {
        CAPTURED.get().put(targetId, new CapturedDamage(normalizeDamage(rawDamage), source));
    }

    /** 仅在没有 LivingAttack 捕获时补录 LivingHurt，兼容跳过 Attack 阶段的伤害实现。 */
    public static boolean captureIfAbsent(UUID targetId, DamageSource source, float rawDamage) {
        return CAPTURED.get().putIfAbsent(
                targetId, new CapturedDamage(normalizeDamage(rawDamage), source)) == null;
    }

    public static void clearCaptured(UUID targetId) {
        Map<UUID, CapturedDamage> captured = CAPTURED.get();
        captured.remove(targetId);
        if (captured.isEmpty()) {
            CAPTURED.remove();
        }
    }

    public static float rawDamage(UUID targetId, float fallback) {
        for (Frame frame : FRAMES.get()) {
            if (frame.targetId().equals(targetId)) {
                return frame.rawDamage();
            }
        }
        CapturedDamage captured = CAPTURED.get().get(targetId);
        return captured == null ? normalizeDamage(fallback) : captured.rawDamage();
    }

    public static long forcedHitSequence() {
        return FORCED_HIT_SEQUENCE.get();
    }

    public static void markForcedHitQueued() {
        FORCED_HIT_SEQUENCE.set(FORCED_HIT_SEQUENCE.get() + 1L);
    }

    private static boolean sameSource(DamageSource first, DamageSource second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.typeHolder() != second.typeHolder()) {
            return false;
        }
        return first.getEntity() == second.getEntity() && first.getDirectEntity() == second.getDirectEntity();
    }
}
