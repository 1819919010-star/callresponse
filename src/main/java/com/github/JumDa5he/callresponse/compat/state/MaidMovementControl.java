package com.github.JumDa5he.callresponse.compat.state;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.level.storage.ValueOutput;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一管理附属临时接管的 TLM 移动状态。
 * 运行态可以暂时改变姿态/任务，但所有保存输出都会被改写为第一次接管前的安全基线，
 * 因而移除附属后 TLM 第一次加载就不会读到临时状态。
 */
public final class MaidMovementControl {
    public static final String ROOT_KEY = "callresponse:movement_control";
    private static final int SCHEMA_VERSION = 1;
    private static final String BASELINE = "Baseline";
    private static final String REASONS = "Reasons";
    private static final Map<UUID, WeakReference<EntityMaid>> TRACKED = new HashMap<>();

    public enum Field {
        PATH(1), SCHEDULE(2), POSE(4), TASK(8), OWNER(16);

        private final int bit;

        Field(int bit) {
            this.bit = bit;
        }
    }

    public enum Reason {
        BEGGING,
        PANIC_FLEE,
        PANIC_HOLD,
        TALK,
        HUNT,
        SADDLE,
        BETRAYAL,
        BETRAYAL_VICTIM_FLEE,
        BROADCAST_WALK,
        BROADCAST_ATTACK,
        LAZY_POSE,
        DOTING_ACTION,
        DOTING_POSSESSIVE,
        DEVOTED_COMBAT,
        DEVOTED_HEAL,
        IDLE_HURT_FLEE,
        WANDERING_WAIT,
        PURCHASE_MOVING
    }

    public enum AbortCause {
        NORMAL,
        REPLACED,
        DEATH,
        REMOVED,
        SERVER_STOPPING,
        LOAD_RECOVERY,
        INVALID_STATE,
        REPAIR
    }

    private MaidMovementControl() {
    }

    public static void begin(EntityMaid maid, Reason reason, EnumSet<Field> fields) {
        if (!serverThread(maid) || fields.isEmpty()) {
            return;
        }
        CompoundTag root = getRoot(maid, true);
        CompoundTag reasons = root.getCompoundOrEmpty(REASONS);
        int oldMask = reasons.getIntOr(reason.name(), 0);
        int requestedMask = mask(fields);
        int newFields = requestedMask & ~activeMask(reasons);
        if (newFields != 0) {
            captureBaseline(maid, root.getCompoundOrEmpty(BASELINE), newFields);
        }
        reasons.putInt(reason.name(), oldMask | requestedMask);
        root.put(REASONS, reasons);
        root.putInt("Schema", SCHEMA_VERSION);
        TRACKED.put(maid.getUUID(), new WeakReference<>(maid));
    }

    public static void end(EntityMaid maid, Reason reason) {
        if (!serverThread(maid)) {
            return;
        }
        CompoundTag root = getRoot(maid, false);
        if (root == null) {
            return;
        }
        if (!hasValidBaseline(maid)) {
            CallResponseMod.LOGGER.warn("Skipped movement-control save sanitization for maid {}: invalid baseline",
                    maid.getUUID());
            return;
        }
        CompoundTag reasons = root.getCompoundOrEmpty(REASONS);
        if (!reasons.contains(reason.name())) {
            return;
        }
        int endedMask = reasons.getIntOr(reason.name(), 0);
        reasons.remove(reason.name());
        int stillOwned = activeMask(reasons);
        int restoreMask = endedMask & ~stillOwned;
        restoreFields(maid, root.getCompoundOrEmpty(BASELINE), restoreMask);
        clearBaselineFields(root.getCompoundOrEmpty(BASELINE), restoreMask);
        root.put(REASONS, reasons);
        cleanupRoot(maid, root);
    }

    public static void abortAll(EntityMaid maid, AbortCause cause) {
        if (!serverThread(maid)) {
            return;
        }
        CompoundTag root = getRoot(maid, false);
        if (root == null) {
            return;
        }
        int active = activeMask(root.getCompoundOrEmpty(REASONS));
        restoreFields(maid, root.getCompoundOrEmpty(BASELINE), active);
        clearNavigation(maid);
        maid.getPersistentData().remove(ROOT_KEY);
        TRACKED.remove(maid.getUUID());
        CallResponseMod.LOGGER.debug("Aborted maid movement control: maid={}, cause={}, fields={}",
                maid.getUUID(), cause, active);
    }

