package com.github.tartaricacid.callresponse.compat.broadcast;

import com.github.tartaricacid.callresponse.compat.broadcast.actions.AttackOtherMaidAction;
import com.github.tartaricacid.callresponse.compat.broadcast.actions.StopAttackAction;
import com.github.tartaricacid.callresponse.compat.broadcast.actions.WalkToOwnerAndTakeFoodAction;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionTool;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.FunctionCallRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.IFunctionCall;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.response.ToolResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class BroadcastTools {

    private static boolean isSitting(EntityMaid maid) {
        return maid.isInSittingPose();
    }

    private static void standUp(EntityMaid maid) {
        maid.setInSittingPose(false);
    }

    private static void sitDown(EntityMaid maid) {
        maid.setInSittingPose(true);
    }

    public static void register() {
        try {
            Field field = FunctionCallRegister.class.getDeclaredField("FUNCTION_CALLS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, IFunctionCall<?>> calls = (Map<String, IFunctionCall<?>>) field.get(null);

            Map<String, IFunctionCall<?>> newCalls = new HashMap<>(calls);

            // stand_up
            newCalls.put("stand_up", new IFunctionCall<String>() {
                @Override
                public String getId() { return "stand_up"; }

                @Override
                public String getDescription(EntityMaid maid) {
                    return "让女仆站起来（如果坐着）。当玩家说'站起来'时调用。";
                }

                @Override
                public Parameter addParameters(ObjectParameter root, EntityMaid maid) { return root; }

                @Override
                public Codec<String> codec() { return Codec.STRING; }

                @Override
                public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) { return true; }

                @Override
                public ToolResponse onToolCall(String result, EntityMaid maid) {
                    if (isSitting(maid)) {
                        standUp(maid);
                        return new ToolResponse("已站起来");
                    }
                    return new ToolResponse("已经站着了");
                }
            });

            // sit_down
            newCalls.put("sit_down", new IFunctionCall<String>() {
                @Override
                public String getId() { return "sit_down"; }

                @Override
                public String getDescription(EntityMaid maid) {
                    return "让女仆坐下。当玩家说'坐下'时调用。";
                }

                @Override
                public Parameter addParameters(ObjectParameter root, EntityMaid maid) { return root; }

                @Override
                public Codec<String> codec() { return Codec.STRING; }

                @Override
                public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) { return true; }

                @Override
                public ToolResponse onToolCall(String result, EntityMaid maid) {
                    if (!isSitting(maid)) {
                        sitDown(maid);
                        return new ToolResponse("已坐下");
                    }
                    return new ToolResponse("已经坐下了");
                }
            });

            // take_food
            newCalls.put("take_food", new IFunctionCall<String>() {
                @Override
                public String getId() { return "take_food"; }

                @Override
                public String getDescription(EntityMaid maid) {
                    return "让女仆走到主人身边并从主人手中取一个食物（如果主人手里有食物）。当玩家说'开饭'、'拿食物'、'喂我'时调用。";
                }

                @Override
                public Parameter addParameters(ObjectParameter root, EntityMaid maid) { return root; }

                @Override
                public Codec<String> codec() { return Codec.STRING; }

                @Override
                public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) { return true; }

                @Override
                public ToolResponse onToolCall(String result, EntityMaid maid) {
                    var owner = maid.getOwner();
                    if (!(owner instanceof ServerPlayer sp)) {
                        return new ToolResponse("没有主人或主人不在线");
                    }
                    WalkToOwnerAndTakeFoodAction.execute(maid, sp);
                    return new ToolResponse("正在前往主人取食物");
                }
            });

            // attack_maid
            newCalls.put("attack_maid", new IFunctionCall<String>() {
                @Override
                public String getId() { return "attack_maid"; }

                @Override
                public String getDescription(EntityMaid maid) {
                    return "让女仆攻击周围最近的其他女仆（无差别攻击）。当玩家说'打起来'、'打架'时调用。";
                }

                @Override
                public Parameter addParameters(ObjectParameter root, EntityMaid maid) { return root; }

                @Override
                public Codec<String> codec() { return Codec.STRING; }

                @Override
                public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) { return true; }

                @Override
                public ToolResponse onToolCall(String result, EntityMaid maid) {
                    var owner = maid.getOwner();
                    if (!(owner instanceof ServerPlayer sp)) {
                        return new ToolResponse("没有主人或主人不在线");
                    }
                    AttackOtherMaidAction.execute(maid, sp);
                    return new ToolResponse("开始攻击");
                }
            });

            // stop_attack
            newCalls.put("stop_attack", new IFunctionCall<String>() {
                @Override
                public String getId() { return "stop_attack"; }

                @Override
                public String getDescription(EntityMaid maid) {
                    return "让女仆停止攻击当前目标。当玩家说'停战'、'停止攻击'时调用。";
                }

                @Override
                public Parameter addParameters(ObjectParameter root, EntityMaid maid) { return root; }

                @Override
                public Codec<String> codec() { return Codec.STRING; }

                @Override
                public boolean addToChatCompletion(EntityMaid maid, ChatCompletion chatCompletion) { return true; }

                @Override
                public ToolResponse onToolCall(String result, EntityMaid maid) {
                    var owner = maid.getOwner();
                    if (!(owner instanceof ServerPlayer sp)) {
                        return new ToolResponse("没有主人或主人不在线");
                    }
                    StopAttackAction.execute(maid, sp);
                    return new ToolResponse("已停战");
                }
            });

            // get_emotion
            newCalls.put("get_emotion", new EmotionTool());

            field.set(null, newCalls);
            System.out.println("[广播工具] 注册成功（含情感工具）");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}