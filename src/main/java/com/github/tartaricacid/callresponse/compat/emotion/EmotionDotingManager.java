package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionDotingManager {

    // ===== 配置参数 =====
    private static final int TRUST_THRESHOLD = 90;
    private static final int FEAR_THRESHOLD = 10;

    // 攻击专用冷却
    private static final int ATTACK_COOLDOWN = 10;           // 0.25秒
    private static final int ATTACK_DIALOGUE_COOLDOWN = 200;           // 10秒

    // 日常行为共享冷却
    private static final int ACTION_COOLDOWN = 2400;         // 120秒

    // 日常行为执行超时（取不到就放弃，避免一直去箱子的路上）
    private static final int ACTION_TIMEOUT_TICKS = 100;     // 5秒

    // 三种日常行为的权重
    private static final double STEAL_WEIGHT = 0.4;
    private static final double SEARCH_WEIGHT = 0.3;
    private static final double FLOWER_WEIGHT = 0.3;


    private static final int DIALOGUE_COOLDOWN = 2400;

    // ===== 状态存储 =====
    private static final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastAttackDialogueTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastActionTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastDialogueTime = new ConcurrentHashMap<>();

    // ===== 待执行日常行为 =====
    private static final Map<UUID, ActionTask> pendingTasks = new ConcurrentHashMap<>();

    private static class ActionTask {
        ActionType type;
        BlockPos target;
        ServerPlayer player;
        long startTick;
        ActionTask(ActionType type, BlockPos target, ServerPlayer player, long startTick) {
            this.type = type;
            this.target = target;
            this.player = player;
            this.startTick = startTick;
        }
    }

    private enum ActionType { STEAL, SEARCH, FLOWER }

    // ===== 工具方法 =====
    public static boolean isDoting(EntityMaid maid, ServerPlayer player) {
        if (!maid.isTame() || maid.getOwner() == null) return false;
        EmotionData.EmotionValues values = EmotionData.get(maid, player);
        return values.trust() >= TRUST_THRESHOLD && values.fear() <= FEAR_THRESHOLD;
    }

    private static void triggerAIDialogue(EntityMaid maid, ServerPlayer player, String prompt) {
        if (maid == null || player == null) return;
        UUID maidId = maid.getUUID();
        long now = maid.level().getGameTime();
        Long lastTime = lastDialogueTime.get(maidId);
        if (lastTime != null && now - lastTime < DIALOGUE_COOLDOWN) return;
        MaidResponder.processBroadcast(player, Collections.singletonList(maid), prompt, false);
        lastDialogueTime.put(maidId, now);
    }

    private static void standUpIfSitting(EntityMaid maid) {
        if (maid.isInSittingPose()) {
            maid.setInSittingPose(false);
        }
    }

    private static void walkTo(EntityMaid maid, BlockPos target) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), 0.6f, 1));
    }

    // ===== 事件主循环 =====
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;


        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        if (!maid.isTame() || maid.getOwner() == null) return;
                        if (!maid.isAlive()) return;
                        if (!maid.getOwnerUUID().equals(player.getUUID())) return;
                        if (!isDoting(maid, player)) return;

                        long tick = maid.level().getGameTime();
                        UUID maidId = maid.getUUID();

                        // ---- 1. 攻击检测（被动，独立运行） ----
                        if (tick - lastAttackTime.getOrDefault(maidId, 0L) >= ATTACK_COOLDOWN) {
                            if (attackNearbyMaids(maid, player, tick)) {
                                lastAttackTime.put(maidId, tick);
                                return;
                            }
                        }

                        // ---- 2. 处理待执行日常行为 ----
                        if (pendingTasks.containsKey(maidId)) {
                            ActionTask task = pendingTasks.get(maidId);
                            if (tick - task.startTick >= ACTION_TIMEOUT_TICKS) {
                                pendingTasks.remove(maidId);
                                return;
                            }
                            double distSq;
                            if (task.type == ActionType.STEAL) {
                                distSq = maid.distanceToSqr(task.player);
                            } else if (task.type == ActionType.SEARCH) {
                                distSq = maid.distanceToSqr(task.target.getX() + 0.5, task.target.getY() + 0.5, task.target.getZ() + 0.5);
                            } else { // FLOWER
                                distSq = maid.distanceToSqr(task.player);
                            }

                            double threshold = (task.type == ActionType.FLOWER) ? 4.0 : 1.0;
                            if (distSq > threshold) {
                                if (task.type == ActionType.STEAL) {
                                    walkTo(maid, task.player.blockPosition());
                                } else if (task.type == ActionType.SEARCH) {
                                    walkTo(maid, task.target);
                                } else {
                                    walkTo(maid, task.player.blockPosition());
                                }
                                return;
                            } else {
                                executeAction(maid, task);
                                pendingTasks.remove(maidId);
                                return;
                            }
                        }

                        // ---- 3. 日常行为触发（共享冷却） ----
                        if (tick - lastActionTime.getOrDefault(maidId, 0L) >= ACTION_COOLDOWN) {
                            ActionType selected = selectAction(maid, player);
                            if (selected != null && startAction(maid, player, selected)) {
                                lastActionTime.put(maidId, tick);
                            }
                        }
                    });
        }
    }

    // ===== 赶走主人附近的所有其他女仆（无伤害，只击退） =====
    private static boolean attackNearbyMaids(EntityMaid maid, ServerPlayer player, long tick) {
        AABB box = new AABB(player.blockPosition()).inflate(3);
        List<EntityMaid> targets = maid.level().getEntitiesOfClass(EntityMaid.class, box,
                m -> m != maid && m.isAlive());
        if (targets.isEmpty()) return false;

        targets.sort(Comparator.comparingDouble(maid::distanceToSqr));
        EntityMaid target = targets.get(0);

        standUpIfSitting(maid);

        double distSq = maid.distanceToSqr(target);
        if (distSq > 4.0) {
            walkTo(maid, target.blockPosition());
            return true;
        }

        // 视觉攻击（不造成实际伤害）
        maid.swing(InteractionHand.MAIN_HAND);
        maid.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1.0f);

        target.hurtTime = target.hurtDuration = 10;

        // 击退目标女仆
        double dx = target.getX() - maid.getX();
        double dz = target.getZ() - maid.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.01) {
            target.setDeltaMovement(dx / dist * 0.5, 0.3, dz / dist * 0.5);
            target.hurtMarked = true;
        }

        // 对话冷却（独立于全局对话，只用自己的 10秒冷却）
        UUID maidId = maid.getUUID();
        Long lastDialogue = lastAttackDialogueTime.get(maidId);
        if (lastDialogue == null || tick - lastDialogue >= ATTACK_DIALOGUE_COOLDOWN) {
            String prompt = "你是一只被主人宠坏了的女仆，眼里容不下任何其他女仆靠近主人。你看到有其他女仆靠近主人，瞬间火冒三丈——那是你的主人！你的！请用充满占有欲和威胁的语气，说一段话把这个不知好歹的女仆赶走，要让主人知道你只允许自己独占他，也要让那个女仆知道她永远不可能比你更受宠。说话要霸道一点，宣誓主权。";
            MaidResponder.processBroadcast(player, Collections.singletonList(maid), prompt, false);
            lastAttackDialogueTime.put(maidId, tick);
        }

        return true;
    }

    // ===== 选择日常行为（按权重） =====
    private static ActionType selectAction(EntityMaid maid, ServerPlayer player) {
        double rand = maid.getRandom().nextDouble();
        if (rand < STEAL_WEIGHT) {
            if (hasPlayerItems(player)) return ActionType.STEAL;
            return selectActionFallback(maid, player);
        } else if (rand < STEAL_WEIGHT + SEARCH_WEIGHT) {
            if (findNearestContainer(maid) != null) return ActionType.SEARCH;
            return selectActionFallback(maid, player);
        } else {
            return ActionType.FLOWER;
        }
    }

    private static ActionType selectActionFallback(EntityMaid maid, ServerPlayer player) {
        if (hasPlayerItems(player)) return ActionType.STEAL;
        if (findNearestContainer(maid) != null) return ActionType.SEARCH;
        return ActionType.FLOWER;
    }

    private static boolean hasPlayerItems(ServerPlayer player) {
        if (player == null) return false;
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return true;
        for (int i = 0; i < 27; i++) {
            if (!player.getInventory().getItem(i).isEmpty()) return true;
        }
        return false;
    }

    private static BlockPos findNearestContainer(EntityMaid maid) {
        BlockPos pos = maid.blockPosition();
        int radius = 10;
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    BlockEntity be = maid.level().getBlockEntity(checkPos);
                    if (be != null && isMaidChest(be)) {
                        double d = maid.distanceToSqr(checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5);
                        if (d < minDist) {
                            minDist = d;
                            nearest = checkPos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean isMaidChest(BlockEntity be) {
        for (IChestType chestType : ChestManager.getAllChestTypes()) {
            if (chestType.isChest(be)) return true;
        }
        return false;
    }

    // ===== 启动日常行为 =====
    private static boolean startAction(EntityMaid maid, ServerPlayer player, ActionType type) {
        standUpIfSitting(maid);
        switch (type) {
            case STEAL:
                if (player == null) return false;
                pendingTasks.put(maid.getUUID(), new ActionTask(type, null, player, maid.level().getGameTime()));
                walkTo(maid, player.blockPosition());
                return true;
            case SEARCH:
                BlockPos container = findNearestContainer(maid);
                if (container == null) return false;
                pendingTasks.put(maid.getUUID(), new ActionTask(type, container, player, maid.level().getGameTime()));
                walkTo(maid, container);
                return true;
            case FLOWER:
                pendingTasks.put(maid.getUUID(), new ActionTask(type, null, player, maid.level().getGameTime()));
                walkTo(maid, player.blockPosition());
                return true;
            default:
                return false;
        }
    }

    // ===== 执行日常行为 =====
    private static void executeAction(EntityMaid maid, ActionTask task) {
        switch (task.type) {
            case STEAL:
                executeSteal(maid, task.player);
                break;
            case SEARCH:
                executeSearch(maid, task.player, task.target);
                break;
            case FLOWER:
                executeFlower(maid, task.player);
                break;
        }
    }

    // ===== 偷主人 =====
    private static void executeSteal(EntityMaid maid, ServerPlayer player) {
        List<ItemStack> candidates = new ArrayList<>();
        if (!player.getMainHandItem().isEmpty()) candidates.add(player.getMainHandItem());
        if (!player.getOffhandItem().isEmpty()) candidates.add(player.getOffhandItem());
        for (int i = 0; i < 27; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) candidates.add(stack);
        }

        if (candidates.isEmpty()) {
            triggerAIDialogue(maid, player, "你翻了翻主人的口袋，发现他居然什么都没有！你作为一只被宠坏的女仆，觉得丢脸死了。请用一句嫌弃又撒娇的语气嘲讽主人穷酸，表达你的失望——但你要让主人知道，就算他穷你也勉为其难跟着他好了，谁让你是他最爱的女仆呢。");
            return;
        }

        ItemStack selected = candidates.get(maid.getRandom().nextInt(candidates.size()));
        ItemStack taken = selected.copy();

        if (player.getMainHandItem() == selected) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else if (player.getOffhandItem() == selected) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        } else {
            for (int i = 0; i < 27; i++) {
                if (player.getInventory().getItem(i) == selected) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
        }

        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getMaidInv(), taken, false);
        if (!remaining.isEmpty()) maid.spawnAtLocation(remaining);

        String itemName = taken.getDisplayName().getString();
        String prompt = "你趁主人不注意，偷偷拿了一样东西（" + itemName + "）——作为一只被宠坏的女仆，你早就习惯了主人什么都顺着你。主人东西多的是，拿一件怎么了？请用一句理直气壮又带点调皮的话告诉主人：你拿了就是你的了，完全不觉得理亏，甚至觉得是主人赚了——你的笑容不就是最好的回报吗？语气要像在说'我看上它是你的福气'。";
        triggerAIDialogue(maid, player, prompt);
    }

    // ===== 偷箱子 =====
    private static void executeSearch(EntityMaid maid, ServerPlayer player, BlockPos targetPos) {
        BlockEntity be = maid.level().getBlockEntity(targetPos);
        if (be == null) return;
        IItemHandler handler = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return;

        List<Integer> slotsWithItems = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) slotsWithItems.add(i);
        }
        if (slotsWithItems.isEmpty()) {
            triggerAIDialogue(maid, player, "你打开箱子，发现里面居然是空的！你作为主人的掌上明珠，怎么可以忍受空箱子？请用一句嫌弃又撒娇的语气抱怨主人连箱子都不塞满——言下之意是：快快快塞满，不然怎么配得上我这么可爱的女仆。");
            return;
        }

        int slot = slotsWithItems.get(maid.getRandom().nextInt(slotsWithItems.size()));
        ItemStack stack = handler.getStackInSlot(slot);
        ItemStack taken = handler.extractItem(slot, stack.getCount(), false);
        if (!taken.isEmpty()) {
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getMaidInv(), taken, false);
            if (!remaining.isEmpty()) maid.spawnAtLocation(remaining);
            String itemName = taken.getDisplayName().getString();
            String prompt = "你在主人家的箱子里翻到一样东西（" + itemName + "），你也不知道它值不值钱，反正你看着合眼缘就拿走了。作为一只被宠坏的女仆，你心里根本没有任何'偷'的概念——主人的就是你的，这家里有什么是你不能拿的？请用一句像在自己家翻自己东西一样自然的语气告诉主人你拿了什么，仿佛这本来就是你的。天真又理直气壮，完全不怕主人怪罪。";
            triggerAIDialogue(maid, player, prompt);
        }
    }

    // ===== 种花 =====
    private static void executeFlower(EntityMaid maid, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    if (maid.level().isEmptyBlock(pos)) {
                        BlockPos below = pos.below();
                        BlockState belowState = maid.level().getBlockState(below);
                        if (belowState.is(Blocks.GRASS_BLOCK) || belowState.is(Blocks.DIRT))
                        {
                            candidates.add(pos);
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            BlockPos below = playerPos.below();
            if (maid.level().isEmptyBlock(below) || maid.level().getBlockState(below).canBeReplaced()) {
                maid.level().setBlock(below, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                BlockPos flowerPos = below.above();
                if (maid.level().isEmptyBlock(flowerPos)) placeRandomFlower(maid, flowerPos);
            }
            return;
        }

        BlockPos target = candidates.get(maid.getRandom().nextInt(candidates.size()));
        placeRandomFlower(maid, target);
    }

    private static void placeRandomFlower(EntityMaid maid, BlockPos pos) {
        Block[] flowers = new Block[]{
                Blocks.POPPY, Blocks.DANDELION, Blocks.BLUE_ORCHID,
                Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP,
                Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
                Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY
        };
        Block flower = flowers[maid.getRandom().nextInt(flowers.length)];
        if (maid.level().isEmptyBlock(pos) && pos.getY() >= maid.level().getMinBuildHeight()) {
            maid.level().setBlock(pos, flower.defaultBlockState(), 3);
            maid.swing(InteractionHand.MAIN_HAND);
            maid.playSound(net.minecraft.sounds.SoundEvents.GRASS_PLACE, 0.8f, 1.0f);
            String flowerName = flower.getName().getString();
            String prompt = "你在主人身边种了一朵" + flowerName + "，这可是你精心挑选的，觉得这朵花配得上主人的好。请用一句甜蜜又娇气的话告诉主人你为他种了花，语气要像是送出了一份大礼——你可是亲手种的！主人必须得好好夸你才行！";
            if (maid.getOwner() instanceof ServerPlayer owner) {
                triggerAIDialogue(maid, owner, prompt);
            }
        }
    }

    public static void resetDoting(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        lastAttackTime.remove(maidId);
        lastAttackDialogueTime.remove(maidId);
        lastActionTime.remove(maidId);
        lastDialogueTime.remove(maidId);
        pendingTasks.remove(maidId);
    }
}