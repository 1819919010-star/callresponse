package com.github.JumDa5he.callresponse.compat.brain;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.cage.CageOrigin;
import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlock;
import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlockEntity;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntDamageContext;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 嫉妒关笼流程、跨女仆目标/笼子预约，以及局部保护破除的运行态。 */
public final class JealousyCageManager {
    private static final int SEARCH_RADIUS = 16;
    private static final long COOLDOWN_TICKS = 15L * 60L * 20L;
    private static final long APPROACH_TIMEOUT = 30L * 20L;
    private static final long TRANSPORT_TIMEOUT = 30L * 20L;
    private static final long PUNISH_DURATION = 30L * 20L;
    private static final long PUNISH_ATTACK_INTERVAL = 2L * 20L;
    private static final float WALK_SPEED = 0.65F;
    private static final double PICKUP_DISTANCE_SQR = 2.25D;
    private static final double CAGE_CAPTURE_DISTANCE_SQR = 3.25D * 3.25D;
    private static final double ATTACK_DISTANCE_SQR = 3.5D * 3.5D;
    private static final String COOLDOWN_UNTIL = "CallResponseJealousyCageCooldownUntil";

    private static final int PICKUP_LINES = 4;
    private static final int RELEASE_LINES = 4;
    private static final int PRISONER_LINES = 4;
    private static final int TAUNT_LINES = 4;
    private static final int BLAME_LINES = 8;

    private static final Map<UUID, Flow> FLOWS = new HashMap<>();
    private static final Map<UUID, UUID> TARGET_CLAIMS = new HashMap<>();
    private static final Map<CageKey, UUID> CAGE_CLAIMS = new HashMap<>();
    public JealousyCageManager() {
    }

    private enum Stage { APPROACH_TARGET, TRANSPORT_TO_CAGE, PUNISH }

    private enum StartFailure {
        NONE,
        ALREADY_RUNNING_OR_CLAIMED,
        OWNER_UNAVAILABLE,
        NOT_DOTING,
        ACTOR_UNAVAILABLE,
        COOLDOWN,
        NO_EMPTY_CAGE,
        NO_VALID_TARGET
    }

    private record StartResult(boolean started, StartFailure failure) {
        private static StartResult success() {
            return new StartResult(true, StartFailure.NONE);
        }

        private static StartResult failure(StartFailure failure) {
            return new StartResult(false, failure);
        }
    }

    private static final class Flow {
        private final EntityMaid actor;
        private final ResourceKey<Level> dimension;
        private final UUID targetId;
        private final BlockPos cagePos;
        private final long startTime;
        private Stage stage = Stage.APPROACH_TARGET;
        private long pickupTime;
        private long punishEndTime;
        private long nextAttackTime;
        private boolean pickedUp;
        private WalkTarget issuedWalkTarget;
        private PositionTracker issuedLookTarget;

        private Flow(EntityMaid actor, UUID targetId, BlockPos cagePos, long startTime) {
            this.actor = actor;
            this.dimension = actor.level().dimension();
            this.targetId = targetId;
            this.cagePos = cagePos.immutable();
            this.startTime = startTime;
        }
    }

    private record CageKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public static boolean isRunning(EntityMaid maid) {
        return maid != null && FLOWS.containsKey(maid.getUUID());
    }

    /** 仅免除本流程执行者作为目击者时的恐惧 +4。 */
    public static boolean isCageWitnessFearExempt(EntityMaid maid) {
        return isRunning(maid);
    }

    public static boolean tryStart(ServerLevel level, EntityMaid maid) {
        return start(level, maid, false).started();
    }

    /** 自然触发与指令触发共用唯一入口；forced 只跳过触发时刻和 CD。 */
    private static StartResult start(ServerLevel level, EntityMaid maid, boolean forced) {
        if (maid == null || FLOWS.containsKey(maid.getUUID())
                || TARGET_CLAIMS.containsKey(maid.getUUID())) {
            return StartResult.failure(StartFailure.ALREADY_RUNNING_OR_CLAIMED);
        }
        long now = level.getGameTime();
        if (!forced && now % 20L != 0L) return StartResult.failure(StartFailure.COOLDOWN);
        if (!(maid.getOwner() instanceof ServerPlayer owner)) {
            return StartResult.failure(StartFailure.OWNER_UNAVAILABLE);
        }
        if (!EmotionDotingManager.isDoting(maid, owner)) {
            return StartResult.failure(StartFailure.NOT_DOTING);
        }
        if (!maid.isAlive() || maid.isRemoved() || maid.isPassenger()
                || !maid.getPassengers().isEmpty()
                || MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE)) {
            return StartResult.failure(StartFailure.ACTOR_UNAVAILABLE);
        }
        long cooldownUntil = maid.getPersistentData().getLong(COOLDOWN_UNTIL);
        if (!forced && now < cooldownUntil && now >= cooldownUntil - COOLDOWN_TICKS) {
            return StartResult.failure(StartFailure.COOLDOWN);
        }

