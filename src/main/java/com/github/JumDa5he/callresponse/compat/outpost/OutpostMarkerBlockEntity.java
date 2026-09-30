package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 营地技术标记实体。
 *
 * <p>本身不保存业务数据；它的存在让区块加载时可以只遍历 BlockEntity，
 * 就能低成本找到锚点和女仆出生点，而不需要扫描整个结构包围盒。</p>
 */
public final class OutpostMarkerBlockEntity extends BlockEntity {
    public OutpostMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.OUTPOST_MARKER_ENTITY.get(), pos, state);
    }

    public boolean isAnchor() {
        return getBlockState().is(ModBlocks.OUTPOST_ANCHOR.get());
    }

    public OutpostMarkerRole spawnRole() {
        if (!getBlockState().is(ModBlocks.OUTPOST_SPAWN_MARKER.get())) {
            return OutpostMarkerRole.NONE;
        }
        return getBlockState().getValue(OutpostSpawnMarkerBlock.ROLE);
    }
}
