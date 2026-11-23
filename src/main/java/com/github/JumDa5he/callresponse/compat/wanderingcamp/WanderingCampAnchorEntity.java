package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WanderingCampAnchorEntity extends BlockEntity {
    private int age;
    public WanderingCampAnchorEntity(BlockPos p, BlockState s) { super(ModBlocks.WANDERING_CAMP_ANCHOR_ENTITY.get(), p, s); }
    public static void tick(Level level, BlockPos pos, BlockState state, WanderingCampAnchorEntity marker) {
        // The chunk's block entities run only after worldgen placement. Check loaded adjacent chunks, never load them.
        if (++marker.age < 40 || marker.age % 20 != 0) return;
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-4, 4})
            if (!level.hasChunkAt(pos.offset(x, 0, z))) return;
        WanderingCampManager.generate((ServerLevel) level, pos, state.getValue(WanderingCampAnchorBlock.FACING));
    }
}
