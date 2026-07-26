package com.github.tartaricacid.callresponse.compat;

import com.github.tartaricacid.callresponse.compat.brain.CustomExtraMaidBrain;
import com.github.tartaricacid.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.tartaricacid.callresponse.compat.broadcast.ChatEventListener;
import com.github.tartaricacid.callresponse.compat.emotion.*;
import com.github.tartaricacid.callresponse.compat.hunger.CustomCakeEdible;
import com.github.tartaricacid.callresponse.compat.hunger.HungerManager;
import com.github.tartaricacid.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.tartaricacid.callresponse.compat.task.LazyMaidTask;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.util.List;

@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {

    public LittleMaidCompat() {
        // 注册所有事件监听
        MinecraftForge.EVENT_BUS.register(new EmotionEventListener());
        MinecraftForge.EVENT_BUS.register(new EmotionActiveDialogue());
        MinecraftForge.EVENT_BUS.register(new EmotionBetrayalManager());
        MinecraftForge.EVENT_BUS.register(new HungerManager());
        MinecraftForge.EVENT_BUS.register(new EmotionDotingManager());
        MinecraftForge.EVENT_BUS.register(new EmotionPassiveManager());
        MinecraftForge.EVENT_BUS.register(new ChatEventListener());
        MinecraftForge.EVENT_BUS.register(new EmotionDevotedManager());
        MinecraftForge.EVENT_BUS.register(new MaidHungerGuiDisplay());
        MinecraftForge.EVENT_BUS.register(new EmotionForgettingManager());
        MinecraftForge.EVENT_BUS.register(new LazyMaidHitHandler());
    }




    @Override
    @OnlyIn(Dist.CLIENT)
    public void addMaidTips(MaidTipsOverlay maidTipsOverlay) {
        maidTipsOverlay.addTips("overlay.example.apple.tips", Items.APPLE);
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new CustomExtraMaidBrain());
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new LazyMaidTask());
    }

    @Override
    public void registerMaidEdibleBlock(MaidEdibleBlockManager manager) {
        // ===== 替换原版蛋糕为自定义版本（支持饱食度同步） =====
        try {
            Field field = MaidEdibleBlockManager.class.getDeclaredField("EDIBLE_BLOCKS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) field.get(null);

            // 移除原版 CakeEdible
            list.removeIf(item -> item.getClass().getSimpleName().equals("CakeEdible"));

            // 添加自定义蛋糕（支持饱食度）
            list.add(new CustomCakeEdible());

            System.out.println("[饱食度] 已成功替换零食柜蛋糕为自定义版本");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[饱食度] 替换蛋糕失败: " + e.getMessage());
        }
    }
}