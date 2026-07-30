package com.github.tartaricacid.callresponse;

import com.github.tartaricacid.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.tartaricacid.callresponse.compat.broadcast.ChatEventListener;
import com.github.tartaricacid.callresponse.compat.emotion.*;
import com.github.tartaricacid.callresponse.compat.hunger.HungerManager;
import com.github.tartaricacid.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.tartaricacid.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.callresponse.config.EmotionPassiveConfig;
import com.github.tartaricacid.callresponse.init.InitAttachTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CallResponseMod.MOD_ID)
public class CallResponseMod {
    public static final String MOD_ID = "callresponse";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CallResponseMod(IEventBus modEventBus, ModContainer modContainer) {
        // 1. 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, BroadcastConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, EmotionPassiveConfig.SPEC, MOD_ID + "-emotion-passive.toml");

        // 2. 注册表
        InitAttachTypes.init(modEventBus);

        // 3.EventHandler
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

        LOGGER.info("✅ CallResponse mod 初始化完成！");
    }
}