    public static boolean isActive(EntityMaid maid, Reason reason) {
        CompoundTag root = getRoot(maid, false);
        return root != null && root.getCompoundOrEmpty(REASONS).contains(reason.name());
    }

    public static boolean controlsPath(EntityMaid maid) {
        return controls(maid, Field.PATH);
    }

    public static boolean controlsSchedule(EntityMaid maid) {
        return controls(maid, Field.SCHEDULE);
    }

    public static boolean controlsPose(EntityMaid maid) {
        return controls(maid, Field.POSE);
    }

    /** 查询除指定原因外，是否还有别的流程占用某项临时状态。 */
    public static boolean controlsOther(EntityMaid maid, Reason excluded, Field field) {
        CompoundTag root = getRoot(maid, false);
        if (root == null) {
            return false;
        }
        CompoundTag reasons = root.getCompoundOrEmpty(REASONS);
        for (String key : reasons.keySet()) {
            if (!key.equals(excluded.name()) && reasons.contains(key)
                    && has(reasons.getIntOr(key, 0), field)) {
                return true;
            }
        }
        return false;
    }

    public static int activeMask(EntityMaid maid) {
        CompoundTag root = getRoot(maid, false);
        return root == null ? 0 : activeMask(root.getCompoundOrEmpty(REASONS));
    }

    public static String describeReasons(EntityMaid maid) {
        CompoundTag root = getRoot(maid, false);
        return root == null ? "[]" : root.getCompoundOrEmpty(REASONS).keySet().toString();
    }

    public static CompoundTag baselineCopy(EntityMaid maid) {
        CompoundTag root = getRoot(maid, false);
        return root == null ? new CompoundTag() : root.getCompoundOrEmpty(BASELINE).copy();
    }

    public static boolean hasValidBaseline(EntityMaid maid) {
        CompoundTag root = getRoot(maid, false);
        if (root == null || root.getIntOr("Schema", 0) != SCHEMA_VERSION
                || !root.contains(BASELINE)
                || !root.contains(REASONS)) {
            return false;
        }
        CompoundTag baseline = root.getCompoundOrEmpty(BASELINE);
        int fields = activeMask(root.getCompoundOrEmpty(REASONS));
        return (!has(fields, Field.SCHEDULE) || baseline.contains("Home")
                && baseline.contains("Schedule"))
                && (!has(fields, Field.POSE) || baseline.contains("Sitting"))
                && (!has(fields, Field.TASK) || baseline.contains("Task"))
                && (!has(fields, Field.OWNER) || baseline.contains("Tame"));
    }

    /** 丢弃无法证明来源的旧控制标签；不猜测并改写 owner/home/task。 */
    public static void discardInvalidData(EntityMaid maid) {
        if (!serverThread(maid)) {
            return;
        }
        clearNavigation(maid);
        maid.getPersistentData().remove(ROOT_KEY);
        TRACKED.remove(maid.getUUID());
    }

    public static void setDeadline(EntityMaid maid, Reason reason, long gameTime) {
        CompoundTag root = getRoot(maid, false);
        if (root != null && root.getCompoundOrEmpty(REASONS).contains(reason.name())) {
            root.putLong("Deadline_" + reason.name(), gameTime);
        }
    }

    public static long getDeadline(EntityMaid maid, Reason reason) {
        CompoundTag root = getRoot(maid, false);
        return root == null ? 0L : root.getLongOr("Deadline_" + reason.name(), 0L);
    }

    /** 在 EntityMaid.addAdditionalSaveData TAIL 调用，直接净化即将写出的 TLM NBT。 */
    public static void sanitizeSave(EntityMaid maid, CompoundTag outgoing) {
        if (maid.level().isClientSide()) {
            return;
        }
        CompoundTag root = getRoot(maid, false);
        if (root == null) {
            return;
        }
        CompoundTag baseline = root.getCompoundOrEmpty(BASELINE);
        int fields = activeMask(root.getCompoundOrEmpty(REASONS));
        if (has(fields, Field.SCHEDULE)) {
            outgoing.putBoolean("MaidIsHome", baseline.getBooleanOr("Home", false));
            if (baseline.contains("Schedule")) {
                outgoing.put("MaidSchedulePos", baseline.getCompoundOrEmpty("Schedule").copy());
            } else {
                outgoing.putBoolean("MaidIsHome", false);
                CallResponseMod.LOGGER.warn("Missing schedule baseline while saving maid {}", maid.getUUID());
            }
        }
        if (has(fields, Field.POSE)) {
            outgoing.putBoolean("Sitting", baseline.getBooleanOr("Sitting", false));
        }
        if (has(fields, Field.TASK)) {
            String task = baseline.getStringOr("Task", "");
            outgoing.putString("MaidTask", Identifier.tryParse(task) == null
                    ? TaskManager.getIdleTask().getUid().toString() : task);
        }
        if (has(fields, Field.OWNER)) {
            UUID owner = baseline.read("Owner", UUIDUtil.CODEC).orElse(null);
            if (baseline.getBooleanOr("Tame", false) && owner != null) {
                outgoing.store("Owner", UUIDUtil.CODEC, owner);
            } else {
                outgoing.remove("Owner");
            }
        }
        outgoing.put("ForgeData", maid.getPersistentData().copy());
    }

