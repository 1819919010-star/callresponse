package com.github.JumDa5he.callresponse.compat.broadcast.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;

public class SitDownTool implements ITool<String> {
    @Override
    public String id() {
        return "sit_down";
    }

    @Override
    public String summary(EntityMaid entityMaid) {
        return "让女仆坐下。当玩家要求坐下时调用。";
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
        if (!callback.getMaid().isInSittingPose()) {
            callback.getMaid().setInSittingPose(true);
            return OneShotToolCall.finish(callback, toolCallId, id(), "已坐下。");
        }
        return OneShotToolCall.finish(callback, toolCallId, id(), "已经坐下了。");
    }
}
