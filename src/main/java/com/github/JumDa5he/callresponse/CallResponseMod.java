package com.github.JumDa5he.callresponse;

import com.github.JumDa5he.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.JumDa5he.callresponse.compat.broadcast.ChatEventListener;
import com.github.JumDa5he.callresponse.compat.emotion.*;
import com.github.JumDa5he.callresponse.compat.gui.NetworkRegistryHandler;
import com.github.JumDa5he.callresponse.compat.hunger.HungerManager;
import com.github.JumDa5he.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.menu.ModMenus;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
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
        // 1. 注册物品
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        // 2. 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, BroadcastConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, EmotionPassiveConfig.SPEC, MOD_ID + "-emotion-passive.toml");

        // 3. 注册表
        InitAttachTypes.init(modEventBus);
        
        // 4. 注册网络包
        modEventBus.addListener(NetworkRegistryHandler::register);

        LOGGER.info("✅ CallResponse mod 初始化完成！");
    }
}
