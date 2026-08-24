package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class AttackOtherMaidAction {

    private static final int SEARCH_RADIUS = 16;
    private static final ResourceLocation ATTACK_TASK_ID = new ResourceLocation("touhou_little_maid:attack");

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
            maid.sendSystemMessage(Component.literal("§c[攻击] 附近没有可攻击的女仆！"));
            if (debugPlayer != null) {
                debugPlayer.sendSystemMessage(Component.literal("§c[调试] 没有找到攻击目标"));
            }
            return;
        }

        // 2. 选择最近的目标
        targets.sort(Comparator.comparingDouble(maid::distanceToSqr));
        EntityMaid target = targets.get(0);
        // 3. 强制切换为近战攻击任务（任务/坐姿基线由主线程调度器保存）
        BroadcastMovementScheduler.startAttack(maid, target, debugPlayer);
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
        // 8. 立即造成一次伤害（触发攻击动画）
        performMeleeAttack(maid, target);

        // 10. 反馈消息
        String maidName = maid.getCustomName() != null ? maid.getCustomName().getString() : "女仆";
        String targetName = target.getCustomName() != null ? target.getCustomName().getString() : "女仆";
        maid.sendSystemMessage(Component.literal("§a[攻击] " + maidName + " 开始攻击 " + targetName));

        if (debugPlayer != null) {
            debugPlayer.sendSystemMessage(Component.literal("§a[调试] " + maidName + " 锁定目标: " + targetName));
        }
    }

    /**
     * 停止女仆的攻击
     */
    public static void stopAttack(EntityMaid maid) {
        BroadcastMovementScheduler.stopAttack(maid);
    }

    /**
     * 清除女仆背包中的远程武器
     */
    private static void clearRangedWeapons(EntityMaid maid) {
        var inv = maid.getMaidInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            var stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String itemId = stack.getItem().getDescriptionId();
                if (itemId.contains("bow") || itemId.contains("crossbow") ||
                        itemId.contains("snowball") || itemId.contains("egg") ||
                        itemId.contains("trident") || itemId.contains("gun")) {
                    inv.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
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
        target.hurt(target.damageSources().mobAttack(attacker), (float) attackDamage);

        attacker.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f);
    }

    /**
     * 停战动作（供 StopAttackAction 调用）
     */
    public static void stopAllAttacks(EntityMaid maid, ServerPlayer debugPlayer) {
        if (!BroadcastMovementScheduler.isAttacking(maid)) {
            maid.sendSystemMessage(Component.literal("§e[停战] 当前没有攻击目标"));
            if (debugPlayer != null) {
                debugPlayer.sendSystemMessage(Component.literal("§e[调试] " + maid.getCustomName() + " 没有正在攻击的目标"));
            }
            return;
        }
        stopAttack(maid);
        maid.sendSystemMessage(Component.literal("§a[停战] 已停止攻击"));
        if (debugPlayer != null) {
            debugPlayer.sendSystemMessage(Component.literal("§a[调试] " + maid.getCustomName() + " 已停战"));
        }
    }
}
