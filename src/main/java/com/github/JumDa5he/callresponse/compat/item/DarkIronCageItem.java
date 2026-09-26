package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.compat.cage.CageOrigin;
import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlock;
import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** 铁笼物品除正常放置外，还能直接套住非玩家生物。 */
public final class DarkIronCageItem extends BlockItem {
    public DarkIronCageItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        // 普通生物普通右键和潜行右键都可抓；玩家必须潜行右键才允许抓捕。
        if (!target.isAlive() || target.isSpectator()
                || (target instanceof Player && !player.isShiftKeyDown())) {
            return InteractionResult.PASS;
        }
        BlockPos lower = target.blockPosition();
        if (!DarkIronCageBlock.canPlaceComplete(player.level(), lower)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel level = (ServerLevel) player.level();
        DarkIronCageBlock.placeComplete(level, lower, player.getDirection().getOpposite(), CageOrigin.NORMAL);
        if (!(level.getBlockEntity(lower) instanceof DarkIronCageBlockEntity cage)
                || !cage.capture(target, player, CageOrigin.NORMAL)) {
            // 极少数并发情况下收容失败，完整回滚上下两格，物品不扣除。
            level.removeBlock(lower.above(), false);
            level.removeBlock(lower, false);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, lower, SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }
}
