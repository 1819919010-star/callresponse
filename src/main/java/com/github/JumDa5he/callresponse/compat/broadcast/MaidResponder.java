package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.actions.*;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager;
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

        // 只有玩家指令才检测动作关键词
        if (isPlayerCommand) {
            boolean isAction = lowerCmd.contains("集合") || lowerCmd.contains("过来") ||
                    lowerCmd.contains("站起来") || lowerCmd.contains("坐下") ||
                    lowerCmd.contains("起立") || lowerCmd.contains("开饭") ||
                    lowerCmd.contains("拿食物") ||
                    lowerCmd.contains("打起来") || lowerCmd.contains("攻击") ||
                    lowerCmd.contains("打架") || lowerCmd.contains("决斗") ||
                    lowerCmd.contains("停战") || lowerCmd.contains("停止攻击");

            if (isAction) {
                debug(player, "§e[调试] 检测到动作指令");

                for (EntityMaid maid : responders) {
                    boolean isDoting = EmotionDotingManager.isDoting(maid, serverPlayer);

                    // 谈话成员可以在近距离回应主人，但外部指令不能改变围坐、寻路或工作。
                    if (TalkEventManager.suppressBroadcastAction(maid, serverPlayer)) {
                        debug(player, Component.translatable("message.callresponse.debug.prefix").append(maid.getName())
                                .append(Component.translatable("message.callresponse.debug.talking_in_place")));
                        continue;
                    }

                    if (lowerCmd.contains("集合") || lowerCmd.contains("过来")) {
                        if (!isDoting) {
                            WalkToOwnerAndSitAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.translatable("message.callresponse.debug.prefix")
                                            .append(maid.getName())
                                            .append(Component.translatable("message.callresponse.debug.doting_refuse_gather"))
                            );
                        }
                    } else if (lowerCmd.contains("开饭") || lowerCmd.contains("拿食物")) {
                        if (!isDoting) {
                            WalkToOwnerAndTakeFoodAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.translatable("message.callresponse.debug.prefix")
                                            .append(maid.getName())
                                            .append(Component.translatable("message.callresponse.debug.doting_refuse_food"))
                            );
                        }
                    } else if (lowerCmd.contains("打起来") || lowerCmd.contains("攻击") || lowerCmd.contains("打架") || lowerCmd.contains("决斗")) {
                        if (!isDoting) {
                            AttackOtherMaidAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.translatable("message.callresponse.debug.prefix")
                                            .append(maid.getName())
                                            .append(Component.translatable("message.callresponse.debug.doting_refuse_attack"))
                            );
                        }
                    } else if (lowerCmd.contains("停战") || lowerCmd.contains("停止攻击")) {
                        StopAttackAction.execute(maid, serverPlayer);
                    } else if (lowerCmd.contains("站起来") || lowerCmd.contains("起立")) {
                        if (!isDoting) {
                            StandUpAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.translatable("message.callresponse.debug.prefix")
                                            .append(maid.getName())
                                            .append(Component.translatable("message.callresponse.debug.doting_refuse_stand"))
                            );
                        }
                    } else if (lowerCmd.contains("坐下")) {
                        if (!isDoting) {
                            SitDownAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.translatable("message.callresponse.debug.prefix")
                                            .append(maid.getName())
                                            .append(Component.translatable("message.callresponse.debug.doting_refuse_sit"))
                            );
                        }
                    }
                }

                debug(player, "§e[调试] 动作指令执行完毕，进入 AI 对话");
            } else {
                debug(player, "§e[调试] 未检测到动作关键词，进入对话模式");
            }
        } else {
            debug(player, "§e[调试] 内部对话（非玩家指令），跳过动作检测");
        }

        // ===== AI 对话 =====
        for (EntityMaid maid : responders) {
            if (!DialogueApiLimiter.tryAcquire()) {
                debug(serverPlayer, "§e[调试] 已达到《呼应》每分钟 AI 调用限额，本轮剩余回应已跳过");
                break;
            }
            MaidAIChatManager manager = maid.getAiChatManager();
            Component maidName = maid.getName();
            debug(serverPlayer, Component.translatable("message.callresponse.debug.processing", maidName));

            try {
                if (isPlayerCommand) {
                    EmotionData.FeedbackKind feedback = EmotionData.applyChatFeedback(
                            maid, serverPlayer, command);
                    if (feedback != EmotionData.FeedbackKind.NONE) {
                        debug(serverPlayer, Component.translatable("message.callresponse.debug.prefix")
                                .append(maidName)
                                .append(Component.translatable("message.callresponse.debug.feedback", feedback)));
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
                        Component.translatable("message.callresponse.debug.success_prefix")
                                .append(maidName)
                                .append(Component.translatable("message.callresponse.debug.chat_complete", clientInfo.language(), manager.getTTSLanguage()))
                );
            } catch (Exception e) {
                debug(serverPlayer,
                        Component.translatable("message.callresponse.debug.error_prefix")
                                .append(maidName)
                                .append(Component.translatable("message.callresponse.debug.error", e.getMessage()))
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
            debug(player, Component.translatable("message.callresponse.debug.response_filter_prefix")
                    .append(maid.getName())
                    .append(Component.translatable("message.callresponse.debug.response_filter_values", mentioned, String.format("%.2f", chance), String.format("%.2f", roll))));
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
