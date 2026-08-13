package com.github.JumDa5he.callresponse.compat.client.renderer;

import com.github.JumDa5he.callresponse.compat.block.MaidCropBlockEntity;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.compat.ysm.YsmCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.level.block.CropBlock;

import java.util.concurrent.ExecutionException;

import static com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil.clearMaidDataResidue;

/** Shows the selected maid model gradually rising out of the crop as it grows. */
public final class MaidCropBlockRenderer implements BlockEntityRenderer<MaidCropBlockEntity> {
    public MaidCropBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MaidCropBlockEntity crop, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (crop.getModelId() == null || crop.getLevel() == null) {
            return;
        }
        EntityMaid maid;
        try {
            maid = EntityCacheUtil.STATUE_CACHE.get(crop.getBlockPos().asLong(),
                    () -> new EntityMaid(crop.getLevel()));
        } catch (ExecutionException exception) {
            return;
        }

        clearMaidDataResidue(maid, true);
        maid.renderState = MaidRenderState.GARAGE_KIT;
        maid.tickCount = YsmCompat.isInstalled() && maid.isYsmModel()
                ? (int) crop.getLevel().getGameTime() : 0;
        maid.setModelId(crop.getModelId());

        poseStack.pushPose();
        poseStack.translate(0.5, -1.5 + 0.9 * (crop.getBlockState().getValue(CropBlock.AGE) / 7.0), 0.5);
        switch (crop.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            default -> {
            }
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        dispatcher.setRenderHitBoxes(false);
        dispatcher.render(maid, 0, 0, 0, 0, partialTick, poseStack, buffers, packedLight);
        dispatcher.setRenderHitBoxes(hitBoxes);
        poseStack.popPose();
    }
}
