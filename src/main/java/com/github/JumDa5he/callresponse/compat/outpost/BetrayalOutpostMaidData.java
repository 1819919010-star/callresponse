package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.compat.api.AuthorUtil;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidAnimationPackage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.List;

/** 复仇女仆据点实体使用的无害附加 NBT。 */
public final class BetrayalOutpostMaidData {
    /** Limited ordinary detection around each maid; pursuit is also bounded around the camp. */
    public static final int CAMP_ACTIVITY_RADIUS = 40;
    private static final int CAMP_SEARCH_RADIUS = 24;
    private static final int CAMP_NAVIGATION_RADIUS = 40;
    private static final String ROOT = "CallResponseBetrayalOutpost";
    private static final String GROUP = "Group";
    private static final String ROLE = "Role";
    private static final String HOME = "Home";
    private static final String PROVOKED = "Provoked";
    private static final String MOVEMENT_SPEED_VERSION = "MovementSpeedVersion";
    private static final String NEXT_DIALOGUE_TIME = "NextDialogueGameTime";
    private static final String LAST_ENCOUNTER_LINE = "LastEncounterLine";
    private static final String LAST_COMBAT_LINE = "LastCombatLine";
    private static final String GLY_NEXT_DIALOGUE_TIME = "GlyNextDialogueGameTime";
    private static final String GLY_LAST_LINE = "GlyLastLine";
    private static final String GLY_NEXT_HURT_TIME = "GlyNextHurtGameTime";
    private static final String GLY_LAST_HURT_LINE = "GlyLastHurtLine";
    private static final String GLY_NEXT_SNOWBALL_TIME = "GlyNextSnowballGameTime";
    private static final String GLY_FLEE_UNTIL = "GlyFleeUntilGameTime";
    private static final String GLY_THREAT_X = "GlyThreatX";
    private static final String GLY_THREAT_Y = "GlyThreatY";
    private static final String GLY_THREAT_Z = "GlyThreatZ";
    private static final String GLY_PICKUP_ANIMATION_TIME = "GlyPickUpAnimationGameTime";
    private static final String LEGACY_EXTRA_HEALTH = "ExtraHealth";
    private static final long DIALOGUE_INTERVAL_TICKS = 20L * 15L;
    /** GLY 扔雪球：间隔、没目标时的重试间隔、射程。 */
    private static final int GLY_SNOWBALL_INTERVAL = 40;
    private static final int GLY_SNOWBALL_RETRY = 40;
    private static final double GLY_THROW_RANGE = 16.0D;
    /** 本体是掷出后 25 tick 再补一次捡球动画，给丢出动作留时间。 */
    private static final int GLY_PICKUP_ANIMATION_DELAY = 25;
    /** GLY 逃跑：持续时间与“算作跑掉了”的距离。 */
    private static final long GLY_FLEE_TICKS = 20L * 8L;
    private static final double GLY_SAFE_DISTANCE = 20.0D;
    private static final float GLY_FLEE_SPEED = 0.9F;
    private static final int ENCOUNTER_LINE_COUNT = 8;
    private static final int COMBAT_LINE_COUNT = 10;
    /** 作者彩蛋（i狐区）专用台词池，正文留空由作者自己写。 */
    private static final int ENCOUNTER_IFOX_LINE_COUNT = 3;
    private static final int COMBAT_IFOX_LINE_COUNT = 3;

    private BetrayalOutpostMaidData() {
    }

    public static void initialize(EntityMaid maid, String group, Role role, BlockPos home) {
        CompoundTag data = new CompoundTag();
        data.putString(GROUP, group);
        data.putString(ROLE, role.name());
        data.putLong(HOME, home.asLong());
        data.putBoolean(PROVOKED, false);
        data.putInt(MOVEMENT_SPEED_VERSION, 1);
        data.putLong(NEXT_DIALOGUE_TIME, 0L);
        data.putInt(LAST_ENCOUNTER_LINE, -1);
        data.putInt(LAST_COMBAT_LINE, -1);
        maid.getPersistentData().put(ROOT, data);
        ((OutpostMaidMarker) maid).callresponse$setOutpostMaid(true);
    }

