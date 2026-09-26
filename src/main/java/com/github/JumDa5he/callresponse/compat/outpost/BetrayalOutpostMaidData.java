package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

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
    private static final String LEGACY_EXTRA_HEALTH = "ExtraHealth";
    private static final long DIALOGUE_INTERVAL_TICKS = 20L * 15L;
    private static final int ENCOUNTER_LINE_COUNT = 8;
    private static final int COMBAT_LINE_COUNT = 10;

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
        int count = combat ? COMBAT_LINE_COUNT : ENCOUNTER_LINE_COUNT;
        int line = nonRepeatingIndex(level, data.getInt(lastKey), count);
        String key = "bubble.callresponse.outpost." + (combat ? "combat." : "encounter.") + (line + 1);
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
}
