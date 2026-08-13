package com.github.JumDa5he.callresponse.compat;

import com.github.JumDa5he.callresponse.compat.bauble.NoEatBauble;
import com.github.JumDa5he.callresponse.compat.brain.CustomExtraMaidBrain;
import com.github.JumDa5he.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.JumDa5he.callresponse.compat.broadcast.BroadcastTools;
import com.github.JumDa5he.callresponse.compat.broadcast.ChatEventListener;
import com.github.JumDa5he.callresponse.compat.emotion.*;
import com.github.JumDa5he.callresponse.compat.hunger.CustomCakeEdible;
import com.github.JumDa5he.callresponse.compat.hunger.HungerManager;
import com.github.JumDa5he.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.JumDa5he.callresponse.compat.hunger.NoEatAwareMaidMeal;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderInteractListener;
import com.github.JumDa5he.callresponse.compat.hunt.HuntGunEventBridge;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntTargetProtectionBypass;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.task.LazyMaidTask;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidManager;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;

import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // 饥饿条渲染是客户端专属功能（引用客户端 GUI 类），不能在服务器上注册
            MinecraftForge.EVENT_BUS.register(new MaidHungerGuiDisplay());
        }
        MinecraftForge.EVENT_BUS.register(new EmotionForgettingManager());
        MinecraftForge.EVENT_BUS.register(new FearPanicManager());
        MinecraftForge.EVENT_BUS.register(new LazyMaidHitHandler());
        MinecraftForge.EVENT_BUS.register(new SaddlePickupHandler());
        MinecraftForge.EVENT_BUS.register(new SaddleLaunchHandler());
        MinecraftForge.EVENT_BUS.register(new HuntOrderManager());
        MinecraftForge.EVENT_BUS.register(new HuntOrderInteractListener());
        MinecraftForge.EVENT_BUS.register(new HuntTargetProtectionBypass());
        MinecraftForge.EVENT_BUS.register(new WanderingMaidManager());
        MinecraftForge.EVENT_BUS.register(new TradingMaidManager());
        HuntGunEventBridge.register();
        MinecraftForge.EVENT_BUS.register(NoEatBauble.class);
    }




    @Override
    public void registerAITool(ToolRegister register) {
        BroadcastTools.register(register);
    }

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.NO_EAT_BAUBLE.get(), (IMaidBauble) ModItems.NO_EAT_BAUBLE.get());
        manager.bind(ModItems.MORE_EAT_BAUBLE.get(), (IMaidBauble) ModItems.MORE_EAT_BAUBLE.get());
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
    public void addMaidMeal(MaidMealManager manager) {
        wrapMaidMeals(manager, MaidMealType.WORK_MEAL);
        wrapMaidMeals(manager, MaidMealType.HOME_MEAL);
        wrapMaidMeals(manager, MaidMealType.HEAL_MEAL);
    }

    private void wrapMaidMeals(MaidMealManager manager, MaidMealType type) {
        List<IMaidMeal> meals = manager.getMaidMeals(type);
        for (int i = 0; i < meals.size(); i++) {
            IMaidMeal meal = meals.get(i);
            if (!(meal instanceof NoEatAwareMaidMeal)) {
                meals.set(i, new NoEatAwareMaidMeal(meal));
            }
        }
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
