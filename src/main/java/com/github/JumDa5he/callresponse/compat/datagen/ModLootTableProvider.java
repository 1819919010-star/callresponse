package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Set;

public class ModLootTableProvider extends BlockLootSubProvider {
    protected ModLootTableProvider(HolderLookup.Provider provider) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
    }

    @Override
    protected void generate() {
        dropOther(ModBlocks.MAID_CROP_BLOCK.get(), ModItems.MAID_SEED.get());
        dropSelf(ModBlocks.REWARD_BOX.get());
        dropSelf(ModBlocks.DARK_IRON_CAGE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return Set.of(ModBlocks.MAID_CROP_BLOCK.get(), ModBlocks.REWARD_BOX.get(), ModBlocks.DARK_IRON_CAGE.get());
    }
}
