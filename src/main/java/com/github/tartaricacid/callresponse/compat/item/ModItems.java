package com.github.tartaricacid.callresponse.compat.item;

import com.github.tartaricacid.callresponse.CallResponseMod;
import com.github.tartaricacid.callresponse.compat.bauble.MoreEatBauble;
import com.github.tartaricacid.callresponse.compat.bauble.NoEatBauble;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CallResponseMod.MOD_ID);

    public static final Supplier<Item> EMOTION_BOOK = ITEMS.register("emotion_book",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final Supplier<Item> NO_EAT_BAUBLE = ITEMS.register("noeat_bauble",
            () -> new NoEatBauble(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final Supplier<Item> MORE_EAT_BAUBLE = ITEMS.register("moreeat_bauble",
            () -> new MoreEatBauble(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final Supplier<Item> HUNT_ORDER = ITEMS.register("hunt_order",
            () -> new HuntOrderItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // ===== 创造模式物品栏：专属栏位 =====
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CallResponseMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> CALLRESPONSE_TAB = TABS.register("callresponse",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(EMOTION_BOOK.get()))
                    .title(Component.translatable("itemGroup.callresponse"))
                    .displayItems((params, output) -> {
                        output.accept(EMOTION_BOOK.get());
                        output.accept(NO_EAT_BAUBLE.get());
                        output.accept(MORE_EAT_BAUBLE.get());
                        output.accept(HUNT_ORDER.get());
                    })
                    .build());
}
