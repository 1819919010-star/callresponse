package com.github.JumDa5he.callresponse.compat.gift;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 陷阱礼物的实际效果。
 * <p>
 * 约定：所有爆炸都不破坏方块；默认只伤害并击飞开盒的玩家，
 * 只有高恐惧（恐惧 / 矛盾）才会波及范围内的其他生物（女仆除外）。
 */
public final class GiftTraps {

    private static final List<Pending> PENDING = new ArrayList<>();

    private GiftTraps() {
    }

    /** 开盒瞬间的上下文，延迟触发的陷阱也靠它回溯位置与情绪。 */
    public record TrapContext(ServerLevel level, Vec3 pos, ServerPlayer opener,
                              EntityMaid giver, EmotionData.EmotionTendency tendency) {
        public RandomSource random() {
            return level.getRandom();
        }
    }

    public static void apply(GiftKind kind, TrapContext ctx) {
        switch (kind) {
            case PRANK -> prank(ctx);
            case SPAWN -> schedule(ctx.level(), 20 + ctx.random().nextInt(20), () -> spawnMobs(ctx));
            case TNT -> schedule(ctx.level(), fuseTicks(ctx.tendency()), () -> explode(ctx, true));
            case BLAST -> schedule(ctx.level(), 5, () -> explode(ctx, false));
            case DEBUFF -> debuff(ctx);
            case GRUDGE -> schedule(ctx.level(), 15, () -> {
                spawnMobs(ctx);
                explode(ctx, false);
            });
            default -> {
            }
        }
    }

    // ===== 延迟任务 =====

    public static void schedule(ServerLevel level, int delayTicks, Runnable action) {
        PENDING.add(new Pending(level, Math.max(1, delayTicks), action));
    }

    /** 服务器关闭时清空，避免把旧存档的延迟任务带到下一个世界。 */
    public static void clearAll() {
        PENDING.clear();
    }

    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            if (pending.level != level) {
                continue;
            }
            if (--pending.ticksLeft <= 0) {
                iterator.remove();
                pending.action.run();
            }
        }
    }

    private static final class Pending {
        private final ServerLevel level;
        private int ticksLeft;
        private final Runnable action;

        private Pending(ServerLevel level, int ticksLeft, Runnable action) {
            this.level = level;
            this.ticksLeft = ticksLeft;
            this.action = action;
        }
    }

    // ===== 各陷阱 =====

    /** 引信长度由女仆当前情绪决定：越害怕越不给反应时间。 */
    private static int fuseTicks(EmotionData.EmotionTendency tendency) {
        return switch (tendency) {
            case BOND -> 70;
            case FRIENDLY -> 60;
            case NEUTRAL -> 45;
            case STRANGER -> 40;
            case FEARFUL -> 25;
            case TERRIFIED -> 15;
            case CONFLICTED -> 20;
        };
    }

    private static void prank(TrapContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 pos = ctx.pos();
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.8, pos.z, 30, 0.5, 0.4, 0.5, 0.05);
        level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6, pos.z, 20, 0.4, 0.3, 0.4, 0.1);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0F, 1.3F);
        ctx.opener().displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.callresponse.gift.prank"), true);
    }

    private static void debuff(TrapContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 pos = ctx.pos();
        ServerPlayer opener = ctx.opener();
        opener.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        opener.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0));
        level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.8, pos.z, 15, 0.4, 0.3, 0.4, 0.02);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void spawnMobs(TrapContext ctx) {
        ServerLevel level = ctx.level();
        ServerPlayer opener = ctx.opener();
        if (!opener.isAlive() || opener.level() != level) {
            return;
        }
        RandomSource random = ctx.random();
        int count = 1 + random.nextInt(isHighFear(ctx.tendency()) ? 3 : 2);
        for (int i = 0; i < count; i++) {
            EntityType<? extends Mob> type = pickMob(ctx.tendency(), random);
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 1.5 + random.nextDouble() * 2.0;
            BlockPos spawnPos = BlockPos.containing(opener.getX() + Math.cos(angle) * distance,
                    opener.getY(), opener.getZ() + Math.sin(angle) * distance);
            Mob mob = type.spawn(level, spawnPos, MobSpawnType.EVENT);
            if (mob != null) {
                mob.setTarget(opener);
            }
        }
        level.playSound(null, ctx.pos().x, ctx.pos().y, ctx.pos().z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    private static EntityType<? extends Mob> pickMob(EmotionData.EmotionTendency tendency, RandomSource random) {
        if (isHighFear(tendency)) {
            int roll = random.nextInt(100);
            if (roll < 25) {
                return EntityType.CREEPER;
            }
            if (roll < 55) {
                return EntityType.SKELETON;
            }
            if (roll < 80) {
                return EntityType.ZOMBIE;
            }
            return EntityType.SPIDER;
        }
        return random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SILVERFISH;
    }

    private static boolean isHighFear(EmotionData.EmotionTendency tendency) {
        return tendency == EmotionData.EmotionTendency.TERRIFIED
                || tendency == EmotionData.EmotionTendency.CONFLICTED;
    }

    private static void explode(TrapContext ctx, boolean big) {
        ServerLevel level = ctx.level();
        Vec3 pos = ctx.pos();
        double radius = big ? 4.5 : 3.0;
        float baseDamage = big ? 8.0F : 5.0F;

        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.5, pos.z, 25, 0.7, 0.5, 0.7, 0.02);
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z, 15, 0.5, 0.4, 0.5, 0.01);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, big ? 1.4F : 1.0F, 1.0F);

        ServerPlayer opener = ctx.opener();
        if (opener.isAlive() && opener.level() == level) {
            double distance = opener.position().distanceTo(pos);
            if (distance <= radius) {
                float damage = (float) (baseDamage * (1.0 - distance / radius)) + 1.0F;
                opener.hurt(level.damageSources().explosion(ctx.giver() != null ? ctx.giver() : opener, opener), damage);
                knockback(opener, pos, big ? 1.3 : 0.9, big ? 0.9 : 0.6);
            }
        }

        // 高恐惧才会波及旁人；女仆一律不伤，避免把别人的女仆炸了
        if (isHighFear(ctx.tendency())) {
            AABB area = new AABB(pos, pos).inflate(radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (target == opener || !target.isAlive() || target instanceof EntityMaid) {
                    continue;
                }
                target.hurt(level.damageSources().explosion(ctx.giver() != null ? ctx.giver() : opener, target),
                        baseDamage * 0.5F);
                knockback(target, pos, 0.8, 0.5);
            }
        }
    }

    private static void knockback(LivingEntity target, Vec3 center, double horizontal, double vertical) {
        Vec3 direction = target.position().subtract(center);
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 push = flat.normalize().scale(horizontal);
        target.push(push.x, vertical, push.z);
        target.hurtMarked = true;
    }
}
