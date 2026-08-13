package com.github.JumDa5he.callresponse;

import com.github.JumDa5he.callresponse.compat.gui.CopyEntityUuidS2CPacket;
import com.github.JumDa5he.callresponse.compat.gui.DropMaidC2SPacket;
import com.github.JumDa5he.callresponse.compat.gui.ExpelMaidC2SPacket;
import com.github.JumDa5he.callresponse.compat.gui.EmotionBookUpdateC2SPacket;
import com.github.JumDa5he.callresponse.compat.gui.HuntOrderUpdateC2SPacket;
import com.github.JumDa5he.callresponse.compat.gui.MaidListS2CPacket;
import com.github.JumDa5he.callresponse.compat.gui.OpenEmotionBookScreenS2CPacket;
import com.github.JumDa5he.callresponse.compat.gui.OpenHuntOrderScreenS2CPacket;
import com.github.JumDa5he.callresponse.compat.gui.RequestHuntOrderScreenC2SPacket;
import com.github.JumDa5he.callresponse.compat.gui.OpenWanderingMaidRequestS2CPacket;
import com.github.JumDa5he.callresponse.compat.gui.OpenWanderingSkinPoolS2CPacket;
import com.github.JumDa5he.callresponse.compat.gui.UpdateWanderingSkinPoolC2SPacket;
import com.github.JumDa5he.callresponse.compat.gui.WanderingMaidDecisionC2SPacket;
import com.github.JumDa5he.callresponse.compat.hunger.HungerAwareEdibleWrapper;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.menu.ModMenus;
import com.github.JumDa5he.callresponse.compat.menu.OpenMaidStatusC2SPacket;
import com.github.JumDa5he.callresponse.compat.trade.EnableTradingMaidButtonS2CPacket;
import com.github.JumDa5he.callresponse.compat.trade.OpenTradingMaidScreenS2CPacket;
import com.github.JumDa5he.callresponse.compat.trade.RequestTradingMaidScreenC2SPacket;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidActionC2SPacket;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
             new ResourceLocation(MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public CallResponseMod() {
        // ===== 1. 注册物品 =====
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);

        // ===== 2. 注册配置文件 =====
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BroadcastConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EmotionPassiveConfig.SPEC, MOD_ID + "-emotion-passive.toml");

        // ===== 3. 注册网络包 =====
        int id = 0;
        CHANNEL.registerMessage(id++, OpenEmotionBookScreenS2CPacket.class,
                OpenEmotionBookScreenS2CPacket::encode,
                OpenEmotionBookScreenS2CPacket::new,
                OpenEmotionBookScreenS2CPacket::handle);
        CHANNEL.registerMessage(id++, EmotionBookUpdateC2SPacket.class,
                EmotionBookUpdateC2SPacket::encode,
                EmotionBookUpdateC2SPacket::new,
                EmotionBookUpdateC2SPacket::handle);
        CHANNEL.registerMessage(id++, DropMaidC2SPacket.class,
                DropMaidC2SPacket::encode,
                DropMaidC2SPacket::new,
                DropMaidC2SPacket::handle);
        CHANNEL.registerMessage(id++, OpenHuntOrderScreenS2CPacket.class,
                OpenHuntOrderScreenS2CPacket::encode,
                OpenHuntOrderScreenS2CPacket::new,
                OpenHuntOrderScreenS2CPacket::handle);
        CHANNEL.registerMessage(id++, HuntOrderUpdateC2SPacket.class,
                HuntOrderUpdateC2SPacket::encode,
                HuntOrderUpdateC2SPacket::new,
                HuntOrderUpdateC2SPacket::handle);
        CHANNEL.registerMessage(id++, CopyEntityUuidS2CPacket.class,
                CopyEntityUuidS2CPacket::encode,
                CopyEntityUuidS2CPacket::new,
                CopyEntityUuidS2CPacket::handle);
        CHANNEL.registerMessage(id++, MaidListS2CPacket.class,
                MaidListS2CPacket::encode,
                MaidListS2CPacket::new,
                MaidListS2CPacket::handle);
        CHANNEL.registerMessage(id++, RequestHuntOrderScreenC2SPacket.class,
                RequestHuntOrderScreenC2SPacket::encode,
                RequestHuntOrderScreenC2SPacket::new,
                RequestHuntOrderScreenC2SPacket::handle);
        CHANNEL.registerMessage(id++, OpenWanderingSkinPoolS2CPacket.class,
                OpenWanderingSkinPoolS2CPacket::encode,
                OpenWanderingSkinPoolS2CPacket::new,
                OpenWanderingSkinPoolS2CPacket::handle);
        CHANNEL.registerMessage(id++, UpdateWanderingSkinPoolC2SPacket.class,
                UpdateWanderingSkinPoolC2SPacket::encode,
                UpdateWanderingSkinPoolC2SPacket::new,
                UpdateWanderingSkinPoolC2SPacket::handle);
        CHANNEL.registerMessage(id++, OpenWanderingMaidRequestS2CPacket.class,
                OpenWanderingMaidRequestS2CPacket::encode,
                OpenWanderingMaidRequestS2CPacket::new,
                OpenWanderingMaidRequestS2CPacket::handle);
        CHANNEL.registerMessage(id++, WanderingMaidDecisionC2SPacket.class,
                WanderingMaidDecisionC2SPacket::encode,
                WanderingMaidDecisionC2SPacket::new,
                WanderingMaidDecisionC2SPacket::handle);
        CHANNEL.registerMessage(id++, ExpelMaidC2SPacket.class,
                ExpelMaidC2SPacket::encode,
                ExpelMaidC2SPacket::new,
                ExpelMaidC2SPacket::handle);
        CHANNEL.registerMessage(id++, OpenMaidStatusC2SPacket.class,
                OpenMaidStatusC2SPacket::encode,
                OpenMaidStatusC2SPacket::new,
                OpenMaidStatusC2SPacket::handle);
        CHANNEL.registerMessage(id++, EnableTradingMaidButtonS2CPacket.class,
                EnableTradingMaidButtonS2CPacket::encode,
                EnableTradingMaidButtonS2CPacket::new,
                EnableTradingMaidButtonS2CPacket::handle);
        CHANNEL.registerMessage(id++, RequestTradingMaidScreenC2SPacket.class,
                RequestTradingMaidScreenC2SPacket::encode,
                RequestTradingMaidScreenC2SPacket::new,
                RequestTradingMaidScreenC2SPacket::handle);
        CHANNEL.registerMessage(id++, OpenTradingMaidScreenS2CPacket.class,
                OpenTradingMaidScreenS2CPacket::encode,
                OpenTradingMaidScreenS2CPacket::new,
                OpenTradingMaidScreenS2CPacket::handle);
        CHANNEL.registerMessage(id++, TradingMaidActionC2SPacket.class,
                TradingMaidActionC2SPacket::encode,
                TradingMaidActionC2SPacket::new,
                TradingMaidActionC2SPacket::handle);

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
