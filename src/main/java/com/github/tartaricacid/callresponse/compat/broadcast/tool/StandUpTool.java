package com.github.tartaricacid.callresponse.compat.broadcast.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;

public class StandUpTool implements ITool<String> {
    @Override
    public String id() {
        return "stand_up";
    }

    @Override
    public String summary(EntityMaid entityMaid) {
        return "让女仆站起来（如果坐着）。当玩家说'站起来'时调用。";
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
        if (isSitting(llmCallback.getMaid())) {
            standUp(llmCallback.getMaid());
            return llmCallback.addToolResult("已站起来", "stand_up");
        }
        return llmCallback.addToolResult("已经站着了", "stand_up");
    }

    private static boolean isSitting(EntityMaid maid) {
        return maid.isInSittingPose();
    }

    private static void standUp(EntityMaid maid) {
        maid.setInSittingPose(false);
    }
}
