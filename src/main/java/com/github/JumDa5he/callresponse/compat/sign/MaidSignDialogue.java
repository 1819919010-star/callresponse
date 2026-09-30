package com.github.JumDa5he.callresponse.compat.sign;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 玩家改完示众牌后，让女仆就“自己被挂上牌子游街示众”这件事说一句。
 * <p>走的是和 NPC 事件同一条通道，人设、气泡、TTS、情感上下文都由本体和广播层处理。
 */
public final class MaidSignDialogue {
    private MaidSignDialogue() {
    }

    public static void reactToParade(ServerPlayer player, EntityMaid maid, String signText) {
        if (signText.isBlank() || !BroadcastConfig.NPC_EVENT_AI_REPLY_ENABLED.get()) {
            return;
        }
        String prompt = "这是一次女仆日常事件。事件：主人把一块写着\"%s\"的告示牌挂到了你身上，正拉着你游街示众。"
                .formatted(signText)
                + "结合你当前的人格、信任与恐惧状态自然回应主人；"
                + "不超过30字，不提系统、数值或AI，不执行任何动作指令。";
        MaidResponder.processBroadcast(player, List.of(maid), prompt, false);
    }
}