    /**
     * 将据点女仆的工作、休息和睡眠日程都锚定到营地中心。
     * TLM 会按全局配置定期重写当前 restriction 半径。导航半径覆盖营地周边，
     * 追击仍受有限营地范围约束。
     */
    public static void ensureCampSchedule(EntityMaid maid) {
        if (!isOutpostMaid(maid)) return;
        BlockPos center = recordedHome(maid);
        if (center == null) return;
        var schedule = maid.getSchedulePos();
        if (!center.equals(schedule.getWorkPos())
                || !center.equals(schedule.getIdlePos())
                || !center.equals(schedule.getSleepPos())
                || !maid.level().dimension().location().equals(schedule.getDimension())
                || !schedule.isConfigured()) {
            schedule.setWorkPos(center);
            schedule.setIdlePos(center);
            schedule.setSleepPos(center);
            schedule.setDimension(maid.level().dimension().location());
            schedule.setConfigured(true);
        }
        if (!maid.isHomeModeEnable()) maid.setHomeModeEnable(true);
        if (!center.equals(maid.getRestrictCenter())
                || Math.abs(maid.getRestrictRadius() - CAMP_NAVIGATION_RADIUS) > 0.01F) {
            maid.restrictTo(center, CAMP_NAVIGATION_RADIUS);
        }
    }

    /** Both the maid and its target use the same fixed, saved camp XYZ as their boundary. */
    public static boolean isWithinPursuitArea(EntityMaid maid, LivingEntity target) {
        return isWithinCamp(maid, target.getX(), target.getY(), target.getZ());
    }

    public static boolean isMaidWithinPursuitArea(EntityMaid maid) {
        return isWithinCamp(maid, maid.getX(), maid.getY(), maid.getZ());
    }

    /** Return detection covers normal camp floors; ±2 Y is only for a teleport landing spot. */
    public static boolean isMaidWithinReturnArea(EntityMaid maid) {
        BlockPos center = recordedHome(maid);
        if (center == null) return false;
        double dx = maid.getX() - (center.getX() + 0.5D);
        double dz = maid.getZ() - (center.getZ() + 0.5D);
        double dy = maid.getY() - center.getY();
        return dx * dx + dz * dz <= CAMP_ACTIVITY_RADIUS * CAMP_ACTIVITY_RADIUS
                && dy >= -4.0D && dy <= 16.0D
                && !(maid.isInWater() && dy < -2.0D);
    }

