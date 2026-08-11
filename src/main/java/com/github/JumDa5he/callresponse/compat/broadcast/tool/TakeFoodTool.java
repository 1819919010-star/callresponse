package com.github.JumDa5he.callresponse.compat.broadcast.tool;

import com.github.JumDa5he.callresponse.compat.broadcast.actions.WalkToOwnerAndTakeFoodAction;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;

public class TakeFoodTool implements ITool<String> {
    @Override
    public String id() {
        return "take_food";
    }

    @Override
    public String summary(EntityMaid entityMaid) {
        return "让女仆走到主人身边并从主人手中取一个食物（如果主人手里有食物）。当玩家说'开饭'、'拿食物'、'喂我'时调用。";
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
            return llmCallback.addToolResult("没有主人或主人不在线", "take_food");
        }
        WalkToOwnerAndTakeFoodAction.execute(llmCallback.getMaid(), sp);
        return llmCallback.addToolResult("正在前往主人取食物", "take_food");
    }
}
