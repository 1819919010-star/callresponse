package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.DISPATCH_BOOK.get())
                .pattern(" P ")
                .pattern("BCB")
                .pattern(" F ")
                .define('P', Items.PAPER)
                .define('B', Items.BOOK)
                .define('C', Items.COMPASS)
                .define('F', Items.FEATHER)
                .unlockedBy("has_book", has(Items.BOOK))
                .save(output, ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "dispatch_book"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModItems.REWARD_BOX.get())
                .pattern("IGI")
                .pattern("ICI")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GOLD_INGOT)
                .define('C', Items.CHEST)
                .unlockedBy("has_chest", has(Items.CHEST))
                .save(output, ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "reward_box"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.HUNT_ORDER.get())
                .requires(Items.NETHER_STAR)
                .requires(Items.DIAMOND_SWORD)
                .requires(Items.SHIELD)
                .requires(Items.BOOK)
                .requires(Items.BOOK)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(output, ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "hunt_order"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.WANDERING_MAID_BOOK.get())
                .requires(Items.WRITABLE_BOOK)
                .requires(Items.EMERALD)
                .unlockedBy("has_writable_book", has(Items.WRITABLE_BOOK))
                .save(output, ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "wandering_maid_book"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.MORE_EAT_BAUBLE.get())
                .requires(Items.CAKE)
                .requires(Items.GOLDEN_APPLE)
                .requires(Items.HONEY_BOTTLE)
                .unlockedBy("has_cake", has(Items.CAKE))
                .save(output, ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "moreeat_bauble"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.NO_EAT_BAUBLE.get())
                .requires(Items.ROTTEN_FLESH)
                .requires(Items.SPIDER_EYE)
                .requires(Items.PUFFERFISH)
                .unlockedBy("has_rotten_flesh", has(Items.ROTTEN_FLESH))
                .save(output, ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "noeat_bauble"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.FACILITY_CAPACITY_TOOL.get())
                .requires(Items.AMETHYST_SHARD)
                .unlockedBy("get", has(Items.AMETHYST_SHARD))
                .save(output);
    }
}
