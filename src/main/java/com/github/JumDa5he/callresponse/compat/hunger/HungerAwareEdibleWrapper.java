package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.JumDa5he.callresponse.compat.api.event.hunger.MaidEatEvent;
import com.github.JumDa5he.callresponse.compat.bauble.BaubleDetector;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

public class HungerAwareEdibleWrapper implements IMaidEdibleBlock {

    private final IMaidEdibleBlock delegate;

    public HungerAwareEdibleWrapper(IMaidEdibleBlock delegate) {
        this.delegate = delegate;
    }

    // 禁食饰品：彻底禁止 edible 偷吃（不寻找目标、不食用、不放置食物）
    private static boolean isBlocked(EntityMaid maid) {
        return BaubleDetector.hasNoEat(maid);
    }

    @Override
    public boolean shouldMoveTo(EntityMaid maid, BlockPos pos, BlockState state) {
        if (isBlocked(maid)) return false;
        return delegate.shouldMoveTo(maid, pos, state);
    }

    @Override
    public int getFavorabilityPoints(EntityMaid maid, BlockPos pos, BlockState state) {
        return delegate.getFavorabilityPoints(maid, pos, state);
    }

    @Override
    public boolean consume(EntityMaid maid, BlockPos pos, BlockState state) {
        if (isBlocked(maid)) return false;
        // 执行原逻辑
        boolean result = delegate.consume(maid, pos, state);

        // 增加饱食度（每口固定 +6，可根据需要调整）
        HungerData.add(maid, NeoForge.EVENT_BUS.post(new MaidEatEvent.Block(maid, pos, state, 6)).getHunger());

        return result;
    }

    @Override
    public boolean canPlaceAsFood(EntityMaid maid, ItemStack stack, int slotIndex) {
        if (isBlocked(maid)) return false;
        return delegate.canPlaceAsFood(maid, stack, slotIndex);
    }

    @Override
    public boolean shouldPlaceTo(EntityMaid maid, BlockPos pos, BlockState state, ItemStack stack) {
        if (isBlocked(maid)) return false;
        return delegate.shouldPlaceTo(maid, pos, state, stack);
    }

    @Override
    public boolean placeAsFood(EntityMaid maid, BlockPos pos, ItemStack stack, int slotIndex) {
        if (isBlocked(maid)) return false;
        return delegate.placeAsFood(maid, pos, stack, slotIndex);
    }
}
