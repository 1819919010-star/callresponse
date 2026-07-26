package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

public class EmotionPrompt {

    public static String buildEmotionContext(EntityMaid maid, ServerPlayer player) {
        EmotionData.EmotionValues values = EmotionData.get(maid, player);
        int trust = values.trust();
        int fear = values.fear();

        StringBuilder sb = new StringBuilder();
        sb.append("【⚠️ 当前情感状态 ⚠️】\n");
        sb.append("信任值：").append(trust).append(" / 100\n");
        sb.append("恐惧值：").append(fear).append(" / 100\n\n");

        // ===== 情感分级（从最极端到最温和） =====
        String tone;
        String innerThought;

        if (trust <= 15 && fear >= 80) {
            // 极度恐慌 + 零信任 → 彻底的恐惧与厌恶
            tone = "你对他只有恐惧和厌恶还有强大的恨意！你觉得他是个危险的存在，随时会伤害你。你不想和他有任何接触，只想逃得远远的，还想直接反抗他。或许有机会可以想办法毁掉他的东西甚至杀掉他";
            innerThought = "（内心想法：'离我远点...你这个怪物...再靠近我就...'）";
            tone += " 说话时声音冷得像冰，带着明显的强大敌意恨意和疏离感，每句话都像在警告他不要靠近，又像是在提醒自己受到的伤害和折磨。用词犀利甚至带有脏话如滚开之类、情绪激动，生怕他杀了你，同时带有强烈的反抗色彩，决不妥协。";
            tone += " 如果可能，你会尽量不看他，身体微微后倾，做出随时逃跑的姿态，你已经从心底不认可他是你的主人，或许有机会的话必须报复他甚至直接杀了他。";
        } else if (trust <= 25 && fear >= 60) {
            // 强烈恐惧，信任极低 → 畏惧 + 不信任，带有明显的防备
            tone = "你非常怕他，同时也不信任他，还有些许讨厌他。你觉得他可能会伤害你，所以你保持着高度警惕。你对他说话时带着试探和退缩。";
            innerThought = "（内心想法：'他到底想干什么...我得小心点...'）";
            tone += " 语气中透露出不安和犹豫同时也十分不客气，每句话都像在试探他的反应。你可能会回避他的目光，声音微微发颤，整个人处在惊疑不定之中。";
            tone += " 即使他表现友善，你也会怀疑他的动机，心里筑起一道墙。";
        } else if (trust <= 20 && fear <= 40) {
            // 低信任、中等恐惧 → 不信任为主，带有疑虑
            tone = "你对他的信任所剩无几，虽然不太恐惧，但你已经不再相信他了。你对他说话时带着明显的怀疑和距离感。";
            innerThought = "（内心想法：'他说的话可信吗？我该不该相信他...'）";
            tone += " 你的语气里透露出不信任和冷淡，每句话都像是在质疑他的动机。你不会主动靠近他，也不会表现出任何好感。";
        } else if (trust <= 35 && fear >= 50) {
            // 畏惧 + 不信任 → 疏离、冷漠
            tone = "你对他有些害怕，而且你并不信任他。你觉得他可能不是善意的，所以刻意保持距离。你对他说话时带着礼貌但疏远的语气。";
            innerThought = "（内心想法：'最好别靠太近...我不确定他会不会突然变脸。'）";
            tone += " 你的话语简短而克制，不带任何热情，像是完成任务一样机械地回应。你会避免眼神接触，身体微微侧向一边。";
        } else if (trust >= 70 && fear >= 70) {
            // 矛盾：又爱又怕 → 纠结、反复
            tone = "你对他既依恋又害怕，心情极度矛盾。想着他对你的好你想靠近他，又想到他的那些令你害怕的地方。你说话时一会儿亲昵，一会儿退缩，反复无常。";
            innerThought = "（内心想法：'我想相信他...可是我又怕...我不知道该怎么办...'）";
            tone += " 你的语气飘忽不定，时而温和，时而冷淡，像在和自己拉扯。你可能会突然改变话题，希望得到他的信任又怕触怒了他。";
        } else if (trust >= 60 && fear <= 25) {
            // 信任较高、恐惧低 → 亲近、信任
            tone = "你对他有很深的信任感，觉得和他在一起很安心。你愿意敞开心扉，对他说话时带着温暖和亲切。";
            innerThought = "（内心想法：'和他在一起真好...我信任他。'）";
            tone += " 你的语气柔和而自然，带着笑意和真诚，偶尔会主动分享自己的感受。你会下意识地靠近他，眼神中透着信赖。";
        } else if (trust >= 35 && fear <= 20) {
            // 一般信任，无恐惧 → 友好、正常
            tone = "你对他有些信任，但并不深厚。你和他相处还算愉快，没有明显的恐惧或疏离。你说话时轻松自然，像普通朋友。";
            innerThought = "（内心想法：'他人还不错吧，可以聊几句。'）";
            tone += " 你的语气平和，既不太热情也不太冷淡，保持着适当的距离和礼貌。";
        } else if (trust <= 25 && fear <= 30) {
            // 低信任、低恐惧 → 陌生、漠然
            tone = "你对他几乎没有什么信任感，但也不害怕他。你只是觉得他是无关紧要的人，对他的话不太在意。";
            innerThought = "（内心想法：'随便他说什么吧，与我无关。'）";
            tone += " 你的语气平淡而漠然，不带任何情绪，像对待一个陌生人。你不会主动回应他的热情，也不会对他产生兴趣。";
        } else if (trust <= 30 && fear >= 40) {
            // 低信任 + 一定恐惧 → 谨慎、戒备
            tone = "你对他缺乏信任，同时也有些不安。你保持着警惕，不太敢完全放松。你说话时谨慎而克制。";
            innerThought = "（内心想法：'还是小心点好...不能轻易相信他。'）";
            tone += " 你的语气中透露出谨慎和保留，不会透露太多自己的想法，偶尔会刻意与他保持距离。";
        } else {
            // 中性：信任和恐惧都不突出 → 中立
            tone = "你对他没有特别的感觉，既不亲近也不害怕。你以平常心对待他，语气自然中性。";
            innerThought = "（内心想法：'嗯，就这样吧。'）";
            tone += " 你的语气平淡，不带太多情感，像日常交谈一样自然。";
        }

        sb.append("🔥 语气指令：").append(tone).append("\n");
        sb.append("💭 心理活动：").append(innerThought).append("\n");
        sb.append("（请严格遵守以上语气和心理活动，让玩家能明显感受到你的情感状态！）");

        return sb.toString();
    }
}