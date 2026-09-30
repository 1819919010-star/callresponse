package com.github.JumDa5he.callresponse.compat.outpost;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/** 女仆出生点标记；role 在结构模板里直接写好。 */
public final class OutpostSpawnMarkerBlock extends BaseEntityBlock {
    public static final EnumProperty<OutpostMarkerRole> ROLE =
            EnumProperty.create("role", OutpostMarkerRole.class);

    public OutpostSpawnMarkerBlock() {
        this(Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE)
                .strength(-1.0F, 3_600_000.0F)
                .noCollission()
                .noOcclusion()
                .isValidSpawn((state, level, pos, entity) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false));
    }

    private OutpostSpawnMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROLE, OutpostMarkerRole.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(OutpostSpawnMarkerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROLE);
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
