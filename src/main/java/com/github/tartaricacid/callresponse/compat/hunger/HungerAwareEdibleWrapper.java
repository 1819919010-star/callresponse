package com.github.tartaricacid.callresponse.compat.hunger;

import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class HungerAwareEdibleWrapper implements IMaidEdibleBlock {

    private final IMaidEdibleBlock delegate;

    public HungerAwareEdibleWrapper(IMaidEdibleBlock delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean shouldMoveTo(EntityMaid maid, BlockPos pos, BlockState state) {
        return delegate.shouldMoveTo(maid, pos, state);
    }

    @Override
    public int getFavorabilityPoints(EntityMaid maid, BlockPos pos, BlockState state) {
        return delegate.getFavorabilityPoints(maid, pos, state);
    }

    @Override
    public boolean consume(EntityMaid maid, BlockPos pos, BlockState state) {
        // 执行原逻辑
        boolean result = delegate.consume(maid, pos, state);

        // 增加饱食度（每口固定 +6，可根据需要调整）
        HungerData.add(maid, 6.0f);

        return result;
    }

    @Override
    public boolean canPlaceAsFood(EntityMaid maid, ItemStack stack, int slotIndex) {
        return delegate.canPlaceAsFood(maid, stack, slotIndex);
    }

    @Override
    public boolean shouldPlaceTo(EntityMaid maid, BlockPos pos, BlockState state, ItemStack stack) {
        return delegate.shouldPlaceTo(maid, pos, state, stack);
    }

    @Override
    public boolean placeAsFood(EntityMaid maid, BlockPos pos, ItemStack stack, int slotIndex) {
        return delegate.placeAsFood(maid, pos, stack, slotIndex);
    }
}