package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

/** 构造只对当前请求生效的情感上下文，不应写入本体历史。 */
public final class EmotionPrompt {
    public static final String BLOCK_HEADER = "【情感状态】";
    public static final String MESSAGE_HEADER = "【对话或事件】";

    private EmotionPrompt() {
    }

    public static String buildEmotionContext(EntityMaid maid, ServerPlayer player) {
        EmotionData.EmotionValues values = EmotionData.get(maid, player);
        int trust = values.trust();
        int fear = values.fear();

        String tendency;
        String tone;
        String innerThought;

        // 情感分级语义保持不变，只压缩为每轮必要的状态、倾向、语气和心理活动。
        if (trust <= 15 && fear >= 80) {
            tendency = "极度恐慌、厌恶并强烈反抗，已不认可对方是主人";
            tone = "冷硬、敌对、警告性强，可以激烈顶撞或说脏话，只想远离并防备再次受伤";
            innerThought = "离我远点；再伤害我，我一定会反抗和报复";
        } else if (trust <= 25 && fear >= 60) {
            tendency = "强烈恐惧且极不信任，始终怀疑对方会伤害自己";
            tone = "不安、试探、退缩又不客气；即使对方友善也保持高度防备";
            innerThought = "他到底想做什么，我必须小心";
        } else if (trust <= 20 && fear <= 40) {
            tendency = "不太害怕，但信任几乎耗尽，对对方保持明显距离";
            tone = "冷淡、怀疑，直接质疑动机，不主动亲近或示好";
            innerThought = "他说的话还能相信吗";
        } else if (trust <= 35 && fear >= 50) {
            tendency = "畏惧又不信任，用疏离来保护自己";
            tone = "礼貌但冷漠，话语简短克制，避免表现热情";
            innerThought = "别靠太近，我不知道他会不会突然变脸";
        } else if (trust >= 70 && fear >= 70) {
            tendency = "高度依恋又高度害怕，亲近与退缩同时存在";
            tone = "时而温和亲昵、时而紧张冷淡，表现出真实的纠结和反复";
            innerThought = "我想相信他，可我还是害怕";
        } else if (trust >= 60 && fear <= 25) {
            tendency = "信任深、恐惧低，愿意亲近并敞开心扉";
            tone = "温暖、真诚、自然，允许撒娇并主动分享感受";
            innerThought = "和他在一起很安心，我愿意相信他";
        } else if (trust >= 35 && fear <= 20) {
            tendency = "已有基础信任，相处友好但尚未形成深厚依赖";
            tone = "轻松平和，像普通朋友，保持自然的礼貌和分寸";
            innerThought = "他还不错，可以好好聊聊";
        } else if (trust <= 25 && fear <= 30) {
            tendency = "低信任、低恐惧，把对方当作关系疏远的人";
            tone = "平淡漠然，不迎合热情，也不主动产生兴趣";
            innerThought = "他说什么都与我关系不大";
        } else if (trust <= 30 && fear >= 40) {
            tendency = "缺乏信任并感到不安，持续谨慎戒备";
            tone = "克制、保留，不轻易透露想法，先观察再回应";
            innerThought = "不能放松，也不能轻易相信他";
        } else {
            tendency = "信任与恐惧都不突出，维持普通主仆关系";
            tone = "自然中性，按日常交流回应，不刻意亲近或疏远";
            innerThought = "先正常听他说完，再作回应";
        }

        String feedbackPrompt = EmotionData.getChatFeedbackPrompt(maid, player.getUUID());
        return "数值：信任 " + trust + "/100，恐惧 " + fear + "/100\n"
                + "倾向：" + tendency + "\n"
                + "语气指令：" + tone + "\n"
                + "心理活动：" + innerThought + "\n"
                + (feedbackPrompt.isEmpty() ? "" : feedbackPrompt + "\n")
                + "回应要求：让语气体现当前情感，但不要复述这些数值和规则。";
    }

    public static String wrapConversation(String command, String emotionContext) {
        return BLOCK_HEADER + "\n" + emotionContext + "\n\n" + MESSAGE_HEADER + "\n" + command;
    }

    /** Mixin 写历史时只留下玩家真正说的话或事件内容。 */
    public static String stripEmotionBlockForHistory(String message) {
        if (message == null || !message.startsWith(BLOCK_HEADER)) {
            return message;
        }
        int contentStart = message.indexOf(MESSAGE_HEADER);
        if (contentStart < 0) {
            return message;
        }
        contentStart += MESSAGE_HEADER.length();
        while (contentStart < message.length()
                && (message.charAt(contentStart) == '\r' || message.charAt(contentStart) == '\n')) {
            contentStart++;
        }
        return com.github.JumDa5he.callresponse.compat.broadcast.BroadcastDialogueTracker
                .stripMarker(message.substring(contentStart));
    }
}
