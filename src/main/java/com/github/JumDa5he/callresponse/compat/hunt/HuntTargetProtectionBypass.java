package com.github.JumDa5he.callresponse.compat.hunt;

import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageContext;
import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidHurtEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * TLM/Forge 标准伤害事件层的狩猎放行器。
 * 更底层的 Event、Entity、LivingEntity 和 ForgeHooks 保险由通用 mixin 完成。
 */
public final class HuntTargetProtectionBypass {
    private static final ThreadLocal<Map<net.minecraft.world.level.Explosion, List<Entity>>> EXPLOSION_TARGETS =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureLivingAttack(LivingAttackEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            HuntDamageContext.captureAttack(event.getEntity().getUUID(), event.getSource(), event.getAmount());
            event.getEntity().invulnerableTime = 0;
        } else if (isOwnerDamage(event.getEntity(), event.getSource())) {
            event.getEntity().invulnerableTime = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowLivingAttack(LivingAttackEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())
                || isOwnerDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureLivingHurtFallback(LivingHurtEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            HuntDamageContext.captureIfAbsent(
                    event.getEntity().getUUID(), event.getSource(), event.getAmount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowLivingHurt(LivingHurtEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(HuntDamageContext.rawDamage(event.getEntity().getUUID(), event.getAmount()));
        } else if (isOwnerDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(OwnerDamageContext.rawDamage((EntityMaid) event.getEntity(), event.getAmount()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowLivingDamage(LivingDamageEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(HuntDamageContext.rawDamage(event.getEntity().getUUID(), event.getAmount()));
        } else if (isOwnerDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(OwnerDamageContext.rawDamage((EntityMaid) event.getEntity(), event.getAmount()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowLivingDeath(LivingDeathEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getEntity(), event.getSource())
                || isOwnerDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidAttack(MaidAttackEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())
                || OwnerDamageContext.hasActiveDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidHurt(MaidHurtEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(HuntDamageContext.rawDamage(event.getMaid().getUUID(), event.getAmount()));
        } else if (OwnerDamageContext.hasActiveDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(OwnerDamageContext.rawDamage(event.getMaid(), event.getAmount()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidDamage(MaidDamageEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(HuntDamageContext.rawDamage(event.getMaid().getUUID(), event.getAmount()));
        } else if (OwnerDamageContext.hasActiveDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
            event.setAmount(OwnerDamageContext.rawDamage(event.getMaid(), event.getAmount()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowMaidDeath(MaidDeathEvent event) {
        if (HuntOrderManager.isHuntDamage(event.getMaid(), event.getSource())
                || OwnerDamageContext.hasActiveDamage(event.getMaid(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    /**
     * 部分兼容层不是取消伤害事件，而是直接把友方从爆炸实体列表移除。
     * 在通用 Forge ExplosionEvent 的首尾保存并恢复狩猎目标和主人自己的女仆。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureExplosionTargets(ExplosionEvent.Detonate event) {
        List<Entity> restoreTargets = new ArrayList<>();
        Entity directSource = event.getExplosion().getDirectSourceEntity();
        for (Entity entity : event.getAffectedEntities()) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            boolean restore = HuntOrderManager.isHuntDamage(
                    target, event.getExplosion().getDamageSource());
            if (target instanceof EntityMaid maid) {
                restore |= OwnerDamageSource.isCurrentOwnerDamage(
                        maid, event.getExplosion().getDamageSource());
                restore |= OwnerDamageSource.isCurrentOwnerEntity(maid, directSource);
            }
            if (restore) {
                restoreTargets.add(entity);
            }
        }
        if (!restoreTargets.isEmpty()) {
            EXPLOSION_TARGETS.get().put(event.getExplosion(), restoreTargets);
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

    private static boolean isOwnerDamage(LivingEntity target,
                                         net.minecraft.world.damagesource.DamageSource source) {
        return target instanceof EntityMaid maid
                && OwnerDamageContext.hasActiveDamage(maid, source);
    }

}
