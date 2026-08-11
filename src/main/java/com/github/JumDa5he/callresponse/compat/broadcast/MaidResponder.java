package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.actions.*;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionPrompt;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
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
        maidList.removeIf(maid -> !maid.isTame() || maid.getOwner() == null);
        if (maidList.isEmpty()) {
            debug(player, "§c[调试] 没有已驯服且有主人的女仆");
            return;
        }

        debug(player, "§e[调试] 进入广播处理，女仆数量: " + maidList.size());

        List<EntityMaid> responders = selectResponders(maidList, player, command);
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

                    if (lowerCmd.contains("集合") || lowerCmd.contains("过来")) {
                        if (!isDoting) {
                            WalkToOwnerAndSitAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.literal("§e[调试] ")
                                            .append(maid.getName())
                                            .append(Component.literal(" 溺爱模式，拒绝集合指令"))
                            );
                        }
                    } else if (lowerCmd.contains("开饭") || lowerCmd.contains("拿食物")) {
                        if (!isDoting) {
                            WalkToOwnerAndTakeFoodAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.literal("§e[调试] ")
                                            .append(maid.getName())
                                            .append(Component.literal(" 溺爱模式，拒绝拿食物指令"))
                            );
                        }
                    } else if (lowerCmd.contains("打起来") || lowerCmd.contains("攻击") || lowerCmd.contains("打架") || lowerCmd.contains("决斗")) {
                        if (!isDoting) {
                            AttackOtherMaidAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.literal("§e[调试] ")
                                            .append(maid.getName())
                                            .append(Component.literal(" 溺爱模式，拒绝攻击指令"))
                            );
                        }
                    } else if (lowerCmd.contains("停战") || lowerCmd.contains("停止攻击")) {
                        StopAttackAction.execute(maid, serverPlayer);
                    } else if (lowerCmd.contains("站起来") || lowerCmd.contains("起立")) {
                        if (!isDoting) {
                            StandUpAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.literal("§e[调试] ")
                                            .append(maid.getName())
                                            .append(Component.literal(" 溺爱模式，拒绝站起来指令"))
                            );
                        }
                    } else if (lowerCmd.contains("坐下")) {
                        if (!isDoting) {
                            SitDownAction.execute(maid, serverPlayer);
                        } else {
                            debug(player,
                                    Component.literal("§e[调试] ")
                                            .append(maid.getName())
                                            .append(Component.literal(" 溺爱模式，拒绝坐下指令"))
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
            MaidAIChatManager manager = maid.getAiChatManager();
            Component maidName = maid.getName();
            debug(serverPlayer, Component.literal("§e[调试] 正在处理: ").append(maidName));

            try {
                String emotionContext = EmotionPrompt.buildEmotionContext(maid, serverPlayer);
                String message = buildConversationMessage(command, emotionContext);
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
        return "【情感状态】\n" + emotionContext + "\n\n【对话或事件】\n" + command;
    }

    private static List<EntityMaid> selectResponders(List<EntityMaid> maids, Player player, String command) {
        debug(player, "§e[调试] 强制所有女仆回应");
        return new ArrayList<>(maids);
    }
}
