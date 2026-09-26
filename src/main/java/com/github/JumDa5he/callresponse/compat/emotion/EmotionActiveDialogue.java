package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.api.event.emotion.MaidEmotionEvent;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionActiveDialogue {

    // ===== 配置参数 =====
    private static final int CHECK_INTERVAL_TICKS = 600;      // 每 30 秒检查一次
    private static final double BASE_TRIGGER_CHANCE = 0.01;    // 主动触发概率 1%
    private static final double MAX_TRIGGER_CHANCE = 0.06;     // 最大触发概率 6%
    private static final int ACTIVE_COOLDOWN_TICKS = 2400;     // 主动触发冷却 120 秒
    private static final int INTERACT_COOLDOWN_TICKS = 600;    // 交互触发冷却 30 秒

    private static final Map<UUID, Long> lastActiveTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastInteractTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<String>> recentVisibleLines = new ConcurrentHashMap<>();
    private static final Map<DialogueKey, Integer> silentTriggerCounts = new ConcurrentHashMap<>();

    // ===== 主动触发：定时检查 =====
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

        long currentTick = event.getServer().getTickCount();

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(16))
                    .forEach(maid -> {
                        // ✅ 必须已驯服且有主人
                        if (!maid.isTame() || maid.getOwner() == null
                                || !player.getUUID().equals(maid.getOwnerUUID())) return;

                        DialogueKey dialogueKey = new DialogueKey(maid.getUUID(), player.getUUID());
                        if (silentTriggerCounts.getOrDefault(dialogueKey, 0) >= 2) {
                            return;
                        }

                        UUID maidId = maid.getUUID();
                        Long lastTime = lastActiveTime.get(maidId);
                        if (lastTime != null && currentTick - lastTime < ACTIVE_COOLDOWN_TICKS) {
                            return;
                        }

                        EmotionData.EmotionValues values = EmotionData.get(maid, player);
                        int intensity = Math.abs(values.trust() - 50) + Math.abs(values.fear() - 50);
                        double chance = BASE_TRIGGER_CHANCE + (intensity / 100.0) * 0.05;
                        if (values.trust() <= 15 || values.fear() >= 85) {
                            chance += 0.015;
                        }
                        chance = Math.min(chance, MAX_TRIGGER_CHANCE);

                        if (maid.getRandom().nextDouble() < chance) {
                            triggerAIDialogue(maid, player, "主动表达情绪");
                            lastActiveTime.put(maidId, currentTick);
                            silentTriggerCounts.merge(dialogueKey, 1, Integer::sum);
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
        if (lastTime != null && currentTick - lastTime < INTERACT_COOLDOWN_TICKS) {
            return;
        }

        if (maid.getRandom().nextDouble() < 0.10) {
            triggerAIDialogue(maid, player, "交互触发情绪表达");
            lastInteractTime.put(maidId, currentTick);
        }
    }

    /** 玩家一旦重新发言，开始新的静默轮次。 */
    public static void onPlayerSpoke(ServerPlayer player) {
        silentTriggerCounts.keySet().removeIf(key -> key.playerId.equals(player.getUUID()));
    }

    /** 由统一 LLM 回调记录玩家真正看见的最近两条女仆发言。 */
    public static void recordVisibleSpeech(EntityMaid maid, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Deque<String> lines = recentVisibleLines.computeIfAbsent(maid.getUUID(), ignored -> new ArrayDeque<>());
        synchronized (lines) {
            lines.addLast(text.trim());
            while (lines.size() > 2) {
                lines.removeFirst();
            }
        }
    }

    private static List<String> getRecentVisibleSpeech(EntityMaid maid) {
        Deque<String> lines = recentVisibleLines.get(maid.getUUID());
        if (lines == null) {
            return List.of();
        }
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    // ===== 实际触发 AI 对话 =====
    private static void triggerAIDialogue(EntityMaid maid, ServerPlayer player, String reason) {
        if(NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidDialogueEvent(maid, reason)).isCanceled())return;
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
        List<String> recent = getRecentVisibleSpeech(maid);
        if (!recent.isEmpty()) {
            command += " 你刚才说过：『" + String.join("』『", recent)
                    + "』。这次换一个角度和新的说法，不要重复相似内容。";
        }
        command += " 用一到两句自然口语表达，控制在 12 到 45 个字。";
        MaidResponder.processBroadcast(player, Collections.singletonList(maid), command, false);
    }

    private record DialogueKey(UUID maidId, UUID playerId) {
    }
}
