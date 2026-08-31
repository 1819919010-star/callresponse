package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.bauble.MoreEatBauble;
import com.github.JumDa5he.callresponse.compat.bauble.NoEatBauble;
import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacityToolItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CallResponseMod.MOD_ID);

    public static final DeferredItem<Item> EMOTION_BOOK = ITEMS.register("emotion_book",
            id -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final DeferredItem<Item> NO_EAT_BAUBLE = ITEMS.register("noeat_bauble",
            id -> new NoEatBauble(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)));

    public static final DeferredItem<Item> MORE_EAT_BAUBLE = ITEMS.register("moreeat_bauble",
            id -> new MoreEatBauble(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)));

    public static final DeferredItem<Item> HUNT_ORDER = ITEMS.register("hunt_order",
            id -> new HuntOrderItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)));

    public static final DeferredItem<Item> WANDERING_MAID_BOOK = ITEMS.register("wandering_maid_book",
            id -> new WanderingMaidBookItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)));

    public static final DeferredItem<Item> MAID_SEED = ITEMS.register("maid_seed",
            id -> new BlockItem(ModBlocks.MAID_CROP_BLOCK.get(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredItem<Item> DISPOSABLE_FAVORABILITY_TOOL_ADD = ITEMS.register(
            "disposable_favorability_tool_add", id -> new DisposableFavorabilityToolItem(id));

    public static final DeferredItem<Item> FREE_PHOTO = ITEMS.register("free_photo", ItemFreePhoto::new);

    public static final DeferredItem<Item> DISPATCH_BOOK = ITEMS.register("dispatch_book",
            id -> new DispatchBookItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<Item> REWARD_BOX = ITEMS.register("reward_box",
            id -> new BlockItem(ModBlocks.REWARD_BOX.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id)).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> FACILITY_CAPACITY_TOOL = ITEMS.register("facility_capacity_tool",
            id -> new FacilityCapacityToolItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1).rarity(Rarity.RARE)));

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
                        output.accept(DISPATCH_BOOK.get());
                        output.accept(REWARD_BOX.get());
                        output.accept(FACILITY_CAPACITY_TOOL.get());
                    })
                    .build());
}
