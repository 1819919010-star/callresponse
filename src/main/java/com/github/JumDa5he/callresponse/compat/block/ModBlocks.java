package com.github.JumDa5he.callresponse.compat.block;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CallResponseMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CallResponseMod.MOD_ID);

    public static final DeferredBlock<MaidCropBlock> MAID_CROP_BLOCK = BLOCKS.register("maid_crop", MaidCropBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaidCropBlockEntity>> MAID_CROP_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("maid_crop", r ->
            new BlockEntityType<>(MaidCropBlockEntity::new, MAID_CROP_BLOCK.get()));
    public static final DeferredBlock<RewardBoxBlock> REWARD_BOX = BLOCKS.register("reward_box", RewardBoxBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RewardBoxBlockEntity>> REWARD_BOX_ENTITY = BLOCK_ENTITY_TYPES.register("reward_box", r ->
            new BlockEntityType<>(RewardBoxBlockEntity::new, REWARD_BOX.get()));
}
