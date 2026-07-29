package com.github.tartaricacid.callresponse.compat.broadcast.tool;

import com.github.tartaricacid.callresponse.compat.broadcast.actions.AttackOtherMaidAction;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;

public class AttackMaidTool implements ITool<String> {
    @Override
    public String id() {
        return "attack_maid";
    }

    @Override
    public String summary(EntityMaid entityMaid) {
        return "让女仆攻击周围最近的其他女仆（无差别攻击）。当玩家说'打起来'、'打架'时调用。";
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
            return llmCallback.addToolResult("没有主人或主人不在线", "attack_maid");
        }
        AttackOtherMaidAction.execute(llmCallback.getMaid(), sp);
        return llmCallback.addToolResult("开始攻击", "attack_maid");
    }
}
