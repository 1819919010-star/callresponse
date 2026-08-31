package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.api.broadcast.BroadcastManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionPrompt;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MaidResponder {

    public static void debug(Player player, String msg) {
        debug(player, Component.literal(msg));
    }

    // 使用 Component 以避免在服务端时的问题
    public static void debug(Player player, Component msg) {
        boolean debugEnabled = BroadcastConfig.DEBUG_ENABLED.get();
        if (debugEnabled && player != null) {
            player.sendSystemMessage(msg);
        }
    }

    public static void processBroadcast(Player player, List<EntityMaid> maids, String command, boolean isPlayerCommand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            debug(player, "§c[调试] 玩家不是 ServerPlayer");
            return;
        }

        // 复制列表，避免不可变列表异常
        List<EntityMaid> maidList = new ArrayList<>(maids);
        maidList.removeIf(maid -> !maid.isTame() || maid.getOwner() == null
                || TalkEventManager.isParticipant(maid));
        if (maidList.isEmpty()) {
            debug(player, "§c[调试] 没有已驯服且有主人的女仆");
            return;
        }

        debug(player, "§e[调试] 进入广播处理，女仆数量: " + maidList.size());

        // 配置概率只约束玩家的 #全体 广播；内部事件指定的单只女仆不能再被二次随机吞掉。
        List<EntityMaid> responders = isPlayerCommand
                ? selectResponders(maidList, player, command)
                : new ArrayList<>(maidList);
        if (responders.isEmpty()) {
            debug(player, "§c[调试] 没有女仆通过筛选");
            return;
        }
        debug(player, "§e[调试] 实际回应女仆: " + responders.size());

        String lowerCmd = command.toLowerCase();

        if(isPlayerCommand)
            BroadcastManager.call(serverPlayer, maids, lowerCmd);

        // ===== AI 对话 =====
        for (EntityMaid maid : responders) {
            if (!DialogueApiLimiter.tryAcquire()) {
                debug(serverPlayer, "§e[调试] 已达到《呼应》每分钟 AI 调用限额，本轮剩余回应已跳过");
                break;
            }
            MaidAIChatManager manager = maid.getAiChatManager();
            Component maidName = maid.getName();
            debug(serverPlayer, Component.literal("§e[调试] 正在处理: ").append(maidName));

            try {
                if (isPlayerCommand) {
                    EmotionData.FeedbackKind feedback = EmotionData.applyChatFeedback(
                            maid, serverPlayer, command);
                    if (feedback != EmotionData.FeedbackKind.NONE) {
                        debug(serverPlayer, Component.literal("§e[调试] ")
                                .append(maidName)
                                .append(Component.literal(" 检测到聊天反馈: " + feedback)));
                    }
                }
                String emotionContext = EmotionPrompt.buildEmotionContext(maid, serverPlayer);
                String message = BroadcastDialogueTracker.mark(
                        buildConversationMessage(command, emotionContext), serverPlayer.getUUID());
                ChatClientInfo clientInfo = createClientInfo(serverPlayer, maid);

                // 完整交给本体 chat()：自定义人设、内置人设、自动生成人设、历史、工具和 TTS
                // 都由本体按正常单体聊天的顺序处理，广播层只补充情感上下文。
                manager.chat(message, clientInfo, serverPlayer);
                debug(serverPlayer,
                        Component.literal("§a[调试] ")
                                .append(maidName)
                                .append(Component.literal(" 的 chat() 调用完成（对话语言: "
                                        + clientInfo.language() + "，语音语言: "
                                        + manager.getTTSLanguage() + "）"))
                );
            } catch (Exception e) {
                debug(serverPlayer,
                        Component.literal("§c[调试] ")
                                .append(maidName)
                                .append(Component.literal(" 异常: " + e.getMessage()))
                );
                CallResponseMod.LOGGER.error("出现错误：", e);
            }
        }
    }

    /**
     * 广播发生在服务端，不能调用本体仅限客户端的 ChatClientInfo.fromMaid()。
     * 服务端保存了玩家客户端上报的语言，因此可以构造出与本体普通聊天等价的信息。
     */
    private static ChatClientInfo createClientInfo(ServerPlayer player, EntityMaid maid) {
        return new ChatClientInfo(
                player.getLanguage(),
                maid.getName().getString(),
                Collections.emptyList()
        );
    }

    /**
     * 人设不能放进用户消息；本体会把自定义/内置人设作为 system 消息自动加入。
     * 此处仅保留附属特有的情感状态和原始指令，避免同一人设被重复注入。
     */
    private static String buildConversationMessage(String command, String emotionContext) {
        return EmotionPrompt.wrapConversation(command, emotionContext);
    }

    private static List<EntityMaid> selectResponders(List<EntityMaid> maids, Player player, String command) {
        double baseChance = BroadcastConfig.BASE_CHANCE.get();
        double nameBonus = BroadcastConfig.NAME_MENTION_BONUS.get();
        int maxResponders = BroadcastConfig.MAX_RESPONDERS.get();
        String normalizedCommand = command.toLowerCase();
        List<ResponseCandidate> passed = new ArrayList<>();

        for (EntityMaid maid : maids) {
            boolean mentioned = isNameMentioned(maid, normalizedCommand);
            double chance = Math.min(1.0, baseChance + (mentioned ? nameBonus : 0.0));
            double roll = maid.getRandom().nextDouble();
            debug(player, Component.literal("§e[调试] 回应筛选 ")
                    .append(maid.getName())
                    .append(Component.literal(String.format(
                            "：点名=%s，概率=%.2f，掷骰=%.2f", mentioned, chance, roll))));
            if (roll < chance) {
                passed.add(new ResponseCandidate(maid, mentioned,
                        maid.distanceToSqr(player)));
            }
        }

        // 点名命中的女仆优先；同级按距离取最近者，避免多人同时调用 LLM。
        passed.sort(Comparator.comparing(ResponseCandidate::mentioned).reversed()
                .thenComparingDouble(ResponseCandidate::distanceSqr));
        List<EntityMaid> result = passed.stream()
                .limit(maxResponders)
                .map(ResponseCandidate::maid)
                .toList();
        debug(player, "§e[调试] 概率筛选通过: " + passed.size()
                + "，受 maxResponders 限制后: " + result.size());
        return new ArrayList<>(result);
    }

    private static boolean isNameMentioned(EntityMaid maid, String normalizedCommand) {
        String displayName = maid.getDisplayName().getString().trim().toLowerCase();
        if (!displayName.isEmpty() && normalizedCommand.contains(displayName)) {
            return true;
        }
        Component customName = maid.getCustomName();
        if (customName == null) {
            return false;
        }
        String ownerGivenName = customName.getString().trim().toLowerCase();
        return !ownerGivenName.isEmpty() && normalizedCommand.contains(ownerGivenName);
    }

    private record ResponseCandidate(EntityMaid maid, boolean mentioned, double distanceSqr) {
    }
}
