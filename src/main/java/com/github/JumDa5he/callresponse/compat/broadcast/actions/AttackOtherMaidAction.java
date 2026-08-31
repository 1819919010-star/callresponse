package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class AttackOtherMaidAction {

    private static final int SEARCH_RADIUS = 16;
    private static final int ATTACK_COOLDOWN_TICKS = 10; // 每 0.5 秒攻击一次
    private static final Identifier ATTACK_TASK_ID = Identifier.parse("touhou_little_maid:attack");

    // 管理每个女仆的攻击状态
    private static final Map<UUID, ScheduledExecutorService> attackThreads = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> currentTargets = new ConcurrentHashMap<>();

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        // 如果已经在攻击，先停止旧攻击
        stopAttack(maid);

        // 1. 查找周围所有其他女仆（无差别攻击）
        AABB area = maid.getBoundingBox().inflate(SEARCH_RADIUS);
        List<EntityMaid> targets = maid.level().getEntitiesOfClass(
                EntityMaid.class, area,
                (m) -> m != maid && m.isAlive()
        );

        if (targets.isEmpty()) {
            if (debugPlayer != null) {
                debugPlayer.sendSystemMessage(Component.literal("§c[调试] 没有找到攻击目标"));
            }
            return;
        }

        // 2. 选择最近的目标
        targets.sort(Comparator.comparingDouble(maid::distanceToSqr));
        EntityMaid target = targets.get(0);
        UUID maidId = maid.getUUID();
        UUID targetId = target.getUUID();

        // 3. 如果女仆坐着，先站起来
        if (maid.isInSittingPose()) {
            maid.setInSittingPose(false);
        }

        // 4. 强制切换为近战攻击任务
        var attackTask = TaskManager.findTask(ATTACK_TASK_ID);
        if (attackTask.isPresent()) {
            maid.setTask(attackTask.get());
        } else {
            // 备选：直接实例化
            maid.setTask(new TaskAttack());
        }

        // 5. 清空所有远程武器（防止扔雪球/弩/弓）
        // 清空主手和副手
        maid.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        maid.setItemInHand(InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        // 清空背包中的远程武器（雪球、弓、弩等）
        clearRangedWeapons(maid);

        // 6. 清除干扰性记忆
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);

        // 7. 设置攻击目标
        maid.setTarget(target);
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        currentTargets.put(maidId, targetId);

        // 8. 立即造成一次伤害（触发攻击动画）
        performMeleeAttack(maid, target);

        // 9. 启动持续攻击线程（每 10 tick 攻击一次）
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        attackThreads.put(maidId, executor);

        executor.scheduleAtFixedRate(() -> {
            // 检查女仆是否存活、目标是否存活、是否应该继续攻击
            if (!maid.isAlive() || !target.isAlive() || !currentTargets.containsKey(maidId)) {
                stopAttack(maid);
                return;
            }

            // 检查目标是否仍然是当前目标
            UUID currentTargetId = currentTargets.get(maidId);
            if (currentTargetId == null || !currentTargetId.equals(targetId)) {
                stopAttack(maid);
                return;
            }

            // 在主线程执行攻击
            maid.level().getServer().execute(() -> {
                if (!maid.isAlive() || !target.isAlive()) {
                    stopAttack(maid);
                    return;
                }

                double distance = maid.distanceTo(target);

                // 如果距离大于 2 格，走过去
                if (distance > 2.0) {
                    maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new BlockPosTracker(target.blockPosition()), 0.8f, 1));
                } else {
                    // 距离足够近，执行近战攻击
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    performMeleeAttack(maid, target);
                }
            });

        }, 0, ATTACK_COOLDOWN_TICKS * 50, TimeUnit.MILLISECONDS);

        // 10. 反馈消息
        Component maidName = maid.getName();
        Component targetName = target.getName();

        if (debugPlayer != null) {
            debugPlayer.sendSystemMessage(Component.literal("§a[调试] ").append(maidName).append(" 锁定目标: ").append(targetName));
        }
    }

    /**
     * 停止女仆的攻击
     */
    public static void stopAttack(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        ScheduledExecutorService executor = attackThreads.remove(maidId);
        if (executor != null) {
            executor.shutdownNow();
        }
        currentTargets.remove(maidId);
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    /**
     * 清除女仆背包中的远程武器
     */
    private static void clearRangedWeapons(EntityMaid maid) {
        var inv = maid.getMaidInv();
        for (int i = 0; i < inv.size(); i++) {
            var stack = ItemsUtil.extractItem(inv, i, 1, true, null);
            if (!stack.isEmpty()) {
                String itemId = stack.getItem().getDescriptionId();
                if (itemId.contains("bow") || itemId.contains("crossbow") ||
                        itemId.contains("snowball") || itemId.contains("egg") ||
                        itemId.contains("trident") || itemId.contains("gun")) {
                    ItemsUtil.setStackInSlot(inv, i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
    }

    /**
     * 执行近战攻击
     */
    static void performMeleeAttack(EntityMaid attacker, LivingEntity target) {
        attacker.swing(InteractionHand.MAIN_HAND);

        double attackDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE) != null ?
                attacker.getAttribute(Attributes.ATTACK_DAMAGE).getValue() : 2.0;
        target.hurtServer(((ServerLevel) target.level()), target.damageSources().mobAttack(attacker), (float) attackDamage);

        attacker.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f);
    }

    /**
     * 停战动作（供 StopAttackAction 调用）
     */
    public static void stopAllAttacks(EntityMaid maid, ServerPlayer debugPlayer) {
        if (!attackThreads.containsKey(maid.getUUID())) {
            if (debugPlayer != null) {
                debugPlayer.sendSystemMessage(
                        Component.literal("§e[调试] ")
                                .append(maid.getName())
                                .append(Component.literal(" 没有正在攻击的目标"))
                );
            }
            return;
        }
        stopAttack(maid);
        if (debugPlayer != null) {
            debugPlayer.sendSystemMessage(
                    Component.literal("§a[调试] ")
                            .append(maid.getName())
                            .append(Component.literal(" 已停战"))
            );
        }
    }
}
