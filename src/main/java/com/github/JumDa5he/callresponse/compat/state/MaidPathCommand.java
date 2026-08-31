package com.github.JumDa5he.callresponse.compat.state;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** /callresponse path：旧档诊断、保存净化验证和显式修复。 */
public final class MaidPathCommand {
    private MaidPathCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("path")
                .then(Commands.literal("inspect")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> inspect(ctx, false))))
                .then(Commands.literal("validate")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> inspect(ctx, true))))
                .then(Commands.literal("repair")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(MaidPathCommand::repair)))
                .then(Commands.literal("repair_force").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> force(ctx, false))
                                .then(Commands.argument("idle", BoolArgumentType.bool())
                                        .executes(ctx -> force(ctx, BoolArgumentType.getBool(ctx, "idle"))))));
    }

    private static int inspect(CommandContext<CommandSourceStack> context, boolean validate)
            throws CommandSyntaxException {
        List<EntityMaid> maids = targets(context);
        requireOwnerOrPermission(context.getSource(), maids, false);
        for (EntityMaid maid : maids) {
            sendReport(context.getSource(), maid);
            if (validate) {
                validateSave(context.getSource(), maid);
            }
        }
        return maids.size();
    }

    private static int repair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<EntityMaid> maids = targets(context);
        requireOwnerOrPermission(context.getSource(), maids, false);
        for (EntityMaid maid : maids) {
            boolean hadEvidence = maid.getPersistentData().contains(MaidMovementControl.ROOT_KEY)
                    || maid.getPersistentData().contains("CallResponseSyncData");
            if (MaidMovementControl.activeMask(maid) != 0) {
                if (MaidMovementControl.hasValidBaseline(maid)) {
                    MaidMovementControl.abortAll(maid, MaidMovementControl.AbortCause.REPAIR);
                } else {
                    MaidMovementControl.discardInvalidData(maid);
                }
            }
            MaidMovementControl.clearNavigation(maid);
            maid.getPersistentData().remove("callresponse:trading_maid_purchase_moving");
            MaidPathRepair.Result result = MaidPathRepair.cleanupKnownSpeedPollution(maid, hadEvidence, true);
            context.getSource().sendSuccess(() -> Component.literal("[path repair] " + maid.getName().getString()
                    + "：已清导航，移除速度修饰符 " + result.removedModifiers()
                    + " 个，Base " + result.oldBase() + " -> " + result.newBase()), false);
        }
        return maids.size();
    }

    private static int force(CommandContext<CommandSourceStack> context, boolean idle) throws CommandSyntaxException {
        List<EntityMaid> maids = targets(context);
        for (EntityMaid maid : maids) {
            context.getSource().sendSuccess(() -> Component.literal("[path force before] " + summary(maid)), false);
            MaidMovementControl.discardInvalidData(maid);
            MaidMovementControl.clearNavigation(maid);
            maid.getPersistentData().remove("callresponse:trading_maid_purchase_moving");
            maid.setInSittingPose(false);
            maid.setHomeModeEnable(false);
            maid.setHomeTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
            if (idle) {
                maid.setTask(TaskManager.getIdleTask());
            }
            MaidPathRepair.cleanupKnownSpeedPollution(maid, true, true);
            context.getSource().sendSuccess(() -> Component.literal("[path force after] " + summary(maid)), false);
        }
        return maids.size();
    }

    private static List<EntityMaid> targets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "targets");
        List<EntityMaid> maids = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity instanceof EntityMaid maid) {
                maids.add(maid);
            }
        }
        if (maids.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }
        return maids;
    }

    private static void requireOwnerOrPermission(CommandSourceStack source, List<EntityMaid> maids, boolean force)
            throws CommandSyntaxException {
        if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        if (force || maids.size() != 1 || !(source.getEntity() instanceof LivingEntity living)
                || !maids.get(0).isOwnedBy(living)) {
            throw net.minecraft.commands.CommandSourceStack.ERROR_NOT_PLAYER.create();
        }
    }

    private static void sendReport(CommandSourceStack source, EntityMaid maid) {
        source.sendSuccess(() -> Component.literal("[path inspect] " + summary(maid)), false);
        SchedulePos schedule = maid.getSchedulePos();
        source.sendSuccess(() -> Component.literal("schedule={work=" + schedule.getWorkPos() + ", idle="
                + schedule.getIdlePos() + ", sleep=" + schedule.getSleepPos() + ", dimension="
                + schedule.getDimension() + ", configured=" + schedule.isConfigured() + "}, restriction={center="
                + maid.getHomePosition() + ", radius=" + maid.getHomeRadius() + "}"), false);
        source.sendSuccess(() -> Component.literal("brain={PATH=" + memory(maid, MemoryModuleType.PATH)
                + ", WALK=" + memory(maid, MemoryModuleType.WALK_TARGET) + ", LOOK="
                + memory(maid, MemoryModuleType.LOOK_TARGET) + ", ATTACK="
                + memory(maid, MemoryModuleType.ATTACK_TARGET) + "}, navigation={done="
                + maid.getNavigation().isDone() + ", path=" + maid.getNavigation().getPath() + "}"), false);
        AttributeInstance speed = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        source.sendSuccess(() -> Component.literal("speed=" + describeSpeed(speed) + ", reasons="
                + MaidMovementControl.describeReasons(maid) + ", baseline="
                + MaidMovementControl.baselineCopy(maid) + ", legacyPurchase="
                + maid.getPersistentData().get("callresponse:trading_maid_purchase_moving")), false);
    }

    private static String summary(EntityMaid maid) {
        return maid.getName().getString() + " uuid=" + maid.getUUID() + " owner=" + (maid.getOwner() == null ? null : maid.getOwner().getUUID())
                + " tame=" + maid.isTame() + " home=" + maid.isHomeModeEnable() + " sitting="
                + maid.isInSittingPose() + " task=" + maid.getTask().getUid();
    }

    private static String memory(EntityMaid maid, MemoryModuleType<?> type) {
        return maid.getBrain().getMemory(type).map(Object::toString).orElse("empty");
    }

    private static String describeSpeed(AttributeInstance speed) {
        if (speed == null) {
            return "missing";
        }
        StringBuilder text = new StringBuilder("base=").append(speed.getBaseValue()).append(", modifiers=[");
        for (AttributeModifier modifier : speed.getModifiers()) {
            text.append('{').append(modifier.id()).append(',').append(modifier.amount())
                    .append(',').append(modifier.operation()).append("},");
        }
        return text.append(']').toString();
    }

    private static void validateSave(CommandSourceStack source, EntityMaid maid) {
        CompoundTag baseline = MaidMovementControl.baselineCopy(maid);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, maid.registryAccess());
        maid.saveWithoutId(output);
        CompoundTag outgoing = output.buildResult();
        List<String> failures = new ArrayList<>();
        if (baseline.contains("Home") && outgoing.getBooleanOr("MaidIsHome", false) != baseline.getBooleanOr("Home", false)) {
            failures.add("home");
        }
        if (baseline.contains("Schedule")
                && !outgoing.getCompoundOrEmpty("MaidSchedulePos").equals(baseline.getCompoundOrEmpty("Schedule"))) {
            failures.add("schedule");
        }
        if (baseline.contains("Sitting") && outgoing.getBooleanOr("Sitting", false) != baseline.getBooleanOr("Sitting", false)) {
            failures.add("sitting");
        }
        if (baseline.contains("Task") && !outgoing.getStringOr("MaidTask", "").equals(baseline.getStringOr("Task", ""))) {
            failures.add("task");
        }
        if (baseline.contains("Tame")) {
            var baselineOwner = baseline.read("Owner", UUIDUtil.CODEC);
            var outgoingOwner = outgoing.read("Owner", UUIDUtil.CODEC);
            boolean expectedOwner = baseline.getBooleanOr("Tame", false) && baselineOwner.isPresent();
            if (outgoingOwner.isPresent() != expectedOwner
                    || expectedOwner && !outgoingOwner.equals(baselineOwner)) {
                failures.add("owner");
            }
        }
        source.sendSuccess(() -> Component.literal("[path validate] " + maid.getName().getString() + "："
                + (failures.isEmpty() ? "PASS，outgoing TLM 字段等于 baseline" : "FAIL " + failures)), false);
    }
}