    private static boolean isWithinCamp(EntityMaid maid, double x, double y, double z) {
        BlockPos center = recordedHome(maid);
        if (center == null) return false;
        double dx = x - (center.getX() + 0.5D);
        double dy = y - center.getY();
        double dz = z - (center.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= CAMP_ACTIVITY_RADIUS * CAMP_ACTIVITY_RADIUS;
    }

    public static AABB pursuitSearchArea(EntityMaid maid) {
        return maid.getBoundingBox().inflate(CAMP_SEARCH_RADIUS, 16.0D, CAMP_SEARCH_RADIUS);
    }

    /** 旧存档只转换一次：把剩余隐藏血池合并到真实 MAX_HEALTH，然后删除旧字段。 */
    public static void migrateLegacyExtraHealth(EntityMaid maid) {
        if (!isOutpostMaid(maid)) return;
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        if (!data.contains(LEGACY_EXTRA_HEALTH, CompoundTag.TAG_DOUBLE)) return;

        double remaining = Math.max(0.0D, data.getDouble(LEGACY_EXTRA_HEALTH));
        data.remove(LEGACY_EXTRA_HEALTH);
        maid.getPersistentData().put(ROOT, data);
        if (remaining <= 0.0D) return;

        AttributeInstance maxHealth = maid.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        double limit = BetrayalOutpostMaxHealth.ensureAndGetLimit();
        double add = Math.min(remaining, Math.max(0.0D, limit - maid.getMaxHealth()));
        if (add <= 0.0D) return;
        float oldHealth = maid.getHealth();
        maxHealth.setBaseValue(maxHealth.getBaseValue() + add);
        maid.setHealth((float) Math.min(maid.getMaxHealth(), oldHealth + add));
    }

    /** 旧营地实体只回调一次移动基值；新生成实体已在装备初始化时使用新比例。 */
    public static void updateLegacyMovementSpeed(EntityMaid maid) {
        if (!isOutpostMaid(maid)) return;
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        if (data.getInt(MOVEMENT_SPEED_VERSION) >= 1) return;
        AttributeInstance movement = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        double ratio = role(maid) == Role.HEAVY ? 0.7D / 0.6D : 0.9D / 0.8D;
        movement.setBaseValue(movement.getBaseValue() * ratio);
        data.putInt(MOVEMENT_SPEED_VERSION, 1);
        maid.getPersistentData().put(ROOT, data);
    }

    /** 营地中心的完整 XYZ 随实体 NBT 持久化；缺字段时仅从已保存的结构记录恢复。 */
    public static BlockPos recordedHome(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        if (data.contains(HOME, CompoundTag.TAG_LONG)) return BlockPos.of(data.getLong(HOME));
        BlockPos restored = null;
        if (maid.level() instanceof ServerLevel level) {
            BetrayalOutpostSavedData.Outpost outpost = BetrayalOutpostSavedData.get(level)
                    .findByKey(level, data.getString(GROUP));
            if (outpost != null) restored = outpost.center();
        }
        if (restored != null) {
            data.putLong(HOME, restored.asLong());
            maid.getPersistentData().put(ROOT, data);
        }
        return restored;
    }

    public static BlockPos home(EntityMaid maid) {
        BlockPos recorded = recordedHome(maid);
        return recorded != null ? recorded : maid.blockPosition();
    }

    public static boolean isOutpostMaid(EntityMaid maid) {
        return maid.level().isClientSide
                ? ((OutpostMaidMarker) maid).callresponse$isOutpostMaid()
                : maid.getPersistentData().contains(ROOT, CompoundTag.TAG_COMPOUND);
    }

    /** Correct only a real tame/owner write, including old saves and third-party changes. */
    public static void ensureUntamed(EntityMaid maid) {
        if (maid.level().isClientSide || !isOutpostMaid(maid)) return;
        if (maid.isTame()) maid.setTame(false, false);
        if (maid.getOwnerUUID() != null) maid.setOwnerUUID(null);
    }

    public static String group(EntityMaid maid) {
        return maid.getPersistentData().getCompound(ROOT).getString(GROUP);
    }

    public static Role role(EntityMaid maid) {
        String name = maid.getPersistentData().getCompound(ROOT).getString(ROLE);
        try {
            return Role.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return Role.SWORDSMAN;
        }
    }

    public static boolean areSisters(EntityMaid first, LivingEntity second) {
        if (!(second instanceof EntityMaid other) || !isOutpostMaid(first) || !isOutpostMaid(other)) {
            return false;
        }
        String group = group(first);
        return !group.isEmpty() && group.equals(group(other));
    }

    /** 被外来者攻击后切换到战斗台词，并允许下一 tick 立即说出第一句。 */
    public static void markProvoked(EntityMaid maid) {
        if (!isOutpostMaid(maid)) return;
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        if (!data.getBoolean(PROVOKED)) {
            data.putBoolean(PROVOKED, true);
            data.putLong(NEXT_DIALOGUE_TIME, 0L);
            maid.getPersistentData().put(ROOT, data);
        }
    }

    /**
     * 据点台词只由服务端触发。未受攻击时只对发现的生存/冒险玩家警告；受攻击后改说战斗台词。
     * 上一句索引和下次允许时间一并写入附加 NBT，避免区块重载造成刷屏或立即复读。
     */
    public static void tickDialogue(EntityMaid maid, LivingEntity target) {
        if (!(maid.level() instanceof ServerLevel level) || !isOutpostMaid(maid)) return;

        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        // 非玩家目标也是正式战斗，同样使用不重复的战斗台词池。
        boolean combat = data.getBoolean(PROVOKED) || !(target instanceof ServerPlayer);
        if (!combat) {
            if (!(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        }

        long now = level.getGameTime();
        if (now < data.getLong(NEXT_DIALOGUE_TIME)) return;

        String lastKey = combat ? LAST_COMBAT_LINE : LAST_ENCOUNTER_LINE;
        int previous = data.getInt(lastKey);
        // 作者彩蛋：台词对象是 i狐区的两位时，换成专属台词池
        boolean fox = target instanceof Player player && AuthorUtil.isLoveWineFoxTV(player);
        String pool = combat ? "combat." : "encounter.";
        int count = combat ? COMBAT_LINE_COUNT : ENCOUNTER_LINE_COUNT;
        if (fox) {
            pool = combat ? "combat.ifox." : "encounter.ifox.";
            count = combat ? COMBAT_IFOX_LINE_COUNT : ENCOUNTER_IFOX_LINE_COUNT;
        }
        int line = nonRepeatingIndex(level, previous, count);
        String key = "bubble.callresponse.outpost." + pool + (line + 1);
        maid.getChatBubbleManager().addTextChatBubble(key);

        data.putInt(lastKey, line);
        data.putLong(NEXT_DIALOGUE_TIME, now + DIALOGUE_INTERVAL_TICKS);
        maid.getPersistentData().put(ROOT, data);
    }

    private static int nonRepeatingIndex(ServerLevel level, int previous, int count) {
        if (count <= 1) return 0;
        if (previous < 0 || previous >= count) return level.getRandom().nextInt(count);
        int next = level.getRandom().nextInt(count - 1);
        return next >= previous ? next + 1 : next;
    }

    public enum Role implements net.minecraft.util.StringRepresentable {
        HEAVY,
        SWORDSMAN,
        FARMER,
        FEEDER,
        GLY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 彩蛋女仆：不参与战斗，只在玩家靠近时随机说话。 */
    public static void tickGlyDialogue(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !isOutpostMaid(maid) || !isGly(maid)) return;
        ServerPlayer nearby = level.getEntitiesOfClass(ServerPlayer.class, maid.getBoundingBox().inflate(32.0D))
                .stream()
                .filter(player -> !player.isSpectator() && player.distanceToSqr(maid) <= 32.0D * 32.0D)
                .findFirst().orElse(null);
        if (nearby == null) return;

        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        long now = level.getGameTime();
        if (now < data.getLong(GLY_NEXT_DIALOGUE_TIME)) return;

        List<String> pool = OutpostGlyDialogue.lines();
        if (pool.isEmpty()) return;
        int line = nonRepeatingIndex(level, data.getInt(GLY_LAST_LINE), pool.size());
        maid.getChatBubbleManager().addChatBubble(
                TextChatBubbleData.type2(net.minecraft.network.chat.Component.literal(pool.get(line))));

        data.putInt(GLY_LAST_LINE, line);
        data.putLong(GLY_NEXT_DIALOGUE_TIME, now + 10L * (10L + level.getRandom().nextInt(21)));
        maid.getPersistentData().put(ROOT, data);
    }

    public static boolean isGly(EntityMaid maid) {
        return role(maid) == Role.GLY;
    }

    /** 彩蛋女仆被攻击时的台词，独立于日常台词池。 */
    public static void tickGlyHurtDialogue(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !isOutpostMaid(maid) || !isGly(maid)) return;
        List<String> pool = OutpostGlyDialogue.hurtLines();
        if (pool.isEmpty()) return;

        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        long now = level.getGameTime();
        if (now < data.getLong(GLY_NEXT_HURT_TIME)) return;

        int line = nonRepeatingIndex(level, data.getInt(GLY_LAST_HURT_LINE), pool.size());
        maid.getChatBubbleManager().addChatBubble(
                TextChatBubbleData.type2(net.minecraft.network.chat.Component.literal(pool.get(line))));
        data.putInt(GLY_LAST_HURT_LINE, line);
        data.putLong(GLY_NEXT_HURT_TIME, now + (2L + level.getRandom().nextInt(4)));
        maid.getPersistentData().put(ROOT, data);
    }

    /**
     * 彩蛋女仆的行为：平时隔一会儿朝营地目标扔一颗雪球（原版雪球对玩家无伤害），
     * 被打之后就只顾着逃跑，跑掉或超时再恢复。
     */
    public static void tickGlyBehavior(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !isOutpostMaid(maid) || !isGly(maid)) return;
        long now = level.getGameTime();
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        if (now < data.getLong(GLY_FLEE_UNTIL)) {
            // 逃跑状态可能因为区块重载丢了移动接管，这里补回来
            if (!MaidMovementControl.isActive(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE)) {
                MaidMovementControl.begin(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE,
                        EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
            }
            tickGlyFlee(maid, now);
            return;
        }
        // 超时结束（正常跑掉走 endGlyFlee），必须释放移动接管，否则她的寻路会一直被占着
        if (MaidMovementControl.isActive(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE)) {
            endGlyFlee(maid);
        }
        tickGlySnowball(maid, level, now);
    }

    /** 被攻击：进入逃跑状态，接管移动并且不还手。 */
    public static void startGlyFlee(EntityMaid maid, Entity attacker) {
        if (!(maid.level() instanceof ServerLevel level) || !isOutpostMaid(maid) || !isGly(maid)) return;
        long now = level.getGameTime();
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        boolean alreadyFleeing = data.getLong(GLY_FLEE_UNTIL) > now;
        data.putLong(GLY_FLEE_UNTIL, now + GLY_FLEE_TICKS);
        Vec3 threat = attacker.position();
        data.putDouble(GLY_THREAT_X, threat.x);
        data.putDouble(GLY_THREAT_Y, threat.y);
        data.putDouble(GLY_THREAT_Z, threat.z);
        // 逃跑期间先不扔雪球，跑完再恢复
        data.putLong(GLY_NEXT_SNOWBALL_TIME, now + GLY_FLEE_TICKS + GLY_SNOWBALL_INTERVAL);
        maid.getPersistentData().put(ROOT, data);
        maid.setTarget(null);
        maid.setAggressive(false);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (!alreadyFleeing) {
            MaidMovementControl.begin(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE,
                    EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
            maid.setInSittingPose(false);
        }
    }

    private static void tickGlyFlee(EntityMaid maid, long now) {
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        double dx = data.getDouble(GLY_THREAT_X) - maid.getX();
        double dy = data.getDouble(GLY_THREAT_Y) - maid.getY();
        double dz = data.getDouble(GLY_THREAT_Z) - maid.getZ();
        if (dx * dx + dy * dy + dz * dz >= GLY_SAFE_DISTANCE * GLY_SAFE_DISTANCE) {
            endGlyFlee(maid);
            return;
        }
        // 逃跑中也不还手
        maid.setTarget(null);
        maid.setAggressive(false);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if ((now + maid.getId()) % 10L != 0L) return;

        BlockPos away = findGlyFleePos(maid,
                new Vec3(data.getDouble(GLY_THREAT_X), data.getDouble(GLY_THREAT_Y), data.getDouble(GLY_THREAT_Z)));
        if (away == null) return;
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(away), GLY_FLEE_SPEED, 1));
        maid.getNavigation().moveTo(away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, GLY_FLEE_SPEED);
    }

    /** 和本体躲苦力怕一样用 LandRandomPos：多试几次，挑一个已经拉开安全距离的方向。 */
    private static BlockPos findGlyFleePos(EntityMaid maid, Vec3 threat) {
        Vec3 best = null;
        double bestDistance = maid.position().distanceToSqr(threat);
        for (int i = 0; i < 8; i++) {
            Vec3 candidate = LandRandomPos.getPosAway(maid, 12, 7, threat);
            if (candidate == null) continue;
            double distance = candidate.distanceToSqr(threat);
            if (distance > bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
            if (distance >= GLY_SAFE_DISTANCE * GLY_SAFE_DISTANCE) break;
        }
        return best == null ? null : BlockPos.containing(best);
    }

    private static void endGlyFlee(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        data.putLong(GLY_FLEE_UNTIL, 0L);
        maid.getPersistentData().put(ROOT, data);
        if (MaidMovementControl.isActive(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE)) {
            MaidMovementControl.end(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE);
            if (!MaidMovementControl.controlsPath(maid)) {
                MaidMovementControl.clearNavigation(maid);
            }
        }
    }

    private static void tickGlySnowball(EntityMaid maid, ServerLevel level, long now) {
        CompoundTag data = maid.getPersistentData().getCompound(ROOT);
        // 掷出之后延迟补一次捡球动画（本体 MaidSnowballTargetTask 是同样的 25 tick）
        long pickupAt = data.getLong(GLY_PICKUP_ANIMATION_TIME);
        if (pickupAt > 0L && now >= pickupAt) {
            data.putLong(GLY_PICKUP_ANIMATION_TIME, 0L);
            maid.getPersistentData().put(ROOT, data);
            PacketDistributor.sendToPlayersTrackingEntity(maid, MaidAnimationPackage.pickUpSnowball(maid));
        }
        // 和本体一样：捡球/掷球动画期间站住不动，动画（约 1750ms）放完再解除
        if (maid.animationId == MaidAnimationPackage.PICK_UP_SNOWBALL) {
            if (System.currentTimeMillis() - maid.animationRecordTime > 1750L) {
                maid.animationId = MaidAnimationPackage.NONE;
                maid.animationRecordTime = -1L;
            } else {
                maid.getNavigation().stop();
            }
        }
        if (now < data.getLong(GLY_NEXT_SNOWBALL_TIME)) return;
        // 睡觉或在座椅上时先不闹
        if (maid.isSleeping() || maid.getVehicle() instanceof EntitySit) return;

        LivingEntity target = BetrayalOutpostAlertManager.findRelaxedTarget(maid);
        if (target == null || !maid.hasLineOfSight(target)
                || maid.distanceToSqr(target) > GLY_THROW_RANGE * GLY_THROW_RANGE) {
            data.putLong(GLY_NEXT_SNOWBALL_TIME, now + GLY_SNOWBALL_RETRY);
            maid.getPersistentData().put(ROOT, data);
            return;
        }

        // 完全套用本体 MaidSnowballTargetTask 的表现：挥动拿雪球的那只手、看向目标、掷出，
        // 再排一次捡球动画（上面的延迟逻辑）。客户端动画靠 MaidAnimationPackage 驱动。
        maid.swing(prepareGlySnowballHand(maid));
        BehaviorUtils.lookAtEntity(maid, target);
        performGlySnowballThrow(maid, target);

        data.putLong(GLY_NEXT_SNOWBALL_TIME,
                now + GLY_SNOWBALL_INTERVAL + level.getRandom().nextInt(GLY_SNOWBALL_INTERVAL));
        data.putLong(GLY_PICKUP_ANIMATION_TIME, now + GLY_PICKUP_ANIMATION_DELAY);
        maid.getPersistentData().put(ROOT, data);
    }

    /** 和本体一致：主手空就拿主手，否则塞到空着的副手；返回这次该挥哪只手。 */
    private static InteractionHand prepareGlySnowballHand(EntityMaid maid) {
        if (maid.getMainHandItem().isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, Items.SNOWBALL.getDefaultInstance());
            return InteractionHand.MAIN_HAND;
        }
        if (!(maid.getMainHandItem().getItem() instanceof SnowballItem) && maid.getOffhandItem().isEmpty()) {
            maid.setItemInHand(InteractionHand.OFF_HAND, Items.SNOWBALL.getDefaultInstance());
            return InteractionHand.OFF_HAND;
        }
        return maid.getMainHandItem().getItem() instanceof SnowballItem
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    /** 本体 MaidSnowballTargetTask#performRangedAttack 的同一套参数：不带 shooter，速度/角度/音量都照抄。 */
    private static void performGlySnowballThrow(EntityMaid shooter, LivingEntity target) {
        Snowball snowball = new Snowball(shooter.level(), shooter.getX(), shooter.getY(), shooter.getZ());
        double dx = target.getX() - shooter.getX();
        double dy = target.getBoundingBox().minY + target.getBbHeight() / 3.0F - snowball.position().y;
        double dz = target.getZ() - shooter.getZ();
        double pitch = Math.sqrt(dx * dx + dz * dz) * 0.15D;
        snowball.shoot(dx, dy + pitch, dz, 1.6F, 1);
        shooter.playSound(SoundEvents.SNOWBALL_THROW, 0.5F,
                0.4F / (shooter.getRandom().nextFloat() * 0.4F + 0.8F));
        shooter.level().addFreshEntity(snowball);
    }
}
