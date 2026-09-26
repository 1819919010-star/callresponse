package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventProvider;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchEventDefinition;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.network.chat.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class ModDispatchEventProvider extends DispatchEventProvider {
    public ModDispatchEventProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                    ExistingFileHelper existingFileHelper) {
        super(output, CallResponseMod.MOD_ID, lookupProvider, existingFileHelper);
    }

    @Override
    public void gatherDispatchEvent(Saver saver) {
        DispatchEventBuilder.builder("bakery_assist")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.bakery_assist.title"))
                .description(Component.translatable("text.callresponse.dispatch.bakery_assist.description"))
                .duration(10, 30)
                .emotion(1, -3, 5, 4)
                .weight(12)
                .cooldown(15)
                .item(Items.BREAD, 32, 64, 10)
                .item(Items.CAKE, 2, 4, 4)
                .item(Items.COOKIE, 24, 32, 8)
                .save(saver);

        DispatchEventBuilder.builder("festival_visit")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.festival_visit.title"))
                .description(Component.translatable("text.callresponse.dispatch.festival_visit.description"))
                .duration(20, 40)
                .emotion(4, -2, 6, 6)
                .weight(8)
                .cooldown(60)
                .item(Items.APPLE, 16, 24, 10)
                .item(Items.FIREWORK_ROCKET, 10, 20, 6)
                .item(Items.EMERALD, 7, 14, 4)
                .save(saver);

        DispatchEventBuilder.builder("fishing_day")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.fishing_day.title"))
                .description(Component.translatable("text.callresponse.dispatch.fishing_day.description"))
                .duration(10, 20)
                .emotion(1, -1, 2, -4)
                .weight(9)
                .cooldown(30)
                .item(Items.COD, 16, 32, 10)
                .item(Items.SALMON, 15, 30, 7)
                .enchant(ResourceLocation.withDefaultNamespace("luck_of_the_sea"), 1, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("flower_trip")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.flower_trip.title"))
                .description(Component.translatable("text.callresponse.dispatch.flower_trip.description"))
                .duration(10, 25)
                .emotion(2, -2, 15, -3)
                .weight(10)
                .cooldown(20)
                .item(Items.POPPY, 4, 12, 10)
                .item(Items.CORNFLOWER, 3, 9, 8)
                .item(Items.HONEY_BOTTLE, 6, 12, 3)
                .save(saver);

        DispatchEventBuilder.builder("forest_picnic")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.forest_picnic.title"))
                .description(Component.translatable("text.callresponse.dispatch.forest_picnic.description"))
                .duration(15, 25)
                .emotion(3, -3, 12, 9)
                .weight(11)
                .cooldown(20)
                .item(Items.APPLE, 7, 14, 10)
                .item(Items.SWEET_BERRIES, 10, 20, 8)
                .item(Items.OAK_SAPLING, 1, 4, 4)
                .save(saver);

        DispatchEventBuilder.builder("library_sort")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.library_sort.title"))
                .description(Component.translatable("text.callresponse.dispatch.library_sort.description"))
                .duration(20, 40)
                .emotion(1, -2, 3, -5)
                .weight(9)
                .cooldown(20)
                .item(Items.BOOK, 16, 32, 10)
                .item(Items.EXPERIENCE_BOTTLE, 32, 64, 5)
                .enchant(ResourceLocation.withDefaultNamespace("unbreaking"), 1, 3, 3)
                .save(saver);

        DispatchEventBuilder.builder("maiddoll_model")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.maiddoll_model.title"))
                .description(Component.translatable("text.callresponse.dispatch.maiddoll_model.description"))
                .duration(30, 50)
                .emotion(2, 1, 2, -10)
                .weight(6)
                .cooldown(90)
                .item(Items.CAKE, 1, 2, 3)
                .item(Items.DIAMOND, 5, 12, 10)
                .item(Items.GOLD_BLOCK, 1, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("merchant_guard")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.merchant_guard.title"))
                .description(Component.translatable("text.callresponse.dispatch.merchant_guard.description"))
                .duration(45, 60)
                .emotion(2, -1, 6, -15)
                .weight(7)
                .cooldown(90)
                .item(Items.EMERALD, 48, 64, 10)
                .item(Items.GOLDEN_APPLE, 13, 20, 3)
                .enchant(ResourceLocation.withDefaultNamespace("protection"), 2, 4, 2)
                .save(saver);

        DispatchEventBuilder.builder("mine_survey")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.mine_survey.title"))
                .description(Component.translatable("text.callresponse.dispatch.mine_survey.description"))
                .duration(20, 40)
                .emotion(1, 1, 2, -15)
                .weight(8)
                .cooldown(60)
                .item(Items.IRON_INGOT, 32, 64, 10)
                .item(Items.GOLD_INGOT, 16, 32, 6)
                .item(Items.DIAMOND, 10, 20, 2)
                .save(saver);

        DispatchEventBuilder.builder("nether_expedition")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.nether_expedition.title"))
                .description(Component.translatable("text.callresponse.dispatch.nether_expedition.description"))
                .duration(45, 60)
                .emotion(2, 3, 4, -14)
                .weight(6)
                .cooldown(90)
                .item(Items.WITHER_SKELETON_SKULL, 1, 2, 3)
                .item(Items.BLAZE_ROD, 5, 12, 10)
                .item(Items.NETHERITE_SCRAP, 1, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("snow_view")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.snow_view.title"))
                .description(Component.translatable("text.callresponse.dispatch.snow_view.description"))
                .duration(15, 30)
                .emotion(3, -1, 6, -8)
                .weight(7)
                .cooldown(60)
                .item(Items.SNOWBALL, 12, 16, 10)
                .item(Items.BLUE_ICE, 16, 32, 3)
                .item(Items.RABBIT_HIDE, 8, 16, 5)
                .save(saver);

        DispatchEventBuilder.builder("village_help")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.village_help.title"))
                .description(Component.translatable("text.callresponse.dispatch.village_help.description"))
                .duration(10, 20)
                .emotion(1, -4, 2, -10)
                .weight(10)
                .cooldown(30)
                .item(Items.EMERALD, 16, 32, 10)
                .item(Items.BREAD, 12, 24, 6)
                .enchant(ResourceLocation.withDefaultNamespace("efficiency"), 2, 4, 2)
                .save(saver);

        DispatchEventBuilder.builder("farm_harvest")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.farm_harvest.title"))
                .description(Component.translatable("text.callresponse.dispatch.farm_harvest.description"))
                .duration(15, 30)
                .emotion(1, -3, 4, -5)
                .weight(10)
                .cooldown(20)
                .experience(5)
                .item(Items.WHEAT, 24, 40, 10)
                .item(Items.BREAD, 8, 16, 8)
                .item(Items.EMERALD, 3, 7, 6)
                .save(saver);

        DispatchEventBuilder.builder("shipwreck_dive")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.shipwreck_dive.title"))
                .description(Component.translatable("text.callresponse.dispatch.shipwreck_dive.description"))
                .duration(25, 45)
                .emotion(2, 1, 3, -8)
                .weight(8)
                .cooldown(60)
                .experience(10)
                .item(Items.HEART_OF_THE_SEA, 1, 1, 3)
                .item(Items.NAUTILUS_SHELL, 2, 5, 8)
                .item(Items.PRISMARINE_SHARD, 12, 20, 10)
                .enchant(ResourceLocation.withDefaultNamespace("respiration"), 2, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("mineshaft_inspect")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.mineshaft_inspect.title"))
                .description(Component.translatable("text.callresponse.dispatch.mineshaft_inspect.description"))
                .duration(30, 50)
                .emotion(1, -2, 2, -12)
                .weight(8)
                .cooldown(45)
                .experience(8)
                .item(Items.IRON_INGOT, 20, 35, 10)
                .item(Items.RAIL, 32, 48, 8)
                .item(Items.REDSTONE_TORCH, 16, 24, 6)
                .enchant(ResourceLocation.withDefaultNamespace("unbreaking"), 1, 2, 3)
                .save(saver);

        DispatchEventBuilder.builder("desert_excavation")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.desert_excavation.title"))
                .description(Component.translatable("text.callresponse.dispatch.desert_excavation.description"))
                .duration(20, 40)
                .emotion(2, 0, 5, -6)
                .weight(7)
                .cooldown(50)
                .experience(6)
                .item(Items.SAND, 32, 48, 10)
                .item(Items.SANDSTONE, 16, 24, 8)
                .item(Items.BONE, 8, 16, 10)
                .item(Items.EMERALD, 4, 8, 5)
                .enchant(ResourceLocation.withDefaultNamespace("efficiency"), 2, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("jungle_trek")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.jungle_trek.title"))
                .description(Component.translatable("text.callresponse.dispatch.jungle_trek.description"))
                .duration(15, 30)
                .emotion(3, -1, 6, -5)
                .weight(9)
                .cooldown(30)
                .experience(4)
                .item(Items.COCOA_BEANS, 8, 16, 10)
                .item(Items.BAMBOO, 12, 24, 8)
                .item(Items.VINE, 8, 16, 6)
                .item(Items.MELON_SLICE, 16, 24, 5)
                .save(saver);

        DispatchEventBuilder.builder("caravan_escort")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.caravan_escort.title"))
                .description(Component.translatable("text.callresponse.dispatch.caravan_escort.description"))
                .duration(40, 60)
                .emotion(2, -4, 5, -10)
                .weight(6)
                .cooldown(90)
                .experience(15)
                .item(Items.EMERALD, 32, 48, 10)
                .item(Items.GOLDEN_APPLE, 8, 16, 6)
                .item(Items.ENCHANTED_GOLDEN_APPLE, 1, 2, 2)
                .enchant(ResourceLocation.withDefaultNamespace("protection"), 3, 4, 3)
                .save(saver);

        DispatchEventBuilder.builder("fishing_contest")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.fishing_contest.title"))
                .description(Component.translatable("text.callresponse.dispatch.fishing_contest.description"))
                .duration(15, 25)
                .emotion(4, -1, 5, -3)
                .weight(10)
                .cooldown(25)
                .experience(8)
                .item(Items.COD, 20, 32, 10)
                .item(Items.SALMON, 16, 28, 8)
                .item(Items.PUFFERFISH, 4, 8, 4)
                .item(Items.TROPICAL_FISH, 4, 8, 3)
                .enchant(ResourceLocation.withDefaultNamespace("luck_of_the_sea"), 2, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("potion_order")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.potion_order.title"))
                .description(Component.translatable("text.callresponse.dispatch.potion_order.description"))
                .duration(20, 35)
                .emotion(1, -2, 3, -6)
                .weight(8)
                .cooldown(40)
                .experience(6)
                .item(Items.GLASS_BOTTLE, 16, 24, 10)
                .item(Items.NETHER_WART, 12, 20, 8)
                .item(Items.REDSTONE, 16, 32, 6)
                .item(Items.GLOWSTONE_DUST, 8, 16, 5)
                .enchant(ResourceLocation.withDefaultNamespace("unbreaking"), 1, 1, 1)
                .save(saver);

        DispatchEventBuilder.builder("wool_gathering")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.wool_gathering.title"))
                .description(Component.translatable("text.callresponse.dispatch.wool_gathering.description"))
                .duration(10, 20)
                .emotion(1, -2, 3, -5)
                .weight(10)
                .cooldown(15)
                .experience(4)
                .item(Items.WHITE_WOOL, 24, 40, 10)
                .item(Items.BLACK_WOOL, 8, 16, 6)
                .item(Items.SHEARS, 1, 1, 8)
                .item(Items.STRING, 8, 16, 4)
                .save(saver);

        DispatchEventBuilder.builder("cave_exploration")
                .category(DispatchEventDefinition.Category.PLAY)
                .title(Component.translatable("text.callresponse.dispatch.cave_exploration.title"))
                .description(Component.translatable("text.callresponse.dispatch.cave_exploration.description"))
                .duration(25, 45)
                .emotion(2, -3, 4, -8)
                .weight(7)
                .cooldown(60)
                .experience(12)
                .item(Items.AMETHYST_SHARD, 8, 16, 10)
                .item(Items.GLOW_INK_SAC, 4, 8, 8)
                .item(Items.MOSS_BLOCK, 8, 16, 6)
                .enchant(ResourceLocation.withDefaultNamespace("unbreaking"), 1, 2, 2)
                .save(saver);

        //region gly小巧思
        // 更多的我写自己模组里了
        DispatchEventBuilder.builder("gly_i_fox")
                .category(DispatchEventDefinition.Category.WORK)
                .title(Component.translatable("text.callresponse.dispatch.gly_i_fox.title"))
                .description(Component.translatable("text.callresponse.dispatch.gly_i_fox.description"))
                .duration(5, 10)
                .emotion(-10, 10, 10, -5)
                .weight(5)
                .cooldown(60)
                .experience(100)
                .items(1, 1, 1, InitItems.SMART_SLAB_INIT.get(), Items.CAKE)
                .item(Items.AIR, 1, 1, 1)
                .save(saver);
        //endregion
    }
}
