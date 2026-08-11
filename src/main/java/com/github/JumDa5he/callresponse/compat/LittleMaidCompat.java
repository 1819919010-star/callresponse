package com.github.JumDa5he.callresponse.compat;

import com.github.JumDa5he.callresponse.compat.bauble.MaidConflictBaubleHandler;
import com.github.JumDa5he.callresponse.compat.bauble.NoEatBauble;
import com.github.JumDa5he.callresponse.compat.brain.CustomExtraMaidBrain;
import com.github.JumDa5he.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.JumDa5he.callresponse.compat.broadcast.ChatEventListener;
import com.github.JumDa5he.callresponse.compat.emotion.*;
import com.github.JumDa5he.callresponse.compat.hunt.HuntGunEventBridge;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderInteractListener;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntTargetProtectionBypass;
import com.github.JumDa5he.callresponse.compat.hunger.HungerManager;
import com.github.JumDa5he.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.JumDa5he.callresponse.compat.hunger.NoEatAwareMaidMeal;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.task.LazyMaidTask;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

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
        NeoForge.EVENT_BUS.register(NoEatBauble.class);
        NeoForge.EVENT_BUS.register(MaidConflictBaubleHandler.class);
        NeoForge.EVENT_BUS.register(new FearPanicManager());
        NeoForge.EVENT_BUS.register(new HuntOrderManager());
        NeoForge.EVENT_BUS.register(new HuntOrderInteractListener());
        NeoForge.EVENT_BUS.register(HuntTargetProtectionBypass.class);
        HuntGunEventBridge.register();
    }

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.NO_EAT_BAUBLE.get(), (IMaidBauble) ModItems.NO_EAT_BAUBLE.get());
        manager.bind(ModItems.MORE_EAT_BAUBLE.get(), (IMaidBauble) ModItems.MORE_EAT_BAUBLE.get());
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new CustomExtraMaidBrain());
    }

    @Override
    public void addMaidMeal(MaidMealManager manager) {
        wrapMaidMeals(MaidMealType.WORK_MEAL);
        wrapMaidMeals(MaidMealType.HOME_MEAL);
        wrapMaidMeals(MaidMealType.HEAL_MEAL);
    }

    private void wrapMaidMeals(MaidMealType type) {
        List<IMaidMeal> meals = MaidMealManager.getMaidMeals(type);
        for (int i = 0; i < meals.size(); i++) {
            IMaidMeal meal = meals.get(i);
            if (!(meal instanceof NoEatAwareMaidMeal)) {
                meals.set(i, new NoEatAwareMaidMeal(meal));
            }
        }
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new LazyMaidTask());
    }
}
