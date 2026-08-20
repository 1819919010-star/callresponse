package com.github.JumDa5he.callresponse;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.capability.ModCapabilities;
import com.github.JumDa5he.callresponse.compat.datagen.DataGenerators;
import com.github.JumDa5he.callresponse.network.NetworkRegistryHandler;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.menu.ModMenus;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import com.github.JumDa5he.callresponse.config.DispatchConfig;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ENTITY_TYPES.register(modEventBus);

        // 2. 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, BroadcastConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, EmotionPassiveConfig.SPEC, MOD_ID + "-emotion-passive.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, DispatchConfig.SPEC, MOD_ID + "-dispatch.toml");

        // 3. 注册表
        InitAttachTypes.init(modEventBus);
        
        // 4. 注册网络包
        modEventBus.addListener(NetworkRegistryHandler::register);

        // 4.5 注册能力
        modEventBus.addListener(ModCapabilities::registerCapabilities);

        // 5. 数据生成
        modEventBus.addListener(DataGenerators::onGatherData);

        LOGGER.info("✅ CallResponse mod 初始化完成！");
    }
}
