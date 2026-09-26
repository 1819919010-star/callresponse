package com.github.JumDa5he.callresponse.compat.brain;

import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 低饥饿时通过 TLM Brain 正式寻找附近食物容器，只负责取一份食物。 */
public final class SeekFoodBehavior extends Behavior<EntityMaid> {
    private static final float START_HUNGER = 15.0f;
    private static final int SEARCH_RADIUS = 12;
    private static final int SEARCH_HEIGHT = 4;
    private static final int WALK_TIMEOUT = 20 * 15;
    private static final double ARRIVE_DISTANCE_SQR = 2.5 * 2.5;
    private static final Set<UUID> SEEKING_MAIDS = ConcurrentHashMap.newKeySet();

    private @Nullable BlockPos targetPos;
    private @Nullable WalkTarget issuedWalkTarget;
    private long nextSearchTime;
    private long lastSourceCheckTime;
    private boolean finished;
    private boolean restoreSittingPose;

    public SeekFoodBehavior() {
        super(ImmutableMap.of(
                // 允许正式 Behavior 在启动时替换低优先级的跟随/闲逛目标。
                // 只在 start 写入一次，不做每 tick 强制覆盖。
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.HURT_BY_ENTITY, MemoryStatus.VALUE_ABSENT
        ), WALK_TIMEOUT);
    }

    public static boolean isSeeking(EntityMaid maid) {
        return SEEKING_MAIDS.contains(maid.getUUID());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        long gameTime = level.getGameTime();
        if (gameTime < nextSearchTime || !canSeek(maid) || !hasBackpackSpace(maid)) {
            return false;
        }
        nextSearchTime = gameTime + 80 + maid.getRandom().nextInt(41);
        targetPos = findNearestFoodSource(level, maid);
        return targetPos != null;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (targetPos == null) return;
        finished = false;
        lastSourceCheckTime = gameTime;
        // 坐下命令本来会禁止 Brain 移动。低饥饿找食物期间临时站起，
        // 正常取完或安全失败后再恢复原来的坐姿，不永久改变玩家命令。
        restoreSittingPose = maid.isMaidInSittingPose();
        if (restoreSittingPose) {
            maid.setInSittingPose(false);
        }
        issuedWalkTarget = new WalkTarget(new BlockPosTracker(targetPos), 0.6f, 2);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, issuedWalkTarget);
        SEEKING_MAIDS.add(maid.getUUID());
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (targetPos == null) {
            finished = true;
            return;
        }
        if (maid.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5) <= ARRIVE_DISTANCE_SQR) {
            takeOneFood(level, targetPos, maid);
            finished = true;
            return;
        }
        if (gameTime - lastSourceCheckTime >= 20) {
            lastSourceCheckTime = gameTime;
            if (findSourceHandler(level, targetPos, maid) == null
                    || maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty()) {
                finished = true;
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return !finished && targetPos != null && canSeek(maid) && hasBackpackSpace(maid);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walkTarget -> walkTarget == issuedWalkTarget)
                .ifPresent(walkTarget -> maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET));
        SEEKING_MAIDS.remove(maid.getUUID());
        // 遭遇战斗、受伤或狩猎时先服从危险状态，避免刚被打又强制坐回去。
        if (restoreSittingPose && maid.isAlive()
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                && !HuntOrderManager.isHunting(maid)) {
            maid.setInSittingPose(true);
        }
        targetPos = null;
        issuedWalkTarget = null;
        finished = false;
        restoreSittingPose = false;
    }

    private static boolean canSeek(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return maid.isAlive() && maid.isTame() && owner != null && owner.isAlive()
                && HungerData.get(maid) < START_HUNGER
                // 坐姿由 start 临时解除；睡觉、乘坐和拴绳仍然不能被饥饿打断。
                && !maid.isPassenger() && !maid.isSleeping() && !maid.isLeashed()
                && !maid.getSwimManager().isGoingToBreath()
                && !HuntOrderManager.isHunting(maid)
                && !MaidMovementControl.controlsPath(maid)
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY);
    }

    static boolean hasBackpackSpace(EntityMaid maid) {
        CombinedInvWrapper inventory = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), inventory.getSlotLimit(slot))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockPos findNearestFoodSource(ServerLevel level, EntityMaid maid) {
        BlockPos origin = maid.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                if (dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS) continue;
                for (int dy = -SEARCH_HEIGHT; dy <= SEARCH_HEIGHT; dy++) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    if (!level.hasChunkAt(check)) continue;
                    BlockPos sourcePos = canonicalSourcePos(level, check);
                    if (sourcePos == null) continue;
                    IItemHandler handler = findSourceHandler(level, sourcePos, maid);
                    if (handler == null || findTransferSlot(handler, maid) < 0) continue;
                    double distance = sourcePos.distSqr(origin);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = sourcePos.immutable();
                    }
                }
            }
        }
        return nearest;
    }

    @Nullable
    private static BlockPos canonicalSourcePos(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TileEntityPicnicMat picnicMat) {
            BlockPos center = picnicMat.getCenterPos();
            return level.getBlockEntity(center) instanceof TileEntityPicnicMat ? center : null;
        }
        return isTlmChest(blockEntity) ? pos : null;
    }

    private static boolean isTlmChest(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) return false;
        for (IChestType chestType : ChestManager.getAllChestTypes()) {
            if (chestType.isChest(blockEntity)) return true;
        }
        return false;
    }

    @Nullable
    private static IItemHandler findSourceHandler(ServerLevel level, BlockPos pos, EntityMaid maid) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TileEntityPicnicMat picnicMat) {
            return picnicMat.getHandler();
        }
        if (blockEntity == null || !(maid.getOwner() instanceof Player owner)) return null;
        boolean allowed = false;
        for (IChestType chestType : ChestManager.getAllChestTypes()) {
            if (chestType.isChest(blockEntity)) {
                allowed = chestType.canOpenByPlayer(blockEntity, owner)
                        && chestType.getOpenCount(level, pos, blockEntity) <= IChestType.ALLOW_COUNT;
                break;
            }
        }
        if (!allowed) return null;
        IItemHandler capability = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (capability != null) return capability;
        return blockEntity instanceof Container container ? new InvWrapper(container) : null;
    }

    private static int findTransferSlot(IItemHandler source, EntityMaid receiver) {
        CombinedInvWrapper destination = receiver.getAvailableBackpackInv();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack sample = source.extractItem(slot, 1, true);
            if (sample.isEmpty() || sample.getFoodProperties(receiver) == null) continue;
            if (ItemHandlerHelper.insertItemStacked(destination, sample.copy(), true).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    static boolean transferOne(IItemHandler source, EntityMaid receiver) {
        int slot = findTransferSlot(source, receiver);
        if (slot < 0) return false;
        ItemStack extracted = source.extractItem(slot, 1, false);
        if (extracted.isEmpty()) return false;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                receiver.getAvailableBackpackInv(), extracted, false);
        if (!remainder.isEmpty()) {
            ItemHandlerHelper.insertItemStacked(source, remainder, false);
            return false;
        }
        return true;
    }

    private static boolean takeOneFood(ServerLevel level, BlockPos pos, EntityMaid maid) {
        IItemHandler source = findSourceHandler(level, pos, maid);
        if (source == null || !transferOne(source, maid)) return false;
        if (level.getBlockEntity(pos) instanceof TileEntityPicnicMat picnicMat) {
            picnicMat.refresh();
        } else if (level.getBlockEntity(pos) != null) {
            level.getBlockEntity(pos).setChanged();
        }
        return true;
    }
}
