package com.github.JumDa5he.callresponse.compat.block;

import com.github.tartaricacid.touhoulittlemaid.api.task.ISpecialCropHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class MaidCropSpecialCropHandler implements ISpecialCropHandler {
    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState, boolean isDestroyMode) {
        ISpecialCropHandler.super.harvest(maid, cropPos, cropState, true);
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        return cropState.is(ModBlocks.MAID_CROP_BLOCK.get())
                && cropState.getValue(MaidCropBlock.AGE) == MaidCropBlock.MAX_AGE;
    }
}
