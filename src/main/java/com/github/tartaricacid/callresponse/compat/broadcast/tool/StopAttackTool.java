package com.github.tartaricacid.callresponse.compat.broadcast.tool;

import com.github.tartaricacid.callresponse.compat.broadcast.actions.StopAttackAction;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;

public class StopAttackTool implements ITool<String> {
    @Override
    public String id() {
        return "stop_attack";
    }

    @Override
    public String summary(EntityMaid entityMaid) {
        return "让女仆停止攻击当前目标。当玩家说'停战'、'停止攻击'时调用。";
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
        var owner = llmCallback.getMaid().getOwner();
        if (!(owner instanceof ServerPlayer sp)) {
            return llmCallback.addToolResult("没有主人或主人不在线", "stop_attack");
        }
        StopAttackAction.execute(llmCallback.getMaid(), sp);
        return llmCallback.addToolResult("已停战", "stop_attack");
    }
}
