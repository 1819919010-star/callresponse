package com.github.JumDa5he.callresponse.compat.client.renderer;

import com.github.JumDa5he.callresponse.compat.block.MaidCropBlockEntity;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;

import static com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil.clearMaidDataResidue;

public class MaidCropBlockRenderer implements BlockEntityRenderer<MaidCropBlockEntity, MaidCropBlockRenderer.MaidCropRenderState> {
    private final EntityRenderDispatcher dispatcher;

    public MaidCropBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.entityRenderer();
    }

    @Override
    public MaidCropRenderState createRenderState() {
        return new MaidCropRenderState();
    }

    @Override
    public void extractRenderState(MaidCropBlockEntity blockEntity, MaidCropRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPos, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        state.cropAge = blockEntity.getBlockState().getValue(CropBlock.AGE);
        state.entityRenderState = null;

        String modelId = blockEntity.getModelID();
        if (modelId == null) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        long posId = blockEntity.getBlockPos().asLong();
        EntityMaid maid;
        try {
            maid = EntityCacheUtil.STATUE_CACHE.get(posId, () -> new EntityMaid(level));
        } catch (ExecutionException e) {
            return;
        }
        clearMaidDataResidue(maid, true);
        maid.renderState = MaidRenderState.GARAGE_KIT;
        maid.tickCount = 0;
        maid.setModelId(modelId);
        state.entityRenderState = this.dispatcher.extractEntity(maid, partialTick);
        state.entityRenderState.lightCoords = state.lightCoords;
    }

    @Override
    public void submit(MaidCropRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.entityRenderState == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, -1.5 + 0.9 * (state.cropAge / 7.0), 0.5);
        switch (state.facing) {
            case EAST:
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
                break;
            case WEST:
                poseStack.mulPose(Axis.YP.rotationDegrees(270));
                break;
            case SOUTH:
                break;
            case NORTH:
            default:
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                break;
        }
        this.dispatcher.submit(state.entityRenderState, camera, 0, 0, 0, poseStack, collector);
        poseStack.popPose();
    }

    public static class MaidCropRenderState extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public int cropAge;
        public @Nullable EntityRenderState entityRenderState;
    }
}