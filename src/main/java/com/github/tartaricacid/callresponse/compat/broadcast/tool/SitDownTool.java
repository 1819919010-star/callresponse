package com.github.tartaricacid.callresponse.compat.broadcast.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;

public class SitDownTool implements ITool<String> {
    private static boolean isSitting(EntityMaid maid) {
        return maid.isInSittingPose();
    }

    private static void standUp(EntityMaid maid) {
        maid.setInSittingPose(false);
    }

    private static void sitDown(EntityMaid maid) {
        maid.setInSittingPose(true);
    }

    @Override
    public String id() {
        return "sit_down";
    }

    @Override
    public String summary(EntityMaid entityMaid) {
        return "让女仆坐下。当玩家说'坐下'时调用。";
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
        if (!isSitting(llmCallback.getMaid())) {
            sitDown(llmCallback.getMaid());
            return llmCallback.addToolResult("已坐下", "sit_down");
        }
        return llmCallback.addToolResult("已经坐下了", "sit_down");
    }
}
