package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.broadcast.tool.OneShotToolCall;
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
        return "获取当前女仆对玩家的信任值和恐惧值（0 到 100），并据此调整回复语气。";
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
    public LLMCallback onCall(String toolCallId, String arguments, LLMCallback callback) {
        if (!OneShotToolCall.claim(callback, id())) {
            return OneShotToolCall.alreadyUsed(callback, toolCallId, id());
        }
        var owner = callback.getMaid().getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return OneShotToolCall.finish(callback, toolCallId, id(), "信任值：40，恐惧值：10，情感倾向：中立");
        }
        var values = EmotionData.get(callback.getMaid(), player);
        var tendency = EmotionData.getTendency(callback.getMaid(), player);
        String response = String.format(
                "信任值：%d，恐惧值：%d，情感倾向：%s",
                values.trust(), values.fear(), tendency.name().toLowerCase()
        );
        return OneShotToolCall.finish(callback, toolCallId, id(), response);
    }
}
