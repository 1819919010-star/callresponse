package com.github.tartaricacid.callresponse.compat;

import com.github.tartaricacid.callresponse.compat.brain.CustomExtraMaidBrain;
import com.github.tartaricacid.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.tartaricacid.callresponse.compat.broadcast.ChatEventListener;
import com.github.tartaricacid.callresponse.compat.emotion.*;
import com.github.tartaricacid.callresponse.compat.hunger.HungerManager;
import com.github.tartaricacid.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.tartaricacid.callresponse.compat.task.LazyMaidTask;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.neoforged.neoforge.common.NeoForge;

@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {
    public LittleMaidCompat() {
        // 注册所有事件监听
        NeoForge.EVENT_BUS.register(new EmotionEventListener());
        NeoForge.EVENT_BUS.register(new EmotionActiveDialogue());
        NeoForge.EVENT_BUS.register(new EmotionBetrayalManager());
        NeoForge.EVENT_BUS.register(new HungerManager());
        NeoForge.EVENT_BUS.register(new EmotionDotingManager());
        NeoForge.EVENT_BUS.register(new EmotionPassiveManager());
        NeoForge.EVENT_BUS.register(new ChatEventListener());
        NeoForge.EVENT_BUS.register(new EmotionDevotedManager());
        NeoForge.EVENT_BUS.register(new MaidHungerGuiDisplay());
        NeoForge.EVENT_BUS.register(new EmotionForgettingManager());
        NeoForge.EVENT_BUS.register(new LazyMaidHitHandler());
        NeoForge.EVENT_BUS.register(new SaddlePickupHandler());
        NeoForge.EVENT_BUS.register(new SaddleLaunchHandler());
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new CustomExtraMaidBrain());
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new LazyMaidTask());
    }
}