    /** 26.1 的实体保存入口使用 ValueOutput；语义与旧 CompoundTag 入口完全一致。 */
    public static void sanitizeSave(EntityMaid maid, ValueOutput outgoing) {
        if (maid.level().isClientSide()) {
            return;
        }
        CompoundTag root = getRoot(maid, false);
        if (root == null) {
            return;
        }
        CompoundTag baseline = root.getCompoundOrEmpty(BASELINE);
        int fields = activeMask(root.getCompoundOrEmpty(REASONS));
        if (has(fields, Field.SCHEDULE)) {
            outgoing.putBoolean("MaidIsHome", baseline.getBooleanOr("Home", false));
            if (baseline.contains("Schedule")) {
                outgoing.store("MaidSchedulePos", CompoundTag.CODEC,
                        baseline.getCompoundOrEmpty("Schedule").copy());
            } else {
                outgoing.putBoolean("MaidIsHome", false);
                CallResponseMod.LOGGER.warn("Missing schedule baseline while saving maid {}", maid.getUUID());
            }
        }
        if (has(fields, Field.POSE)) {
            outgoing.putBoolean("Sitting", baseline.getBooleanOr("Sitting", false));
        }
        if (has(fields, Field.TASK)) {
            String task = baseline.getStringOr("Task", "");
            outgoing.putString("MaidTask", Identifier.tryParse(task) == null
                    ? TaskManager.getIdleTask().getUid().toString() : task);
        }
        if (has(fields, Field.OWNER)) {
            UUID owner = baseline.read("Owner", UUIDUtil.CODEC).orElse(null);
            if (baseline.getBooleanOr("Tame", false) && owner != null) {
                outgoing.store("Owner", UUIDUtil.CODEC, owner);
            } else {
                outgoing.discard("Owner");
            }
        }
        outgoing.store("ForgeData", CompoundTag.CODEC, maid.getPersistentData().copy());
    }

