package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionActiveDialogue {

    // ===== 配置参数 =====
    private static final int CHECK_INTERVAL_TICKS = 600;      // 每 30 秒检查一次
    private static final double BASE_TRIGGER_CHANCE = 0.01;    // 主动触发概率 1%
    private static final double MAX_TRIGGER_CHANCE = 0.04;     // 最大触发概率 4%
    private static final int ACTIVE_COOLDOWN_TICKS = 2400;     // 主动触发冷却 120 秒
    private static final int INTERACT_COOLDOWN_TICKS = 600;    // 交互触发冷却 30 秒

    private static final Map<UUID, Long> lastActiveTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastInteractTime = new ConcurrentHashMap<>();

    // ===== 主动触发：定时检查 =====
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

        long currentTick = event.getServer().getTickCount();

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(16))
                    .forEach(maid -> {
                        // ✅ 必须已驯服且有主人
                        if (!maid.isTame() || maid.getOwner() == null) return;

                        UUID maidId = maid.getUUID();
                        Long lastTime = lastActiveTime.get(maidId);
                        if (lastTime != null && currentTick - lastTime < ACTIVE_COOLDOWN_TICKS) {
                            return;
                        }

                        EmotionData.EmotionValues values = EmotionData.get(maid, player);
                        int intensity = Math.abs(values.trust() - 50) + Math.abs(values.fear() - 50);
                        double chance = BASE_TRIGGER_CHANCE + (intensity / 200.0) * 0.03;
                        chance = Math.min(chance, MAX_TRIGGER_CHANCE);

                        if (maid.getRandom().nextDouble() < chance) {
                            triggerAIDialogue(maid, player, "主动表达情绪");
                            lastActiveTime.put(maidId, currentTick);
                        }
                    });
        }
    }

    // ===== 交互触发 =====
    public static void tryInteractDialogue(EntityMaid maid, ServerPlayer player) {
        // ✅ 必须已驯服且有主人
        if (!maid.isTame() || maid.getOwner() == null) return;

        long currentTick = maid.level().getGameTime();
        UUID maidId = maid.getUUID();

        Long lastTime = lastInteractTime.get(maidId);
        // ★ 30秒冷却（INTERACT_COOLDOWN_TICKS = 600 ticks）
        if (lastTime != null && currentTick - lastTime < INTERACT_COOLDOWN_TICKS) {
            return;
        }

        if (maid.getRandom().nextDouble() < 0.10) {
            triggerAIDialogue(maid, player, "交互触发情绪表达");
            lastInteractTime.put(maidId, currentTick);
        }
    }

    // ===== 实际触发 AI 对话 =====
    private static void triggerAIDialogue(EntityMaid maid, ServerPlayer player, String reason) {
        EmotionData.EmotionTendency tendency = EmotionData.getTendency(maid, player);
        String suffix = EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
        String command = switch (tendency) {
            case BOND -> "你非常信赖和依赖主人，和他在一起时内心充满温暖。请主动对主人说一句亲昵的话表达你此刻的心情。" + suffix;
            case FRIENDLY -> "你对主人充满好感，觉得他是个可靠的人。请主动对主人说一句友好的话表达你此刻的心情。" + suffix;
            case STRANGER -> "你和主人还不太熟悉，你保持着一丝礼貌的距离。请主动对主人说一句客气的话。" + suffix;
            case FEARFUL -> "你有点害怕主人，在他面前总是小心翼翼的。请主动对主人说一句紧张的话。" + suffix;
            case TERRIFIED -> "你非常害怕主人，恨不得躲起来。请用颤抖的语气对主人说一句卑微讨好的话。" + suffix;
            case CONFLICTED -> "你对主人又爱又怕，内心非常矛盾。请主动对主人说一句混乱的话表达你纠结的心情。" + suffix;
            case NEUTRAL -> "你和主人维持着普通的主仆关系。请主动对主人说一句日常的话。" + suffix;
        };
        command += " 不超过 30 个字。";
        MaidResponder.processBroadcast(player, Collections.singletonList(maid), command, false);
    }
}