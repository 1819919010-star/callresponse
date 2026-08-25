package com.github.JumDa5he.callresponse.compat.damage;

import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 不硬引用枪械模组类型地追溯伤害实体的 owner/shooter/caster。 */
public final class OwnerDamageSource {
    private static final String[] OWNER_GETTERS = {"getOwner", "getShooter", "getCaster"};
    private static final String[] DAMAGE_EVENT_WORDS = {
            "damage", "hurt", "attack", "death", "projectile", "bullet", "gun", "hitentity"
    };

    private OwnerDamageSource() {
    }

    public static boolean isEnabled() {
        return BroadcastConfig.OWNER_DAMAGE_BYPASS_ENABLED.get();
    }

    public static boolean isCurrentOwnerDamage(EntityMaid maid, DamageSource source) {
        if (!isEnabled() || maid.getOwnerUUID() == null || source == null) {
            return false;
        }
        UUID ownerId = maid.getOwnerUUID();
        return belongsToPlayer(source.getEntity(), ownerId, 0, new HashSet<>())
                || belongsToPlayer(source.getDirectEntity(), ownerId, 0, new HashSet<>());
    }

    /** 配置关闭时仍用于保留原有情感反馈判定，不代表启用保护破除。 */
    public static boolean isCurrentOwnerSource(EntityMaid maid, DamageSource source) {
        if (maid.getOwnerUUID() == null || source == null) {
            return false;
        }
        UUID ownerId = maid.getOwnerUUID();
        return belongsToPlayer(source.getEntity(), ownerId, 0, new HashSet<>())
                || belongsToPlayer(source.getDirectEntity(), ownerId, 0, new HashSet<>());
    }

    @Nullable
    public static ServerPlayer findServerPlayer(EntityMaid maid, DamageSource source) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null || source == null) {
            return null;
        }
        Player player = findPlayer(source.getEntity(), ownerId, 0, new HashSet<>());
        if (player == null) {
            player = findPlayer(source.getDirectEntity(), ownerId, 0, new HashSet<>());
        }
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    /**
     * TACZ/Superb Warfare 在真正 hurt 之前会取消自己的命中事件。
     * 这里只识别同时暴露“受害女仆 + 当前主人/原始来源”的伤害类事件。
     */
    public static boolean belongsToOwnerDamageEvent(Event event) {
        return belongsToOwnerDamageEvent((Object) event);
    }

    /**
     * 给可选枪械兼容 Mixin 使用。参数保留为 Object，避免在未安装枪械模组时
     * 对 TACZ/Superb Warfare 的事件类型产生硬链接。
     */
    public static boolean belongsToOwnerDamageEvent(Object event) {
        if (!isEnabled() || event == null) {
            return false;
        }
        String eventName = event.getClass().getName().toLowerCase(Locale.ROOT);
        boolean damageEvent = false;
        for (String word : DAMAGE_EVENT_WORDS) {
            if (eventName.contains(word)) {
                damageEvent = true;
                break;
            }
        }
        if (!damageEvent) {
            return false;
        }

        List<EntityMaid> targets = new ArrayList<>();
        List<Entity> attackers = new ArrayList<>();
        List<DamageSource> sources = new ArrayList<>();
        for (Method method : event.getClass().getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!Entity.class.isAssignableFrom(returnType)
                    && !DamageSource.class.isAssignableFrom(returnType)) {
                continue;
            }
            Object value;
            try {
                value = method.invoke(event);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (value instanceof DamageSource source) {
                sources.add(source);
            } else if (value instanceof EntityMaid maid
                    && (name.contains("target") || name.contains("hurt")
                    || name.equals("getentity") || name.contains("maid") || name.contains("victim"))) {
                targets.add(maid);
            } else if (value instanceof Entity entity
                    && (name.contains("attacker") || name.contains("owner")
                    || name.contains("shooter") || name.contains("caster") || name.contains("source"))) {
                attackers.add(entity);
            }
        }

        for (EntityMaid target : targets) {
            for (DamageSource source : sources) {
                if (isCurrentOwnerDamage(target, source)
                        || OwnerDamageContext.hasActiveDamage(target, source)) {
                    return true;
                }
            }
            UUID ownerId = target.getOwnerUUID();
            if (ownerId == null) {
                continue;
            }
            for (Entity attacker : attackers) {
                if (belongsToPlayer(attacker, ownerId, 0, new HashSet<>())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 爆炸事件可从弹体本身追溯 owner 时，也只放行该女仆真正的当前主人。 */
    public static boolean isCurrentOwnerEntity(EntityMaid maid, @Nullable Entity sourceEntity) {
        if (!isEnabled() || maid.getOwnerUUID() == null) {
            return false;
        }
        return belongsToPlayer(sourceEntity, maid.getOwnerUUID(), 0, new HashSet<>());
    }

    private static boolean belongsToPlayer(@Nullable Entity entity, UUID ownerId, int depth,
                                           Set<Entity> visited) {
        return findPlayer(entity, ownerId, depth, visited) != null;
    }

    @Nullable
    private static Player findPlayer(@Nullable Entity entity, UUID ownerId, int depth,
                                     Set<Entity> visited) {
        if (entity == null || depth > 4 || !visited.add(entity)) {
            return null;
        }
        if (entity instanceof Player player) {
            return player.getUUID().equals(ownerId) ? player : null;
        }
        if (entity instanceof Projectile projectile) {
            Player player = findPlayer(projectile.getOwner(), ownerId, depth + 1, visited);
            if (player != null) {
                return player;
            }
        }
        if (entity instanceof PrimedTnt tnt) {
            Player player = findPlayer(tnt.getOwner(), ownerId, depth + 1, visited);
            if (player != null) {
                return player;
            }
        }
        for (String getterName : OWNER_GETTERS) {
            try {
                Method getter = entity.getClass().getMethod(getterName);
                Object owner = getter.invoke(entity);
                if (owner instanceof Player player && player.getUUID().equals(ownerId)) {
                    return player;
                }
                if (owner instanceof Entity ownerEntity) {
                    Player player = findPlayer(ownerEntity, ownerId, depth + 1, visited);
                    if (player != null) {
                        return player;
                    }
                }
                if (owner instanceof UUID uuid && uuid.equals(ownerId)) {
                    Player player = entity.level().getPlayerByUUID(ownerId);
                    if (player != null) {
                        return player;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 可选模组的实体可能只实现其中一个常见 getter，继续尝试即可。
            }
        }
        return null;
    }
}