    /** 在 EntityMaid.readAdditionalSaveData TAIL 调用。普通临时流程一律中止；可恢复状态按严格规则处理。 */
    public static void recoverOnLoad(EntityMaid maid) {
        if (!serverThread(maid)) {
            return;
        }
        CompoundTag root = getRoot(maid, false);
        if (root == null) {
            return;
        }
        if (!hasValidBaseline(maid)) {
            CallResponseMod.LOGGER.warn("Invalid movement-control data on maid {}; applying safe recovery", maid.getUUID());
            discardInvalidData(maid);
            return;
        }

        CompoundTag reasons = root.getCompoundOrEmpty(REASONS);
        boolean keepPanicHold = reasons.contains(Reason.PANIC_HOLD.name())
                && getDeadline(maid, Reason.PANIC_HOLD) > maid.level().getGameTime();
        boolean keepWandering = reasons.contains(Reason.WANDERING_WAIT.name())
                && WanderingMaidData.isSpecial(maid);
        boolean keepPurchase = reasons.contains(Reason.PURCHASE_MOVING.name())
                && maid.getPersistentData().read("callresponse:trading_maid_purchase_moving", UUIDUtil.CODEC).isPresent()
                && getDeadline(maid, Reason.PURCHASE_MOVING) > maid.level().getGameTime()
                && maid.isTame()
                && (maid.getOwner() == null ? null : maid.getOwner().getUUID()) != null
                && (maid.getOwner() == null ? null : maid.getOwner().getUUID()).equals(maid.getPersistentData()
                .read("callresponse:trading_maid_purchase_moving", UUIDUtil.CODEC).orElse(null));
        boolean keepBetrayal = reasons.contains(Reason.BETRAYAL.name())
                && maid.getPersistentData().getBooleanOr("IsBetraying", false)
                && root.getCompoundOrEmpty(BASELINE).getBooleanOr("Tame", false)
                && root.getCompoundOrEmpty(BASELINE).read("Owner", UUIDUtil.CODEC).isPresent();

        for (String key : reasons.keySet().toArray(String[]::new)) {
            if ((key.equals(Reason.PANIC_HOLD.name()) && keepPanicHold)
                    || (key.equals(Reason.WANDERING_WAIT.name()) && keepWandering)
                    || (key.equals(Reason.PURCHASE_MOVING.name()) && keepPurchase)
                    || (key.equals(Reason.BETRAYAL.name()) && keepBetrayal)) {
                continue;
            }
            try {
                end(maid, Reason.valueOf(key));
            } catch (IllegalArgumentException ignored) {
                reasons.remove(key);
            }
        }

        if (!keepPurchase) {
            maid.getPersistentData().remove("callresponse:trading_maid_purchase_moving");
        }

        if (keepBetrayal) {
            maid.setTame(true, false);
            maid.setOwnerReference(null);
            TaskManager.findTask(Identifier.fromNamespaceAndPath("touhou_little_maid", "attack")).ifPresent(maid::setTask);
            maid.setAggressive(true);
        } else if (maid.getPersistentData().getBooleanOr("IsBetraying", false)) {
            maid.getPersistentData().remove("IsBetraying");
            CallResponseMod.LOGGER.warn("Aborted incomplete betrayal state on maid {} without guessing an owner", maid.getUUID());
        }
        cleanupRoot(maid, root);
        if (getRoot(maid, false) != null) {
            TRACKED.put(maid.getUUID(), new WeakReference<>(maid));
        }
    }

    public static void abortTrackedOnServerStop() {
        for (WeakReference<EntityMaid> reference : new ArrayList<>(TRACKED.values())) {
            EntityMaid maid = reference.get();
            if (maid != null && !maid.level().isClientSide()) {
                CompoundTag root = getRoot(maid, false);
                if (root == null) {
                    continue;
                }
                CompoundTag reasons = root.getCompoundOrEmpty(REASONS);
                for (String key : reasons.keySet().toArray(String[]::new)) {
                    boolean survivesRestart = key.equals(Reason.BETRAYAL.name())
                            || key.equals(Reason.PANIC_HOLD.name())
                            || key.equals(Reason.WANDERING_WAIT.name())
                            || key.equals(Reason.PURCHASE_MOVING.name());
                    if (!survivesRestart) {
                        try {
                            end(maid, Reason.valueOf(key));
                        } catch (IllegalArgumentException ignored) {
                            reasons.remove(key);
                        }
                    }
                }
            }
        }
        TRACKED.clear();
    }

