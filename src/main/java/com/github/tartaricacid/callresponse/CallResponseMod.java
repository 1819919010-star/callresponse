package com.github.tartaricacid.callresponse;

import com.github.tartaricacid.callresponse.compat.hunger.HungerAwareEdibleWrapper;
import com.github.tartaricacid.callresponse.compat.hunger.SyncHungerPacket;
import com.github.tartaricacid.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.callresponse.config.EmotionPassiveConfig;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

@Mod(CallResponseMod.MOD_ID)
public class CallResponseMod {
    public static final String MOD_ID = "callresponse";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CallResponseMod(IEventBus modEventBus, ModContainer modContainer) {
        // 1. 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, BroadcastConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, EmotionPassiveConfig.SPEC, MOD_ID + "-emotion-passive.toml");

        // 2. 注册网络包
        modEventBus.addListener(SyncHungerPacket::register);

        // 3. 注册 ILittleMaid 扩展
        TouhouLittleMaid.EXTENSIONS.add(new ILittleMaid() {
            @Override
            public void registerMaidEdibleBlock(MaidEdibleBlockManager manager) {
                try {
                    Field field = MaidEdibleBlockManager.class.getDeclaredField("EDIBLE_BLOCKS");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<IMaidEdibleBlock> list = (List<IMaidEdibleBlock>) field.get(null);

                    for (int i = 0; i < list.size(); i++) {
                        IMaidEdibleBlock original = list.get(i);
                        list.set(i, new HungerAwareEdibleWrapper(original));
                    }

                    LOGGER.info("✅ 已包装所有 MaidEdibleBlock（共 {} 个），饱食度同步已启用", list.size());
                } catch (Exception e) {
                    LOGGER.error("❌ 包装 MaidEdibleBlock 失败", e);
                }
            }
        });

        LOGGER.info("✅ CallResponse mod 初始化完成！");
    }
}
