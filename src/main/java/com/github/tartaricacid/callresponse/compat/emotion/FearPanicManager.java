package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FearPanicManager {

    // ===== 恐惧支配：恐惧值比信任值高 30 且主人在 4 格内 =====
    private static final int FEAR_TRUST_GAP = 30;
    private static final int OWNER_DISTANCE = 4;
    private static final long PANIC_COOLDOWN_TICKS = 20 * 60 * 3; // 3分钟
    private static final int RETREAT_MIN_DISTANCE = 8;
    private static final int RETREAT_MAX_DISTANCE = 10;

    // ===== 远离阶段（走完才算结束，期间持续压过跟随任务） =====
    private static final double FLEE_ARRIVE_DISTANCE = 2.0;
    private static final long FLEE_TIMEOUT = 20 * 15; // 15秒内走不到就放弃

    private static final Map<UUID, Long> lastPanicTime = new HashMap<>();
    private static final Map<UUID, FleeState> fleeStates = new HashMap<>();

    private static class FleeState {
        final BlockPos target;
        final long startTick;

        FleeState(BlockPos target, long startTick) {
            this.target = target;
            this.startTick = startTick;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        if (!maid.isAlive()) return;
                        if (!maid.isTame()) return;

                        UUID maidId = maid.getUUID();
                        long tick = maid.level().getGameTime();

                        // 1. 远离进行中：持续设置 WALK_TARGET 朝远离点走
                        //    CORE 行为里 walkToTarget(优先级2) 高于 followOwner(3)，只要 WALK_TARGET 在，
                        //    跟随任务就不会把女仆拉回主人身边
                        FleeState flee = fleeStates.get(maidId);
                        if (flee != null) {
                            if (tick - flee.startTick > FLEE_TIMEOUT
                                    || maid.blockPosition().distSqr(flee.target) < FLEE_ARRIVE_DISTANCE * FLEE_ARRIVE_DISTANCE) {
                                // 走到或走不到都结束：开 home mode 保持不跟随，恢复正常逻辑
                                fleeStates.remove(maidId);
                                maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
                                maid.setHomeModeEnable(true);
                                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                            } else {
                                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                        new WalkTarget(new BlockPosTracker(flee.target), 0.7f, 1));
                            }
                            return;
                        }

                        // 2. 触发检查
                        LivingEntity ownerEntity = maid.getOwner();
                        if (!(ownerEntity instanceof ServerPlayer ownerPlayer)) return;

                        // CD 检查：确保只远离这一次，CD 到了才能再触发
                        Long lastPanic = lastPanicTime.get(maidId);
                        if (lastPanic != null && tick - lastPanic < PANIC_COOLDOWN_TICKS) return;

                        // 恐惧值比信任值高 30
                        EmotionData.EmotionValues values = EmotionData.get(maid, ownerPlayer.getUUID());
                        if (values.fear() - values.trust() < FEAR_TRUST_GAP) return;

                        // 主人在自己 4 格内
                        if (!maid.closerThan(ownerPlayer, OWNER_DISTANCE)) return;

                        // 3. 触发恐惧支配
                        lastPanicTime.put(maidId, tick);

                        // 3.1 站起来（防止本来是坐着）
                        if (maid.isInSittingPose()) {
                            maid.setInSittingPose(false);
                        }

                        // 3.2 计算远离点：主人反方向 8~10 格（水平方向）
                        double dx = maid.getX() - ownerPlayer.getX();
                        double dz = maid.getZ() - ownerPlayer.getZ();
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist > 0.1) {
                            int retreatDist = RETREAT_MIN_DISTANCE
                                    + maid.getRandom().nextInt(RETREAT_MAX_DISTANCE - RETREAT_MIN_DISTANCE + 1);
                            double targetX = maid.getX() + (dx / dist) * retreatDist;
                            double targetZ = maid.getZ() + (dz / dist) * retreatDist;
                            BlockPos retreatPos = new BlockPos((int) targetX, maid.blockPosition().getY(), (int) targetZ);
                            fleeStates.put(maidId, new FleeState(retreatPos, tick));
                        } else {
                            // 和主人重叠：直接随机方向远离
                            double angle = maid.getRandom().nextDouble() * Math.PI * 2;
                            int retreatDist = RETREAT_MIN_DISTANCE
                                    + maid.getRandom().nextInt(RETREAT_MAX_DISTANCE - RETREAT_MIN_DISTANCE + 1);
                            BlockPos retreatPos = new BlockPos(
                                    (int) (maid.getX() + Math.cos(angle) * retreatDist),
                                    maid.blockPosition().getY(),
                                    (int) (maid.getZ() + Math.sin(angle) * retreatDist));
                            fleeStates.put(maidId, new FleeState(retreatPos, tick));
                        }

                        // 3.3 说一句 AI 对话
                        String suffix = EmotionData.getTendencyPromptSuffix(maid, ownerPlayer.getUUID());
                        String prompt = "主人突然靠这么近，你被吓坏了，只想快点躲远一点。" + suffix
                                + " 请用你自己的话惊慌地说一句你想远离主人的话，20字左右。";
                        MaidResponder.processBroadcast(ownerPlayer, Collections.singletonList(maid), prompt, false);
                    });
        }
    }

    public static void resetPanic(EntityMaid maid) {
        lastPanicTime.remove(maid.getUUID());
        fleeStates.remove(maid.getUUID());
    }
}