    public static void clearNavigation(EntityMaid maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    private static boolean controls(EntityMaid maid, Field field) {
        return has(activeMask(maid), field);
    }

    private static int mask(EnumSet<Field> fields) {
        int value = 0;
        for (Field field : fields) {
            value |= field.bit;
        }
        return value;
    }

    private static int activeMask(CompoundTag reasons) {
        int value = 0;
        for (String key : reasons.keySet()) {
            if (reasons.contains(key)) {
                value |= reasons.getIntOr(key, 0);
            }
        }
        return value;
    }

    private static boolean has(int mask, Field field) {
        return (mask & field.bit) != 0;
    }

    private static void captureBaseline(EntityMaid maid, CompoundTag baseline, int fields) {
        if (has(fields, Field.SCHEDULE)) {
            baseline.putBoolean("Home", maid.isHomeModeEnable());
            SchedulePos schedule = maid.getSchedulePos();
            CompoundTag data = new CompoundTag();
            data.store("Work", BlockPos.CODEC, schedule.getWorkPos());
            data.store("Idle", BlockPos.CODEC, schedule.getIdlePos());
            data.store("Sleep", BlockPos.CODEC, schedule.getSleepPos());
            data.putString("Dimension", schedule.getDimension().toString());
            data.putBoolean("Configured", schedule.isConfigured());
            baseline.put("Schedule", data);
            baseline.store("RestrictCenter", BlockPos.CODEC, maid.getHomePosition());
            baseline.putFloat("RestrictRadius", maid.getHomeRadius());
        }
        if (has(fields, Field.POSE)) {
            baseline.putBoolean("Sitting", maid.isInSittingPose());
        }
        if (has(fields, Field.TASK)) {
            baseline.putString("Task", maid.getTask().getUid().toString());
        }
        if (has(fields, Field.OWNER)) {
            baseline.putBoolean("Tame", maid.isTame());
            UUID owner = (maid.getOwner() == null ? null : maid.getOwner().getUUID());
            if (owner != null) {
                baseline.store("Owner", UUIDUtil.CODEC, owner);
            }
        }
    }

    private static void restoreFields(EntityMaid maid, CompoundTag baseline, int fields) {
        if (has(fields, Field.SCHEDULE)) {
            boolean home = baseline.getBooleanOr("Home", false);
            if (baseline.contains("Schedule")) {
                CompoundTag data = baseline.getCompoundOrEmpty("Schedule");
                SchedulePos schedule = maid.getSchedulePos();
                schedule.setWorkPos(readPos(data, "Work", maid.blockPosition()));
                schedule.setIdlePos(readPos(data, "Idle", maid.blockPosition()));
                schedule.setSleepPos(readPos(data, "Sleep", maid.blockPosition()));
                Identifier dimension = Identifier.tryParse(data.getStringOr("Dimension", ""));
                schedule.setDimension(dimension == null ? maid.level().dimension().identifier() : dimension);
                schedule.setConfigured(data.getBooleanOr("Configured", false));
            }
            maid.setHomeModeEnable(home);
            if (home) {
                maid.getSchedulePos().restrictTo(maid);
            } else {
                maid.setHomeTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
            }
        }
        if (has(fields, Field.POSE)) {
            maid.setInSittingPose(baseline.getBooleanOr("Sitting", false));
        }
        if (has(fields, Field.TASK)) {
            Identifier id = Identifier.tryParse(baseline.getStringOr("Task", ""));
            maid.setTask(id == null ? TaskManager.getIdleTask()
                    : TaskManager.findTask(id).orElse(TaskManager.getIdleTask()));
        }
        if (has(fields, Field.OWNER)) {
            boolean tame = baseline.getBooleanOr("Tame", false);
            maid.setTame(tame, false);
            UUID owner = tame ? baseline.read("Owner", UUIDUtil.CODEC).orElse(null) : null;
            maid.setOwnerReference(owner == null ? null : EntityReference.of(owner));
        }
    }

    private static BlockPos readPos(CompoundTag data, String key, BlockPos fallback) {
        return data.read(key, BlockPos.CODEC).orElse(fallback);
    }

    private static void clearBaselineFields(CompoundTag baseline, int fields) {
        if (has(fields, Field.SCHEDULE)) {
            baseline.remove("Home");
            baseline.remove("Schedule");
            baseline.remove("RestrictCenter");
            baseline.remove("RestrictRadius");
        }
        if (has(fields, Field.POSE)) {
            baseline.remove("Sitting");
        }
        if (has(fields, Field.TASK)) {
            baseline.remove("Task");
        }
        if (has(fields, Field.OWNER)) {
            baseline.remove("Tame");
            baseline.remove("Owner");
        }
    }

    private static void cleanupRoot(EntityMaid maid, CompoundTag root) {
        CompoundTag reasons = root.getCompoundOrEmpty(REASONS);
        for (Reason reason : Reason.values()) {
            if (!reasons.contains(reason.name())) {
                root.remove("Deadline_" + reason.name());
            }
        }
        if (reasons.isEmpty()) {
            maid.getPersistentData().remove(ROOT_KEY);
            TRACKED.remove(maid.getUUID());
        }
    }

    private static CompoundTag getRoot(EntityMaid maid, boolean create) {
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(ROOT_KEY)) {
            if (!create) {
                return null;
            }
            CompoundTag root = new CompoundTag();
            root.putInt("Schema", SCHEMA_VERSION);
            root.put(BASELINE, new CompoundTag());
            root.put(REASONS, new CompoundTag());
            data.put(ROOT_KEY, root);
        }
        return data.getCompoundOrEmpty(ROOT_KEY);
    }

    private static boolean serverThread(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return false;
        }
        if (maid.level().getServer() != null && !maid.level().getServer().isSameThread()) {
            CallResponseMod.LOGGER.error("Rejected off-thread maid movement mutation for {}", maid.getUUID());
            return false;
        }
        return true;
    }
}
