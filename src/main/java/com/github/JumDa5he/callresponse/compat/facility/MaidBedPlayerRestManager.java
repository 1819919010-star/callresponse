package com.github.JumDa5he.callresponse.compat.facility;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 玩家无法正式入睡时，在女仆床上维持一个不写入 sleepingPos 的纯躺卧状态。 */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class MaidBedPlayerRestManager {
    private static final long DISPLACED_COOLDOWN_TICKS = 5L * 60L * 20L;
    private static final String DISPLACED_COOLDOWN_TAG = "CallResponseMaidBedDisplacedCooldown";
    private static final String FIXED_REPLY_PREFIX = "message.callresponse.maid_bed.displaced.";
    private static final int FIXED_REPLY_COUNT = 4;
    private static final Map<UUID, RestState> RESTING = new HashMap<>();

    private MaidBedPlayerRestManager() {
    }

    /** TLM 的女仆床没有玩家睡眠交互，使用 Forge 方块右键事件新增完整入口。 */
    @SubscribeEvent
    public static void onRightClickMaidBed(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !event.getEntity().getMainHandItem().isEmpty()
                || !event.getEntity().getOffhandItem().isEmpty()) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof BlockMaidBed)) return;

        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            useMaidBed(player, event.getPos());
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }

    public static void useMaidBed(ServerPlayer player, BlockPos clickedPos) {
        ServerLevel level = player.serverLevel();
        BlockPos headPos = canonicalHead(level, clickedPos);
        if (headPos == null) return;

        RestState oldState = RESTING.get(player.getUUID());
        if (oldState != null) {
            stopResting(player);
            return;
        }

        int capacity = FacilityCapacityManager.getCapacity(level, headPos);
        int occupants = FacilityCapacityManager.getCurrentOccupants(level, headPos);
        EntityMaid maidToDisplace = occupants >= capacity ? findSleepingMaid(level, headPos) : null;
        if (capacity <= 0 || (occupants >= capacity && maidToDisplace == null)) {
            player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
            return;
        }

        // 女仆床无论当前能否正式入睡，都像原版床一样先登记重生点。
        // forced 保持 false，死亡后的安全落点搜索仍走正常床铺规则。
        player.setRespawnPosition(level.dimension(), headPos, player.getYRot(), false, true);

        // 多人床可能已被女仆标记为 occupied；这里只在调用原版睡眠入口期间临时放行，
        // 实际容量已经由上面的统一计数检查完成。
        setOccupied(level, headPos, false);
        var sleepResult = player.startSleepInBed(headPos);
        if (sleepResult.right().isPresent()) {
            if (maidToDisplace != null) displaceSleepingMaid(maidToDisplace, player);
            setOccupied(level, headPos, true);
            return;
        }

        Player.BedSleepingProblem problem = sleepResult.left().orElse(Player.BedSleepingProblem.OTHER_PROBLEM);
        if (!canFallBackToRest(problem)) {
            setOccupied(level, headPos, FacilityCapacityManager.getCurrentOccupants(level, headPos) > 0);
            Component message = problem.getMessage();
            if (message != null) player.displayClientMessage(message, true);
            return;
        }

        if (maidToDisplace != null) displaceSleepingMaid(maidToDisplace, player);
        beginResting(player, headPos);
    }

    private static boolean canFallBackToRest(Player.BedSleepingProblem problem) {
        return problem != Player.BedSleepingProblem.NOT_SAFE
                && problem != Player.BedSleepingProblem.TOO_FAR_AWAY
                && problem != Player.BedSleepingProblem.OBSTRUCTED;
    }

    private static void beginResting(ServerPlayer player, BlockPos headPos) {
        ServerLevel level = player.serverLevel();
        Vec3 anchor = new Vec3(headPos.getX() + 0.5D, headPos.getY() + 0.8D, headPos.getZ() + 0.5D);
        RESTING.put(player.getUUID(), new RestState(level.dimension(), headPos.immutable(), anchor));
        setOccupied(level, headPos, true);
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setPos(anchor.x, anchor.y, anchor.z);
        player.setPose(Pose.SLEEPING);
    }

    public static boolean isResting(Player player) {
        return !player.level().isClientSide && RESTING.containsKey(player.getUUID());
    }

    /** 客户端以“睡眠姿势但没有 sleepingPos，且躺在女仆床头上”识别同步过来的休息状态。 */
    public static boolean shouldKeepRestingPose(Player player) {
        if (player.isSleeping() || player.getPose() != Pose.SLEEPING) return false;
        if (!player.level().isClientSide) return RESTING.containsKey(player.getUUID());
        return findNearbyHead(player.level(), player.blockPosition()) != null;
    }

    @Nullable
    public static Direction bedDirection(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.getPose() != Pose.SLEEPING) return null;

        BlockPos bedPos = player.getSleepingPos().orElse(null);
        if (bedPos == null && !player.level().isClientSide) {
            RestState state = RESTING.get(player.getUUID());
            if (state != null && state.dimension.equals(player.level().dimension())) bedPos = state.headPos;
        }
        if (bedPos == null) bedPos = findNearbyHead(player.level(), player.blockPosition());
        BlockPos headPos = bedPos == null ? null : canonicalHead(player.level(), bedPos);
        if (headPos == null) return null;
        return player.level().getBlockState(headPos).getValue(BlockMaidBed.FACING);
    }

    public static boolean isUsedByPlayer(ServerLevel level, BlockPos bedPos) {
        return countPlayersUsingBed(level, bedPos) > 0;
    }

    public static int countPlayersUsingBed(ServerLevel level, BlockPos bedPos) {
        BlockPos headPos = canonicalHead(level, bedPos);
        if (headPos == null) return 0;
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (headPos.equals(player.getSleepingPos().orElse(null))) {
                count++;
                continue;
            }
            RestState state = RESTING.get(player.getUUID());
            if (state != null && state.dimension.equals(level.dimension()) && state.headPos.equals(headPos)) count++;
        }
        return count;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        RestState state = RESTING.get(player.getUUID());
        if (state == null) return;

        boolean invalid = !player.isAlive() || player.isRemoved() || player.isPassenger()
                || !state.dimension.equals(player.level().dimension())
                || !isMaidBedHead(player.level(), state.headPos)
                || player.position().distanceToSqr(state.anchor) > 1.0D;
        if (invalid) {
            stopResting(player);
            return;
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setPose(Pose.SLEEPING);
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && RESTING.containsKey(player.getUUID())) {
            stopResting(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stopResting(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stopResting(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        RESTING.remove(event.getOriginal().getUUID());
    }

    @SubscribeEvent
    public static void onBedBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos headPos = canonicalHead(level, event.getPos());
        if (headPos == null) return;
        List<ServerPlayer> users = level.players().stream()
                .filter(player -> {
                    RestState state = RESTING.get(player.getUUID());
                    return state != null && state.headPos.equals(headPos);
                }).toList();
        users.forEach(MaidBedPlayerRestManager::stopResting);
    }

    private static void stopResting(ServerPlayer player) {
        RestState state = RESTING.remove(player.getUUID());
        if (state == null) return;
        ServerLevel bedLevel = player.server.getLevel(state.dimension);
        if (bedLevel != null) {
            setOccupied(bedLevel, state.headPos,
                    FacilityCapacityManager.getCurrentOccupants(bedLevel, state.headPos) > 0);
        }
        if (!player.isSleeping() && player.getPose() == Pose.SLEEPING) {
            player.setPose(Pose.STANDING);
            player.refreshDimensions();
        }
    }

    @Nullable
    private static EntityMaid findSleepingMaid(ServerLevel level, BlockPos headPos) {
        AABB search = new AABB(headPos).inflate(1.5D, 1.5D, 1.5D);
        List<EntityMaid> sleepers = level.getEntitiesOfClass(EntityMaid.class, search, maid ->
                maid.isSleeping() && headPos.equals(maid.getSleepingPos().orElse(null)));
        return sleepers.isEmpty() ? null : sleepers.get(0);
    }

    private static void displaceSleepingMaid(EntityMaid maid, ServerPlayer player) {
        maid.stopSleeping();
        if (maid.isOwnedBy(player)) applyDisplacedPenalty(maid, player);
    }

    private static void applyDisplacedPenalty(EntityMaid maid, ServerPlayer owner) {
        long now = maid.level().getGameTime();
        long cooldownUntil = maid.getPersistentData().getLong(DISPLACED_COOLDOWN_TAG);
        if (now < cooldownUntil) return;

        maid.getPersistentData().putLong(DISPLACED_COOLDOWN_TAG, now + DISPLACED_COOLDOWN_TICKS);
        maid.setFavorability(Math.max(0, maid.getFavorability() - 1));
        EmotionData.addTrust(maid, owner.getUUID(), -1);

        if (BroadcastConfig.NPC_EVENT_AI_REPLY_ENABLED.get()) {
            String prompt = "这是一次女仆日常互动：主人占用了女仆正在睡的床，女仆被迫起床。"
                    + "请结合当前人格和情绪，自然地向主人抱怨自己没有地方睡；不超过30字，不提系统、数值或AI。";
            MaidResponder.processBroadcast(owner, List.of(maid), prompt, false);
        } else {
            int variant = 1 + maid.getRandom().nextInt(FIXED_REPLY_COUNT);
            maid.getChatBubbleManager().addTextChatBubble(FIXED_REPLY_PREFIX + variant);
        }
    }

    private static void setOccupied(ServerLevel level, BlockPos headPos, boolean occupied) {
        if (!isMaidBedHead(level, headPos)) return;
        BlockState headState = level.getBlockState(headPos);
        Direction facing = headState.getValue(BlockMaidBed.FACING);
        BlockPos footPos = headPos.relative(facing.getOpposite());
        level.setBlock(headPos, headState.setValue(BlockMaidBed.OCCUPIED, occupied), 3);
        BlockState footState = level.getBlockState(footPos);
        if (footState.getBlock() instanceof BlockMaidBed) {
            level.setBlock(footPos, footState.setValue(BlockMaidBed.OCCUPIED, occupied), 3);
        }
    }

    @Nullable
    private static BlockPos canonicalHead(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockMaidBed)) return null;
        return state.getValue(BlockMaidBed.PART) == BedPart.HEAD
                ? pos.immutable()
                : pos.relative(state.getValue(BlockMaidBed.FACING)).immutable();
    }

    @Nullable
    private static BlockPos findNearbyHead(Level level, BlockPos center) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    BlockPos head = canonicalHead(level, candidate);
                    if (head != null && head.distSqr(center) <= 3.0D) return head;
                }
            }
        }
        return null;
    }

    private static boolean isMaidBedHead(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BlockMaidBed
                && state.getValue(BlockMaidBed.PART) == BedPart.HEAD;
    }

    private record RestState(ResourceKey<Level> dimension, BlockPos headPos, Vec3 anchor) {
    }
}
