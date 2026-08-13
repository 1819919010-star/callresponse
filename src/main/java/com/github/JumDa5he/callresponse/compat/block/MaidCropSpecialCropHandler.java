package com.github.JumDa5he.callresponse.compat.block;

import com.github.tartaricacid.touhoulittlemaid.api.task.ISpecialCropHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MaidCropSpecialCropHandler implements ISpecialCropHandler {
    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState, boolean isDestroyMode) {
        ISpecialCropHandler.super.harvest(maid, cropPos, cropState, true);
        if(!maid.level().isClientSide && maid.level().getBlockEntity(cropPos) instanceof MaidCropBlockEntity blockEntity){
            var newMaid = new EntityMaid(maid.level());
            newMaid.setPos(cropPos.getCenter().add(0, -0.5, 0));
            newMaid.setModelId(blockEntity.getModelID() == null ? MaidCropBlockEntity.DEFAULT_MODEL_ID : blockEntity.getModelID());
            maid.level().addFreshEntity(newMaid);
        }
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        return cropState.is(ModBlocks.MAID_CROP_BLOCK) && cropState.getValue(MaidCropBlock.AGE) == MaidCropBlock.MAX_AGE;
    }
}
