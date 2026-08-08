package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;

public class EmotionTool implements ITool<String> {

    @Override
    public String id() {
        return "get_emotion";
    }

    @Override
    public String summary(EntityMaid entityMaid) {
        return "获取当前女仆对玩家的信任值和恐惧值（0-100）。" +
                "信任值高表示亲近，恐惧值高表示害怕。" +
                "根据这些值调整你的回复语气：信任>恐惧时亲近，恐惧>信任时畏惧。";
    }

    @Override
    public Parameter parameters(ObjectParameter objectParameter, EntityMaid entityMaid) {
        return objectParameter;
    }

    @Override
    public Codec<String> codec() {
        return Codec.STRING;
    }

    @Override
    public LLMCallback onCall(String s, String s2, LLMCallback llmCallback) {
        // 获取主人（触发对话的玩家）
        var owner = llmCallback.getMaid().getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return llmCallback.addToolResult("信任值: 40, 恐惧值: 10, 情感倾向: 中立", "get_emotion");
        }

        // 获取情感值
        var values = EmotionData.get(llmCallback.getMaid(), player);
        var tendency = EmotionData.getTendency(llmCallback.getMaid(), player);

        // 构建返回信息
        String response = String.format(
                "信任值: %d, 恐惧值: %d, 情感倾向: %s",
                values.trust(),
                values.fear(),
                tendency.name().toLowerCase()
        );

        return llmCallback.addToolResult(response, "get_emotion");
    }
}