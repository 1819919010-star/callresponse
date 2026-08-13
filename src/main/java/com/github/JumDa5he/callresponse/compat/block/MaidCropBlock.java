package com.github.JumDa5he.callresponse.compat.block;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
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
        if (getAge(state) == getMaxAge()) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && !movedByPiston
                && getAge(state) == getMaxAge()
                && level.getBlockEntity(pos) instanceof MaidCropBlockEntity crop) {
            EntityMaid maid = new EntityMaid(level);
            maid.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            maid.setModelId(crop.getModelId() == null ? MaidCropBlockEntity.DEFAULT_MODEL_ID : crop.getModelId());
            level.addFreshEntity(maid);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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

    private static String randomModelId(Level level) {
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
