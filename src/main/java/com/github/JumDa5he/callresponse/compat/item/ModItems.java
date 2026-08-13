package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.bauble.MoreEatBauble;
import com.github.JumDa5he.callresponse.compat.bauble.NoEatBauble;
import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, CallResponseMod.MOD_ID);

    public static final Supplier<Item> EMOTION_BOOK = ITEMS.register("emotion_book",
            () -> new Item(new Item.Properties().stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final Supplier<Item> NO_EAT_BAUBLE = ITEMS.register("noeat_bauble",
            () -> new NoEatBauble(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final Supplier<Item> MORE_EAT_BAUBLE = ITEMS.register("moreeat_bauble",
            () -> new MoreEatBauble(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final Supplier<Item> HUNT_ORDER = ITEMS.register("hunt_order",
            () -> new HuntOrderItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final Supplier<Item> WANDERING_MAID_BOOK = ITEMS.register("wandering_maid_book",
            () -> new WanderingMaidBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final Supplier<Item> MAID_SEED = ITEMS.register("maid_seed", r -> new ItemNameBlockItem(
            ModBlocks.MAID_CROP_BLOCK.get(), new Item.Properties().rarity(Rarity.RARE)
    ));

    public static final Supplier<Item> DISPOSABLE_FAVORABILITY_TOOL_ADD = ITEMS.register(
            "disposable_favorability_tool_add", DisposableFavorabilityToolItem::new);

    // ===== 创造模式物品栏：专属栏位 =====
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CallResponseMod.MOD_ID);

    public static final Supplier<CreativeModeTab> CALLRESPONSE_TAB = TABS.register("callresponse",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(EMOTION_BOOK.get()))
                    .title(Component.translatable("itemGroup.callresponse"))
                    .displayItems((params, output) -> {
                        output.accept(EMOTION_BOOK.get());
                        output.accept(NO_EAT_BAUBLE.get());
                        output.accept(MORE_EAT_BAUBLE.get());
                        output.accept(HUNT_ORDER.get());
                        output.accept(WANDERING_MAID_BOOK.get());
                        output.accept(MAID_SEED.get());
                        output.accept(DISPOSABLE_FAVORABILITY_TOOL_ADD.get());
                    })
                    .build());
}
