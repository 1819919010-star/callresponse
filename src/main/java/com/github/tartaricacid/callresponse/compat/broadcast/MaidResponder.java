package com.github.tartaricacid.callresponse.compat.broadcast;

import com.github.tartaricacid.callresponse.compat.broadcast.actions.*;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionDotingManager;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionPrompt;
import com.github.tartaricacid.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.CharacterSetting;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class MaidResponder {

    public static void debug(Player player, String msg) {
        boolean debugEnabled = BroadcastConfig.DEBUG_ENABLED.get();
        if (debugEnabled && player != null) {
            player.sendSystemMessage(Component.literal(msg));
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
                            debug(player, "§e[调试] " + maid.getCustomName() + " 溺爱模式，拒绝集合指令");
                        }
                    } else if (lowerCmd.contains("开饭") || lowerCmd.contains("拿食物")) {
                        if (!isDoting) {
                            WalkToOwnerAndTakeFoodAction.execute(maid, serverPlayer);
                        } else {
                            debug(player, "§e[调试] " + maid.getCustomName() + " 溺爱模式，拒绝拿食物指令");
                        }
                    } else if (lowerCmd.contains("打起来") || lowerCmd.contains("攻击") || lowerCmd.contains("打架") || lowerCmd.contains("决斗")) {
                        if (!isDoting) {
                            AttackOtherMaidAction.execute(maid, serverPlayer);
                        } else {
                            debug(player, "§e[调试] " + maid.getCustomName() + " 溺爱模式，拒绝攻击指令");
                        }
                    } else if (lowerCmd.contains("停战") || lowerCmd.contains("停止攻击")) {
                        StopAttackAction.execute(maid, serverPlayer);
                    } else if (lowerCmd.contains("站起来") || lowerCmd.contains("起立")) {
                        if (!isDoting) {
                            StandUpAction.execute(maid, serverPlayer);
                        } else {
                            debug(player, "§e[调试] " + maid.getCustomName() + " 溺爱模式，拒绝站起来指令");
                        }
                    } else if (lowerCmd.contains("坐下")) {
                        if (!isDoting) {
                            SitDownAction.execute(maid, serverPlayer);
                        } else {
                            debug(player, "§e[调试] " + maid.getCustomName() + " 溺爱模式，拒绝坐下指令");
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
        EntityMaid firstMaid = responders.get(0);
        MaidAIChatManager firstManager = firstMaid.getAiChatManager();
        if (firstManager == null) {
            debug(player, "§c无法获取女仆AI管理器");
            return;
        }

        String language = firstManager.getTTSLanguage();
        if (language == null || language.isBlank()) {
            language = AIConfig.TTS_LANGUAGE.get();
        }
        debug(player, "§e[调试] 使用语言: " + language);


        final ServerPlayer finalServerPlayer = serverPlayer;

        for (EntityMaid maid : responders) {
            MaidAIChatManager manager = maid.getAiChatManager();
            if (manager == null) continue;

            final EntityMaid finalMaid = maid;
            final MaidAIChatManager finalManager = manager;

            String maidName = finalMaid.getCustomName() != null ? finalMaid.getCustomName().getString() : "无名";
            debug(finalServerPlayer, "§e[调试] 正在处理: " + maidName);

            try {
                // ===== 获取人设（如果有） =====
                Optional<CharacterSetting> settingOpt = finalManager.getSetting();
                String finalLanguage = language;
                String setting = settingOpt.map(s -> s.getSetting(finalMaid, finalLanguage))
                        .orElse("你是一个女仆，请友好地回复主人。");

                // ===== 获取情感上下文 =====
                String emotionContext = EmotionPrompt.buildEmotionContext(finalMaid, finalServerPlayer);

                // ===== 融合人设 + 情感作为用户消息前缀 =====
                String modifiedCommand = "【人设】\n" + setting + "\n\n【情感状态】\n" + emotionContext + "\n\n【玩家的指令】\n" + command;

                // ===== 调用原版 chat =====
                ChatClientInfo clientInfo = new ChatClientInfo(
                        finalServerPlayer.getName().getString(),
                        language,
                        Collections.emptyList()
                );
                finalManager.chat(modifiedCommand, clientInfo, finalServerPlayer);
                debug(finalServerPlayer, "§a[调试] " + maidName + " 的 chat() 调用完成（人设+情感已注入）");

            } catch (Exception e) {
                debug(finalServerPlayer, "§c[调试] " + maidName + " 异常: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static List<EntityMaid> selectResponders(List<EntityMaid> maids, Player player, String command) {
        debug(player, "§e[调试] 强制所有女仆回应");
        return new ArrayList<>(maids);
    }
}