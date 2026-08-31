package com.github.JumDa5he.callresponse.compat.hunt;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 在枪械模组真正创建 DamageSource 之前，放行被 TLM 友军保护提前取消的狩猎命中。
 * 通过可选事件桥接注册，未安装对应枪械模组时不会加载其类。
 */
public final class HuntGunEventBridge {
    @FunctionalInterface
    private interface GunHitCapture {
        void capture(Event event, EntityMaid maid, LivingEntity target) throws ReflectiveOperationException;
    }

    private HuntGunEventBridge() {
    }

    public static void register() {
        registerOptionalEvent(
                "tacz",
                "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre",
                "getAttacker",
                "getHurtEntity",
                HuntGunEventBridge::createTaczCapture);
        registerOptionalEvent(
                "superbwarfare",
                "com.atsuishio.superbwarfare.api.event.ProjectileHitEvent$HitEntity",
                "getOwner",
                "getTarget",
                HuntGunEventBridge::createSuperbWarfareCapture);
    }

    @FunctionalInterface
    private interface CaptureFactory {
        GunHitCapture create(Class<?> eventClass) throws ReflectiveOperationException;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerOptionalEvent(String modId, String eventClassName,
                                               String attackerGetter, String targetGetter,
                                               CaptureFactory captureFactory) {
        if (!ModList.get().isLoaded(modId)) return;
        try {
            Class<?> rawClass = Class.forName(eventClassName);
            if (!Event.class.isAssignableFrom(rawClass)) {
                CallResponseMod.LOGGER.warn("狩猎枪械桥接跳过了非事件类：{}", eventClassName);
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getAttacker = rawClass.getMethod(attackerGetter);
            Method getTarget = rawClass.getMethod(targetGetter);
            GunHitCapture capture = captureFactory.create(rawClass);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (Class) eventClass,
                    event -> allowExplicitHuntHit((Event) event, getAttacker, getTarget, capture));
            CallResponseMod.LOGGER.info("已启用狩猎枪械命中桥接：{}", modId);
        } catch (ReflectiveOperationException | LinkageError e) {
            CallResponseMod.LOGGER.warn("无法注册 {} 的狩猎枪械命中桥接", modId, e);
        }
    }

    private static void allowExplicitHuntHit(Event event, Method getAttacker, Method getTarget,
                                             GunHitCapture capture) {
        try {
            Object attackerObject = getAttacker.invoke(event);
            Object targetObject = getTarget.invoke(event);
            if (attackerObject instanceof EntityMaid maid
                    && targetObject instanceof LivingEntity target
                    && HuntOrderManager.isHuntTarget(maid, target)
                    && event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                cancellable.setCanceled(false);
                capture.capture(event, maid, target);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            CallResponseMod.LOGGER.debug("读取枪械命中事件失败", e);
        }
    }

    private static GunHitCapture createTaczCapture(Class<?> eventClass) throws ReflectiveOperationException {
        Method getBaseAmount = eventClass.getMethod("getBaseAmount");
        Method isHeadShot = eventClass.getMethod("isHeadShot");
        Method getHeadshotMultiplier = eventClass.getMethod("getHeadshotMultiplier");
        Class<?> partClass = Class.forName("com.tacz.guns.api.event.common.GunDamageSourcePart");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object nonArmorPart = Enum.valueOf((Class<? extends Enum>) partClass.asSubclass(Enum.class),
                "NON_ARMOR_PIERCING");
        Method getDamageSource = eventClass.getMethod("getDamageSource", partClass);

        return (event, maid, target) -> {
            float damage = ((Number) getBaseAmount.invoke(event)).floatValue();
            if ((Boolean) isHeadShot.invoke(event)) {
                damage *= ((Number) getHeadshotMultiplier.invoke(event)).floatValue();
            }
            Object source = getDamageSource.invoke(event, nonArmorPart);
            if (source instanceof DamageSource damageSource) {
                HuntOrderManager.queueExplicitGunHit(maid, target, damageSource, damage);
            }
        };
    }

    private static GunHitCapture createSuperbWarfareCapture(Class<?> eventClass)
            throws ReflectiveOperationException {
        Method getProjectile = eventClass.getMethod("getProjectile");
        Method isHeadshot = eventClass.getMethod("isHeadshot");
        Method isLegShot = eventClass.getMethod("isLegShot");

        Class<?> projectileClass = Class.forName(
                "com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity");
        Method getDamage = projectileClass.getMethod("getDamage");
        Method getBypassArmorRate = projectileClass.getMethod("getBypassArmorRate");
        Field velocity = optionalAccessibleField(projectileClass, "velocity");
        Field legShotMultiplier = optionalAccessibleField(projectileClass, "legShot");

        Class<?> damageTypes = Class.forName("com.atsuishio.superbwarfare.init.ModDamageTypes");
        Method normalDamage = damageTypes.getMethod("causeGunFireDamage",
                RegistryAccess.class, Entity.class, Entity.class);
        Method headshotDamage = damageTypes.getMethod("causeGunFireHeadshotDamage",
                RegistryAccess.class, Entity.class, Entity.class);
        Method absoluteDamage = damageTypes.getMethod("causeGunFireAbsoluteDamage",
                RegistryAccess.class, Entity.class, Entity.class);
        Method absoluteHeadshotDamage = damageTypes.getMethod("causeGunFireHeadshotAbsoluteDamage",
                RegistryAccess.class, Entity.class, Entity.class);

        return (event, maid, target) -> {
            Object projectileObject = getProjectile.invoke(event);
            if (!(projectileObject instanceof Projectile projectile)
                    || !projectileClass.isInstance(projectileObject)) {
                return;
            }

            float damage = ((Number) getDamage.invoke(projectileObject)).floatValue();
            if (velocity != null) {
                float baseVelocity = velocity.getFloat(projectileObject);
                if (baseVelocity > 0.0F) {
                    damage *= (float) (projectile.getDeltaMovement().length() / baseVelocity);
                }
            }
            if ((Boolean) isLegShot.invoke(event) && legShotMultiplier != null) {
                damage *= legShotMultiplier.getFloat(projectileObject);
            }

            boolean headshot = (Boolean) isHeadshot.invoke(event);
            boolean bypassArmor = ((Number) getBypassArmorRate.invoke(projectileObject)).floatValue() > 0.0F;
            Method sourceFactory = bypassArmor
                    ? (headshot ? absoluteHeadshotDamage : absoluteDamage)
                    : (headshot ? headshotDamage : normalDamage);
            Object source = sourceFactory.invoke(null, target.level().registryAccess(), projectile, maid);
            if (source instanceof DamageSource damageSource) {
                HuntOrderManager.queueExplicitGunHit(maid, target, damageSource, damage);
            }
        };
    }

    private static Field optionalAccessibleField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
