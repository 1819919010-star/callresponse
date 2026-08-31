package com.github.JumDa5he.callresponse.compat.hunt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从任意 Event 的公共实体/伤害源 getter 中识别“狩猎女仆 -> 当前目标”。
 * 只依据 Minecraft/TLM 基础类型和通用 getter 语义，不包含任何附属模组类名。
 */
public final class HuntEventIntrospection {
    private static final ClassValue<Method[]> VALUE_GETTERS = new ClassValue<>() {
        @Override
        protected Method[] computeValue(Class<?> type) {
            List<Method> result = new ArrayList<>();
            for (Method method : type.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (Entity.class.isAssignableFrom(returnType)
                        || DamageSource.class.isAssignableFrom(returnType)) {
                    result.add(method);
                }
            }
            return result.toArray(Method[]::new);
        }
    };

    private HuntEventIntrospection() {
    }

    public static boolean belongsToHuntDamage(Event event) {
        if (!HuntOrderManager.hasActiveHunts()) {
            return false;
        }

        List<LivingEntity> targets = new ArrayList<>();
        List<Entity> attackers = new ArrayList<>();
        List<Entity> ambiguousMaids = new ArrayList<>();
        List<DamageSource> sources = new ArrayList<>();
        boolean hasExplicitTarget = false;

        for (Method getter : VALUE_GETTERS.get(event.getClass())) {
            Object value;
            try {
                value = getter.invoke(event);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                continue;
            }
            if (value instanceof DamageSource source) {
                sources.add(source);
                continue;
            }
            if (!(value instanceof Entity entity)) {
                continue;
            }

            String name = getter.getName().toLowerCase(Locale.ROOT);
            if (name.contains("target") || name.contains("hurtentity") || name.equals("getentity")) {
                if (entity instanceof LivingEntity living) {
                    targets.add(living);
                    hasExplicitTarget = true;
                }
            } else if (name.contains("attacker") || name.contains("owner")
                    || name.contains("shooter") || name.contains("source")
                    || name.contains("direct")) {
                attackers.add(entity);
            } else if (name.contains("maid")) {
                ambiguousMaids.add(entity);
            }
        }

        // TLM 的受伤事件只有 getMaid + getSource，getMaid 表示目标；
        // TLM 的主动攻击事件同时有 getTarget，此时 getMaid 表示攻击者。
        for (Entity maidValue : ambiguousMaids) {
            if (hasExplicitTarget) {
                attackers.add(maidValue);
            } else if (maidValue instanceof LivingEntity living) {
                targets.add(living);
            }
        }

        for (LivingEntity target : targets) {
            for (DamageSource source : sources) {
                if (HuntOrderManager.isHuntDamage(target, source)) {
                    return true;
                }
            }
            for (Entity attacker : attackers) {
                if (attacker instanceof EntityMaid maid && HuntOrderManager.isHuntTarget(maid, target)) {
                    return true;
                }
            }
        }

        // 目标选择事件通常只暴露“当前实体 + 新目标”，没有 DamageSource/attacker getter。
        // 在这种事件中按活动狩猎的明确方向配对，放行女仆锁定自己的狩猎目标。
        if (sources.isEmpty() && attackers.isEmpty()) {
            for (LivingEntity possibleAttacker : targets) {
                if (!(possibleAttacker instanceof EntityMaid maid)) {
                    continue;
                }
                for (LivingEntity possibleTarget : targets) {
                    if (possibleTarget != maid && HuntOrderManager.isHuntTarget(maid, possibleTarget)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
