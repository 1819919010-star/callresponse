package com.github.JumDa5he.callresponse.compat.gift;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 礼物系统用到的数据组件。
 */
public final class ModGiftComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CallResponseMod.MOD_ID);

    /** 礼物盒内容 */
    public static final Supplier<DataComponentType<GiftBoxData>> GIFT_BOX = COMPONENTS.register("gift_box",
            () -> DataComponentType.<GiftBoxData>builder()
                    .persistent(GiftBoxData.CODEC)
                    .networkSynchronized(GiftBoxData.STREAM_CODEC)
                    .build());

    private ModGiftComponents() {
    }
}
