package com.github.JumDa5he.callresponse.compat.hunt;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidHurtEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * TLM/NeoForge 标准伤害事件层的狩猎放行器。
 * 更底层的 Event、Entity、LivingEntity 和 CommonHooks 保险由通用 mixin 完成。
 */
public final class HuntTargetProtectionBypass {
    private static final ThreadLocal<Map<net.minecraft.world.level.Explosion, List<Entity>>> EXPLOSION_TARGETS =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureLivingAttack(LivingIncomingDamageEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            HuntDamageContext.captureAttack(event.getEntity().getUUID(), event.getSource(), event.getAmount());
            event.getEntity().invulnerableTime = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowLivingAttack(LivingIncomingDamageEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureLivingDamageFallback(LivingDamageEvent.Pre event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            HuntDamageContext.captureIfAbsent(
                    event.getEntity().getUUID(), event.getSource(), event.getContainer().getNewDamage());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowLivingDamage(LivingDamageEvent.Pre event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            event.setNewDamage(HuntDamageContext.rawDamage(event.getEntity().getUUID(), event.getNewDamage()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowLivingDeath(LivingDeathEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidAttack(MaidAttackEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidHurt(MaidHurtEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(HuntDamageContext.rawDamage(event.getMaid().getUUID(), event.getAmount()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidDamage(MaidDamageEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(HuntDamageContext.rawDamage(event.getMaid().getUUID(), event.getAmount()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidDeath(MaidDeathEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    /**
     * 部分兼容层不是取消伤害事件，而是直接把友方从爆炸实体列表移除。
     * 在通用 ExplosionEvent 的首尾保存并恢复狩猎目标，不检查爆炸物或附属类型。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureExplosionTargets(ExplosionEvent.Detonate event) {
        List<Entity> protectedTargets = new ArrayList<>();
        for (Entity entity : event.getAffectedEntities()) {
            if (entity instanceof LivingEntity target
                    && HuntOrderManager.isHuntDamage(target,
                    target.level().damageSources().explosion(event.getExplosion()))) {
                protectedTargets.add(entity);
            }
        }
        if (!protectedTargets.isEmpty()) {
            EXPLOSION_TARGETS.get().put(event.getExplosion(), protectedTargets);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void restoreExplosionTargets(ExplosionEvent.Detonate event) {
        Map<net.minecraft.world.level.Explosion, List<Entity>> snapshots = EXPLOSION_TARGETS.get();
        List<Entity> protectedTargets = snapshots.remove(event.getExplosion());
        if (snapshots.isEmpty()) {
            EXPLOSION_TARGETS.remove();
        }
        if (protectedTargets != null) {
            for (Entity entity : protectedTargets) {
                if (!event.getAffectedEntities().contains(entity)) {
                    event.getAffectedEntities().add(entity);
                }
            }
        }
    }
}
