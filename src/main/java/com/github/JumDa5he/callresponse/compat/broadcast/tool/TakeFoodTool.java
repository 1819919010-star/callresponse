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
        return "让女仆走到主人身边，从主人手中取得一个食物。当玩家要求开饭、拿食物或喂食时调用。";
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
            return OneShotToolCall.finish(callback, toolCallId, id(), "没有可用的在线主人，无法取食物。");
        }
        WalkToOwnerAndTakeFoodAction.execute(callback.getMaid(), player);
        return OneShotToolCall.finish(callback, toolCallId, id(), "正在前往主人处取食物。");
    }
}
