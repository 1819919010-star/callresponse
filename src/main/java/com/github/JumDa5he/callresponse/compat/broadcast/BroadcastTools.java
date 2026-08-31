package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.JumDa5he.callresponse.compat.broadcast.tool.*;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionTool;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;

public class BroadcastTools {
    public static void register(ToolRegister register) {
        register.register(new EmotionTool());
        register.register(new AttackMaidTool());
        register.register(new SitDownTool());
        register.register(new StandUpTool());
        register.register(new StopAttackTool());
        register.register(new TakeFoodTool());
    }
}