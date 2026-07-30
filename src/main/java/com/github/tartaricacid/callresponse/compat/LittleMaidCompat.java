package com.github.tartaricacid.callresponse.compat;

import com.github.tartaricacid.callresponse.compat.brain.CustomExtraMaidBrain;
import com.github.tartaricacid.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.tartaricacid.callresponse.compat.broadcast.BroadcastTools;
import com.github.tartaricacid.callresponse.compat.broadcast.ChatEventListener;
import com.github.tartaricacid.callresponse.compat.emotion.*;
import com.github.tartaricacid.callresponse.compat.hunger.CustomCakeEdible;
import com.github.tartaricacid.callresponse.compat.hunger.HungerManager;
import com.github.tartaricacid.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.tartaricacid.callresponse.compat.task.LazyMaidTask;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;


import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.util.List;

@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {
    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new CustomExtraMaidBrain());
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new LazyMaidTask());
    }

    @Override
    public void registerAITool(ToolRegister register) {
        BroadcastTools.register(register);
    }
}