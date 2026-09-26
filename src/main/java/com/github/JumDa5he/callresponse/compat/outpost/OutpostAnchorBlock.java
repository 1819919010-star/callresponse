package com.github.JumDa5he.callresponse.compat.outpost;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 每个复仇女仆营地一个的结构锚点；只用于生成期标记和运行时定位。 */
public final class OutpostAnchorBlock extends BaseEntityBlock {
    public OutpostAnchorBlock() {
        this(Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.GOLD)
                .strength(-1.0F, 3_600_000.0F)
                .noCollission()
                .noOcclusion()
                .isValidSpawn((state, level, pos, entity) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false));
    }

    private OutpostAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(OutpostAnchorBlock::new);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OutpostMarkerBlockEntity(pos, state);
    }
}
