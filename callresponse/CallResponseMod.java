package com.github.tartaricacid.callresponse;

import com.github.tartaricacid.callresponse.compat.hunger.HungerAwareEdibleWrapper;
import com.github.tartaricacid.callresponse.compat.hunger.SyncHungerPacket;
import com.github.tartaricacid.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.callresponse.config.EmotionPassiveConfig;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

@Mod(CallResponseMod.MOD_ID)
public class CallResponseMod {
    public static final String MOD_ID = "callresponse";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // ===== 网络通道（饱食度同步） =====
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public CallResponseMod() {
        // ===== 1. 注册配置文件 =====
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BroadcastConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EmotionPassiveConfig.SPEC, MOD_ID + "-emotion-passive.toml");

        // ===== 2. 注册网络包 =====
        int id = 0;
        CHANNEL.registerMessage(id++, SyncHungerPacket.class,
                SyncHungerPacket::encode,
                SyncHungerPacket::new,
                SyncHungerPacket::handle);

        // ===== 3. 注册 ILittleMaid 扩展（在 MaidEdibleBlockManager.init() 之前执行） =====
        // 这个扩展会包装所有方块食物，增加饱食度同步逻辑

        TouhouLittleMaid.EXTENSIONS.add(new ILittleMaid() {
            @Override
            public void registerMaidEdibleBlock(MaidEdibleBlockManager manager) {
                try {
                    Field field = MaidEdibleBlockManager.class.getDeclaredField("EDIBLE_BLOCKS");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<IMaidEdibleBlock> list = (List<IMaidEdibleBlock>) field.get(null);

                    // 用包装类替换所有 IMaidEdibleBlock
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