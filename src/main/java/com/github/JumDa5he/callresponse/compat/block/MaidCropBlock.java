package com.github.JumDa5he.callresponse.compat.block;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class MaidCropBlock extends CropBlock implements EntityBlock {
    public MaidCropBlock() {
        super(Properties.ofFullCopy(Blocks.WHEAT));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new MaidCropBlockEntity(blockPos, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.MAID_SEED.get();
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if(state.getValue(CropBlock.AGE) == getMaxAge() && params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MaidCropBlockEntity blockEntity) {
            var r = new ArrayList<>(super.getDrops(state, params));
            var photo = new ItemStack(ModItems.FREE_PHOTO.get());
            var data = new CompoundTag();
            data.putString(EntityMaid.MODEL_ID_TAG, blockEntity.getModelID() == null ? randomID(blockEntity.getLevel()) : blockEntity.getModelID());
            data.putBoolean(EntityMaid.IS_YSM_MODEL_TAG, false);
            photo.set(InitDataComponent.MAID_INFO, CustomData.of(data));
            r.add(photo);
            return r;
        }
        return super.getDrops(state, params);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if(!level.isClientSide &&
                state.getBlock() != oldState.getBlock() &&
                state.is(ModBlocks.MAID_CROP_BLOCK) &&
                !movedByPiston && level.getBlockEntity(pos) instanceof MaidCropBlockEntity blockEntity)
            blockEntity.setModelID(randomID(level));
    }

    public static String randomID(Level level){
        var count = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelSize();
        if(count <= 0)return MaidCropBlockEntity.DEFAULT_MODEL_ID;
        int skipRandom = level.getRandom().nextInt(count);
        Optional<String> modelId = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet().stream()
                .skip(skipRandom).findFirst();
        return modelId.orElse(MaidCropBlockEntity.DEFAULT_MODEL_ID);
    }

    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        int i = this.getAge(state) + this.getBonemealAgeIncrease(level);
        int j = this.getMaxAge();
        if (i > j) {
            i = j;
        }

        level.setBlock(pos, this.getStateForAge(i).setValue(FACING, state.getValue(FACING)), 2);
    }
}
