package com.github.JumDa5he.callresponse.compat.block;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MaidCropBlock extends CropBlock implements EntityBlock {
    public MaidCropBlock() {
        super(Properties.copy(Blocks.WHEAT));
        registerDefaultState(defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                net.minecraft.core.Direction.NORTH));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaidCropBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.MAID_SEED.get();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        if (getAge(state) == getMaxAge()
                && params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof MaidCropBlockEntity crop) {
            ItemStack photo = new ItemStack(ModItems.FREE_PHOTO.get());
            CompoundTag maidData = new CompoundTag();
            maidData.putString("id", "touhou_little_maid:maid");
            maidData.putString(EntityMaid.MODEL_ID_TAG,
                    crop.getModelId() == null ? randomModelId(crop.getLevel()) : crop.getModelId());
            maidData.putBoolean(EntityMaid.IS_YSM_MODEL_TAG, false);
            photo.getOrCreateTag().put("MaidInfo", maidData);
            drops.add(photo);
        }
        return drops;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && state.getBlock() != oldState.getBlock()
                && state.is(ModBlocks.MAID_CROP_BLOCK.get()) && !movedByPiston
                && level.getBlockEntity(pos) instanceof MaidCropBlockEntity crop) {
            crop.setModelId(randomModelId(level));
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static String randomModelId(@Nullable Level level) {
        if (level == null) {
            return MaidCropBlockEntity.DEFAULT_MODEL_ID;
        }
        int count = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelSize();
        if (count <= 0) {
            return MaidCropBlockEntity.DEFAULT_MODEL_ID;
        }
        int skip = level.getRandom().nextInt(count);
        Optional<String> selected = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet().stream()
                .skip(skip).findFirst();
        return selected.orElse(MaidCropBlockEntity.DEFAULT_MODEL_ID);
    }

    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        int age = Math.min(getAge(state) + getBonemealAgeIncrease(level), getMaxAge());
        level.setBlock(pos, getStateForAge(age).setValue(HorizontalDirectionalBlock.FACING,
                state.getValue(HorizontalDirectionalBlock.FACING)), Block.UPDATE_CLIENTS);
    }
}