        List<BlockPos> cages = findAvailableCages(level, maid);
        if (cages.isEmpty()) return StartResult.failure(StartFailure.NO_EMPTY_CAGE);
        List<EntityMaid> targets = findAvailableTargets(level, maid, owner, now, forced);
        if (targets.isEmpty()) return StartResult.failure(StartFailure.NO_VALID_TARGET);

        EntityMaid target = targets.get(maid.getRandom().nextInt(targets.size()));
        BlockPos cagePos = cages.get(maid.getRandom().nextInt(cages.size()));
        CageKey cageKey = new CageKey(level.dimension(), cagePos);
        TARGET_CLAIMS.put(target.getUUID(), maid.getUUID());
        CAGE_CLAIMS.put(cageKey, maid.getUUID());
        FLOWS.put(maid.getUUID(), new Flow(maid, target.getUUID(), cagePos, now));
        // 只临时占用寻路权，阻止 TLM 跟随主人覆盖运输路线；不修改工作、主人、Home 或 Schedule。
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.JEALOUSY_CAGE,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH));
        maid.getPersistentData().putLong(COOLDOWN_UNTIL, now + COOLDOWN_TICKS);
        maid.setInSittingPose(false);
        return StartResult.success();
    }

    public static void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        Flow flow = FLOWS.get(maid.getUUID());
        if (flow == null) return;
        if (!flow.dimension.equals(level.dimension()) || !maid.isAlive() || maid.isRemoved()) {
            finish(flow, true, false);
            return;
        }
        if (MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE)) {
            finish(flow, true, true);
            return;
        }
        if (maid.isPassenger()) {
            finish(flow, true, false);
            return;
        }

        // 较早流程已经把本执行者锁为目标时，较晚流程让路；较早者仍可按原目标完成抱起。
        UUID priorActor = TARGET_CLAIMS.get(maid.getUUID());
        if (priorActor != null && !priorActor.equals(maid.getUUID()) && FLOWS.containsKey(priorActor)) {
            clearIssuedMovement(flow);
            return;
        }

        Entity entity = level.getEntity(flow.targetId);
        if (!(entity instanceof EntityMaid target) || !target.isAlive() || target.isRemoved()) {
            finish(flow, true, false);
            return;
        }
        DarkIronCageBlockEntity cage = cage(level, flow);

        switch (flow.stage) {
            case APPROACH_TARGET -> tickApproach(level, flow, target, cage, gameTime);
            case TRANSPORT_TO_CAGE -> tickTransport(level, flow, target, cage, gameTime);
            case PUNISH -> tickPunish(level, flow, target, cage, gameTime);
        }
    }

    private static void tickApproach(ServerLevel level, Flow flow, EntityMaid target,
                                     DarkIronCageBlockEntity cage, long now) {
        if (cage == null || cage.isOccupied() || now - flow.startTime >= APPROACH_TIMEOUT
                || !PrincessCarryManager.canDirectMaidPickup(flow.actor, target)) {
            finish(flow, false, false);
            return;
        }
        if (flow.actor.distanceToSqr(target) > PICKUP_DISTANCE_SQR) {
            issueEntityWalk(flow, target, 1);
            return;
        }
        if (!PrincessCarryManager.tryDirectMaidPickup(flow.actor, target)) {
            finish(flow, false, false);
            return;
        }
        flow.pickedUp = true;
        flow.pickupTime = now;
        flow.stage = Stage.TRANSPORT_TO_CAGE;
        say(flow.actor, "bubble.callresponse.jealousy.pickup.", PICKUP_LINES);
        issueCageWalk(level, flow);
    }

    private static void tickTransport(ServerLevel level, Flow flow, EntityMaid target,
                                      DarkIronCageBlockEntity cage, long now) {
        if (cage == null || cage.isOccupied() || target.getVehicle() != flow.actor
                || now - flow.pickupTime >= TRANSPORT_TIMEOUT) {
            finish(flow, true, false);
            return;
        }
        if (flow.actor.distanceToSqr(Vec3.atCenterOf(flow.cagePos)) > CAGE_CAPTURE_DISTANCE_SQR) {
            issueCageWalk(level, flow);
            return;
        }
        if (!cage.capture(target, null, CageOrigin.NORMAL) || cage.occupant() != target) {
            finish(flow, true, false);
            return;
        }
        PrincessCarryManager.completeExternalTransfer(flow.actor, target);
        level.playSound(null, flow.cagePos, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        flow.stage = Stage.PUNISH;
        flow.punishEndTime = now + PUNISH_DURATION;
        flow.nextAttackTime = now + PUNISH_ATTACK_INTERVAL;
        say(target, "bubble.callresponse.jealousy.prisoner.", PRISONER_LINES);
        say(flow.actor, "bubble.callresponse.jealousy.taunt.", TAUNT_LINES);
        issueCageWalk(level, flow);
    }

    private static void tickPunish(ServerLevel level, Flow flow, EntityMaid target,
                                   DarkIronCageBlockEntity cage, long now) {
        if (now >= flow.punishEndTime || cage == null || cage.occupant() != target) {
            finish(flow, false, false);
            return;
        }
        flow.actor.setTarget(target);
        double distance = flow.actor.distanceToSqr(target);
        // 目标已由本流程锁定且仍是该笼囚犯。铁笼栏杆会挡住 vanilla 实体视线射线，
        // 因此这里只按近战距离判定；保护破除上下文仍只包住本次目标的 1 点伤害。
        if (distance > ATTACK_DISTANCE_SQR) {
            issueCageWalk(level, flow);
            return;
        }
        issueLook(flow, target);
        if (now < flow.nextAttackTime) return;
        flow.nextAttackTime = now + PUNISH_ATTACK_INTERVAL;
        dealOneDamage(flow.actor, target);
    }

    private static void dealOneDamage(EntityMaid attacker, EntityMaid target) {
        DamageSource source = target.damageSources().mobAttack(attacker);
        target.invulnerableTime = 0;
        HuntDamageContext.begin(target.getUUID(), 1.0F, source);
        try {
            attacker.swing(InteractionHand.MAIN_HAND);
            target.hurt(source, 1.0F);
        } finally {
            HuntDamageContext.end();
            HuntDamageContext.clearCaptured(target.getUUID());
        }
    }

    private static void issueEntityWalk(Flow flow, EntityMaid target, int closeEnough) {
        EntityTracker tracker = new EntityTracker(target, true);
        issueWalk(flow, tracker, closeEnough);
    }

    private static void issueCageWalk(ServerLevel level, Flow flow) {
        Direction facing = level.getBlockState(flow.cagePos).getValue(DarkIronCageBlock.FACING);
        BlockPos outside = flow.cagePos.relative(facing, 2);
        issueWalk(flow, new BlockPosTracker(outside), 1);
    }

    private static void issueWalk(Flow flow, PositionTracker tracker, int closeEnough) {
        WalkTarget walkTarget = new WalkTarget(tracker, WALK_SPEED, closeEnough);
        flow.issuedWalkTarget = walkTarget;
        flow.issuedLookTarget = tracker;
        flow.actor.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
        flow.actor.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
    }

    private static void issueLook(Flow flow, EntityMaid target) {
        EntityTracker tracker = new EntityTracker(target, true);
        flow.issuedLookTarget = tracker;
        flow.actor.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
    }

    private static List<EntityMaid> findAvailableTargets(ServerLevel level, EntityMaid actor,
                                                         ServerPlayer owner, long now, boolean forcedActor) {
        AABB area = actor.getBoundingBox().inflate(SEARCH_RADIUS);
        List<EntityMaid> result = new ArrayList<>();
        for (EntityMaid candidate : level.getEntitiesOfClass(EntityMaid.class, area)) {
            if (candidate == actor || !candidate.isOwnedBy(owner)
                    || TARGET_CLAIMS.containsKey(candidate.getUUID())
                    || FLOWS.containsKey(candidate.getUUID())
                    || (!forcedActor && isSimultaneousStarter(candidate, owner, now))
                    || !PrincessCarryManager.canDirectMaidPickup(actor, candidate)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    private static boolean isSimultaneousStarter(EntityMaid candidate, ServerPlayer owner, long now) {
        if (!EmotionDotingManager.isDoting(candidate, owner)) return false;
        long until = candidate.getPersistentData().getLong(COOLDOWN_UNTIL);
        return now % 20L == 0L && (now >= until || now < until - COOLDOWN_TICKS);
    }

    private static List<BlockPos> findAvailableCages(ServerLevel level, EntityMaid actor) {
        BlockPos center = actor.blockPosition();
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (center.distSqr(cursor) > SEARCH_RADIUS * SEARCH_RADIUS) continue;
            BlockPos pos = cursor.immutable();
            CageKey key = new CageKey(level.dimension(), pos);
            if (CAGE_CLAIMS.containsKey(key) || !level.getBlockState(pos).is(ModBlocks.DARK_IRON_CAGE.get())) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof DarkIronCageBlockEntity cage && !cage.isOccupied()) {
                result.add(pos);
            }
        }
        return result;
    }

    private static DarkIronCageBlockEntity cage(ServerLevel level, Flow flow) {
        if (!level.getBlockState(flow.cagePos).is(ModBlocks.DARK_IRON_CAGE.get())
                || !(level.getBlockEntity(flow.cagePos) instanceof DarkIronCageBlockEntity cage)) {
            return null;
        }
        UUID owner = CAGE_CLAIMS.get(new CageKey(level.dimension(), flow.cagePos));
        return flow.actor.getUUID().equals(owner) ? cage : null;
    }

    private static void say(EntityMaid maid, String prefix, int count) {
        maid.getChatBubbleManager().addTextChatBubble(prefix + (1 + maid.getRandom().nextInt(count)));
    }

    private static void finish(Flow flow, boolean releasePassenger, boolean blameTarget) {
        if (FLOWS.remove(flow.actor.getUUID()) != flow) return;
        TARGET_CLAIMS.remove(flow.targetId, flow.actor.getUUID());
        CAGE_CLAIMS.remove(new CageKey(flow.dimension, flow.cagePos), flow.actor.getUUID());
        if (releasePassenger && flow.pickedUp) {
            PrincessCarryManager.releaseCarrier(flow.actor);
            if (!blameTarget) say(flow.actor, "bubble.callresponse.jealousy.release.", RELEASE_LINES);
        }
        if (blameTarget) say(flow.actor, "bubble.callresponse.jealousy.blame.", BLAME_LINES);
        clearIssuedMovement(flow);
        Entity currentTarget = flow.actor.getTarget();
        if (currentTarget != null && currentTarget.getUUID().equals(flow.targetId)) {
            flow.actor.setTarget(null);
        }
        flow.actor.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(target -> target.getUUID().equals(flow.targetId))
                .ifPresent(target -> flow.actor.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET));
        MaidMovementControl.end(flow.actor, MaidMovementControl.Reason.JEALOUSY_CAGE);
    }

    private static void clearIssuedMovement(Flow flow) {
        boolean clearedWalk = flow.actor.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(target -> target == flow.issuedWalkTarget)
                .map(target -> {
                    flow.actor.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    return true;
                }).orElse(false);
        flow.actor.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                .filter(target -> target == flow.issuedLookTarget)
                .ifPresent(target -> flow.actor.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET));
        if (clearedWalk) flow.actor.getNavigation().stop();
        flow.issuedWalkTarget = null;
        flow.issuedLookTarget = null;
    }

    public static void onBehaviorStopped(EntityMaid maid) {
        Flow flow = FLOWS.get(maid.getUUID());
        if (flow != null) finish(flow, true, false);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Flow flow : new ArrayList<>(FLOWS.values())) {
            if (MaidMovementControl.isActive(flow.actor, MaidMovementControl.Reason.CAGE)) {
                finish(flow, true, true);
            }
        }
    }

    @SubscribeEvent
    public void onMaidLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityMaid maid) {
            Flow own = FLOWS.get(maid.getUUID());
            if (own != null) finish(own, true, false);
            for (Flow flow : new ArrayList<>(FLOWS.values())) {
                if (flow.targetId.equals(maid.getUUID())) finish(flow, true, false);
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (Flow flow : new ArrayList<>(FLOWS.values())) finish(flow, true, false);
        FLOWS.clear();
        TARGET_CLAIMS.clear();
        CAGE_CLAIMS.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("jealousy_cage")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(context -> force(context.getSource(), EntityArgument.getEntities(context, "targets"))));
        event.getDispatcher().register(Commands.literal("callresponse").then(node));
    }

    private static int force(CommandSourceStack source, Collection<? extends Entity> entities) {
        List<EntityMaid> maids = entities.stream().filter(EntityMaid.class::isInstance)
                .map(EntityMaid.class::cast).toList();
        Map<StartFailure, Integer> failures = new EnumMap<>(StartFailure.class);
        int started = 0;
        for (EntityMaid maid : maids) {
            StartResult result;
            if (maid.level() instanceof ServerLevel level) {
                result = start(level, maid, true);
            } else {
                result = StartResult.failure(StartFailure.ACTOR_UNAVAILABLE);
            }
            if (result.started()) {
                started++;
            } else {
                failures.merge(result.failure(), 1, Integer::sum);
            }
        }
        int result = started;
        source.sendSuccess(() -> Component.translatable("command.callresponse.jealousy_cage.result", result), true);
        if (started == 0) {
            source.sendFailure(Component.translatable("command.callresponse.jealousy_cage.failure_summary",
                    maids.size(),
                    failures.getOrDefault(StartFailure.NOT_DOTING, 0),
                    failures.getOrDefault(StartFailure.OWNER_UNAVAILABLE, 0),
                    failures.getOrDefault(StartFailure.ACTOR_UNAVAILABLE, 0)
                            + failures.getOrDefault(StartFailure.ALREADY_RUNNING_OR_CLAIMED, 0),
                    failures.getOrDefault(StartFailure.NO_EMPTY_CAGE, 0),
                    failures.getOrDefault(StartFailure.NO_VALID_TARGET, 0)));
        }
        return started;
    }
}
