package com.github.JumDa5he.callresponse.compat.damage;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** 精确替换 TLM 枪械兼容层对爆炸女仆名单的全局过滤。 */
public final class OwnerGunExplosionProtection {
    private static final String TACZ_PROJECTILE =
            "com.tacz.guns.entity.EntityKineticBullet";
    private static final String SUPERB_WARFARE_PROJECTILE =
            "com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity";
    private static final String SUPERB_WARFARE_THROWABLE_PROJECTILE =
            "com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile";

    private OwnerGunExplosionProtection() {
    }

    /**
     * @return true 表示已接管本次 TLM 爆炸过滤，调用方应取消原来的“移除全部女仆”处理。
     */
    public static boolean filterProtectedMaids(ExplosionEvent.Detonate event) {
        if (!OwnerDamageSource.isEnabled()) {
            return false;
        }
        Entity directSource = event.getExplosion().getDirectSourceEntity();
        if (!isSupportedGunProjectile(directSource)) {
            return false;
        }

        event.getAffectedEntities().removeIf(entity -> {
            if (!(entity instanceof EntityMaid maid)) {
                return false;
            }
            // 野生女仆没有主人，不属于友军保护对象，保留正常爆炸伤害。
            if (maid.getOwnerUUID() == null) {
                return false;
            }
            // 有主女仆仅在爆炸确实来自她自己的主人时放行；其他女仆继续受 TLM 保护。
            return !OwnerDamageSource.isCurrentOwnerEntity(maid, directSource);
        });
        return true;
    }

    private static boolean isSupportedGunProjectile(Entity entity) {
        if (entity == null) {
            return false;
        }
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            String className = type.getName();
            if (TACZ_PROJECTILE.equals(className)
                    || SUPERB_WARFARE_PROJECTILE.equals(className)
                    || SUPERB_WARFARE_THROWABLE_PROJECTILE.equals(className)) {
                return true;
            }
        }
        return false;
    }
}
