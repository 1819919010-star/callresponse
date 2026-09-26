package com.github.JumDa5he.callresponse.compat.block;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlock;
import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, CallResponseMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CallResponseMod.MOD_ID);

    public static final DeferredHolder<Block, MaidCropBlock> MAID_CROP_BLOCK = BLOCKS.register("maid_crop", r -> new MaidCropBlock());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaidCropBlockEntity>> MAID_CROP_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("maid_crop", r ->
            BlockEntityType.Builder.of(MaidCropBlockEntity::new, MAID_CROP_BLOCK.get()).build(null));
    public static final DeferredHolder<Block, RewardBoxBlock> REWARD_BOX = BLOCKS.register("reward_box", r -> new RewardBoxBlock());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RewardBoxBlockEntity>> REWARD_BOX_ENTITY = BLOCK_ENTITY_TYPES.register("reward_box", r ->
            BlockEntityType.Builder.of(RewardBoxBlockEntity::new, REWARD_BOX.get()).build(null));
    public static final DeferredHolder<Block, DarkIronCageBlock> DARK_IRON_CAGE = BLOCKS.register("dark_iron_cage", r -> new DarkIronCageBlock());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DarkIronCageBlockEntity>> DARK_IRON_CAGE_ENTITY = BLOCK_ENTITY_TYPES.register("dark_iron_cage", r ->
            BlockEntityType.Builder.of(DarkIronCageBlockEntity::new, DARK_IRON_CAGE.get()).build(null));
}
