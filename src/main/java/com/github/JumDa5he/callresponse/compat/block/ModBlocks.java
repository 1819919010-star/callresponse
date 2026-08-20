package com.github.JumDa5he.callresponse.compat.block;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;


public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CallResponseMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CallResponseMod.MOD_ID);

    public static final RegistryObject<MaidCropBlock> MAID_CROP_BLOCK =
            BLOCKS.register("maid_crop", MaidCropBlock::new);
    public static final RegistryObject<BlockEntityType<MaidCropBlockEntity>> MAID_CROP_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("maid_crop", () ->
                    BlockEntityType.Builder.of(MaidCropBlockEntity::new, MAID_CROP_BLOCK.get()).build(null));
    public static final RegistryObject<RewardBoxBlock> REWARD_BOX =
            BLOCKS.register("reward_box", RewardBoxBlock::new);
    public static final RegistryObject<BlockEntityType<RewardBoxBlockEntity>> REWARD_BOX_ENTITY =
            BLOCK_ENTITY_TYPES.register("reward_box", () ->
                    BlockEntityType.Builder.of(RewardBoxBlockEntity::new, REWARD_BOX.get()).build(null));

    private ModBlocks() {
    }
}
