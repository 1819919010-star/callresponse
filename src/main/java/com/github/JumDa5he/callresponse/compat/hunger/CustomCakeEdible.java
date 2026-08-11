package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.JumDa5he.callresponse.compat.bauble.BaubleDetector;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;

import static com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock.belowIsSnackStand;

public class CustomCakeEdible implements IMaidEdibleBlock {

    // 禁食饰品：禁止偷吃方块食物（不寻找目标、不食用、不放置）
    private static boolean isBlocked(EntityMaid maid) {
        return BaubleDetector.hasNoEat(maid);
    }

    @Override
    public boolean shouldMoveTo(EntityMaid maid, BlockPos pos, BlockState state) {
        if (isBlocked(maid)) return false;
        if (state.is(Blocks.CAKE)) {
            return belowIsSnackStand(maid, pos);
        }
        return false;
    }

    @Override
    public int getFavorabilityPoints(EntityMaid maid, BlockPos pos, BlockState state) {
        return 1;
    }

    @Override
    public boolean consume(EntityMaid maid, BlockPos pos, BlockState state) {
        if (isBlocked(maid)) return false;
        int bites = state.getValue(CakeBlock.BITES);
        Level level = maid.level();
        if (bites < CakeBlock.MAX_BITES) {
            int currentBites = Math.min(bites + 1, CakeBlock.MAX_BITES);
            level.setBlock(pos, state.setValue(CakeBlock.BITES, currentBites), Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
        maid.spawnItemParticles(new ItemStack(Items.CAKE), 8);
        maid.playSound(SoundEvents.GENERIC_EAT);

        // ===== 增加饱食度（每口 +6） =====
        HungerData.add(maid, 6.0f);

        return true;
    }

    @Override
    public boolean canPlaceAsFood(EntityMaid maid, ItemStack stack, int slotIndex) {
        if (isBlocked(maid)) return false;
        return stack.is(Items.CAKE);
    }
}