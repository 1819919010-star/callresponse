package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;

public class EmotionTool implements IFunctionCall<String> {

    @Override
    public String getId() {
        return "get_emotion";
    }

    @Override
    public String getDescription(EntityMaid maid) {
        return "获取当前女仆对玩家的信任值和恐惧值（0-100）。" +
                "信任值高表示亲近，恐惧值高表示害怕。" +
                "根据这些值调整你的回复语气：信任>恐惧时亲近，恐惧>信任时畏惧。";
    }

    @Override
    public Parameter addParameters(ObjectParameter root, EntityMaid maid) {
        // 不需要参数
        return root;
    }

    @Override
    public Codec<String> codec() {
        return Codec.STRING;
    }

    @Override
    public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) {
        // 始终添加此工具
        return true;
    }

    @Override
    public ToolResponse onToolCall(String result, EntityMaid maid) {
        // 获取主人（触发对话的玩家）
        var owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return new ToolResponse("信任值: 40, 恐惧值: 10, 情感倾向: 中立");
        }

        // 获取情感值
        var values = EmotionData.get(maid, player);
        var tendency = EmotionData.getTendency(maid, player);

        // 构建返回信息
        String response = String.format(
                "信任值: %d, 恐惧值: %d, 情感倾向: %s",
                values.trust(),
                values.fear(),
                tendency.name().toLowerCase()
        );

        return new ToolResponse(response);
    }
}