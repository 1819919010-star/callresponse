package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampManager;

/**
 * 营地技术标记实体。
 *
 * <p>本身不保存业务数据；它的存在让区块加载时可以只遍历 BlockEntity，
 * 就能低成本找到锚点和女仆出生点，而不需要扫描整个结构包围盒。</p>
 */
public final class OutpostMarkerBlockEntity extends BlockEntity {
    private String campType = "";
    private int headHeight = 2;
    private int age;
    public OutpostMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.OUTPOST_MARKER_ENTITY.get(), pos, state);
    }

    public boolean isAnchor() {
        return getBlockState().is(ModBlocks.OUTPOST_ANCHOR.get()) && !isWanderingCamp();
    }

    public boolean isWanderingCamp() { return "wandering".equals(campType); }
    public BlockPos beddingHead() { return getBlockPos().above(headHeight); }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        campType = tag.getString("CampType");
        headHeight = tag.contains("HeadHeight") ? Math.max(1, Math.min(4, tag.getInt("HeadHeight"))) : 2;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!campType.isEmpty()) { tag.putString("CampType", campType); tag.putInt("HeadHeight", headHeight); }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, OutpostMarkerBlockEntity anchor) {
        if (!anchor.isWanderingCamp() || ++anchor.age < 40 || anchor.age % 20 != 0) return;
        var ledger = com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampSavedData.get((ServerLevel)level);
        var record = ledger.entry(level.dimension().location()+"@"+anchor.beddingHead().asLong());
        if (record.getBoolean("Spawned") || record.getInt("Attempts") >= 3) return;
        for (int x : new int[]{-4,4}) for (int z : new int[]{-4,4})
            if (!level.hasChunkAt(pos.offset(x,0,z))) return;
        WanderingCampManager.generate((ServerLevel)level,pos,anchor.beddingHead(),state.getValue(OutpostAnchorBlock.FACING),false);
    }

    public OutpostMarkerRole spawnRole() {
        if (!getBlockState().is(ModBlocks.OUTPOST_SPAWN_MARKER.get())) {
            return OutpostMarkerRole.NONE;
        }
        return getBlockState().getValue(OutpostSpawnMarkerBlock.ROLE);
    }
}
