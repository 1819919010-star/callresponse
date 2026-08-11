package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.api.broadcast.BroadcastManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionPrompt;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
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
        debug(player, Component.literal(msg));
    }

    // 使用 Component 以避免在服务端时的问题
    // 救…我……
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

        BroadcastManager.call(serverPlayer, maids, lowerCmd);

        // ===== AI 对话 =====
        EntityMaid firstMaid = responders.get(0);
        MaidAIChatManager firstManager = firstMaid.getAiChatManager();

        String language = firstManager.getTTSLanguage();
        if (language == null || language.isBlank()) {
            language = AIConfig.TTS_LANGUAGE.get();
        }
        debug(player, "§e[调试] 使用语言: " + language);


        for (EntityMaid maid : responders) {
            MaidAIChatManager manager = maid.getAiChatManager();

            final EntityMaid finalMaid = maid;

            Component maidName = finalMaid.getName();
            debug(serverPlayer, Component.literal("§e[调试] 正在处理: ").append(maidName));

            try {
                // ===== 获取人设（如果有） =====
                Optional<CharacterSetting> settingOpt = manager.getSetting();
                String finalLanguage = language;
                String setting = settingOpt.map(s -> s.getSetting(finalMaid, finalLanguage))
                        .orElse("你是一个女仆，请友好地回复主人。");

                // ===== 获取情感上下文 =====
                String emotionContext = EmotionPrompt.buildEmotionContext(finalMaid, serverPlayer);

                // ===== 融合人设 + 情感作为用户消息前缀 =====
                String modifiedCommand = "【人设】\n" + setting + "\n\n【情感状态】\n" + emotionContext + "\n\n【玩家的指令】\n" + command;

                // ===== 调用原版 chat =====
                ChatClientInfo clientInfo = new ChatClientInfo(
                        serverPlayer.getName().getString(),
                        language,
                        Collections.emptyList()
                );
                manager.chat(modifiedCommand, clientInfo, serverPlayer);
                debug(serverPlayer,
                        Component.literal("§a[调试] ")
                                .append(maidName)
                                .append(Component.literal(" 的 chat() 调用完成（人设+情感已注入）"))
                );
            } catch (Exception e) {
                debug(serverPlayer,
                        Component.literal("§c[调试] ")
                                .append(maidName)
                                .append(Component.literal(" 异常: " + e.getMessage()))
                );                CallResponseMod.LOGGER.error("出现错误：", e);
            }
        }
    }

    private static List<EntityMaid> selectResponders(List<EntityMaid> maids, Player player, String command) {
        debug(player, "§e[调试] 强制所有女仆回应");
        return new ArrayList<>(maids);
    }
}