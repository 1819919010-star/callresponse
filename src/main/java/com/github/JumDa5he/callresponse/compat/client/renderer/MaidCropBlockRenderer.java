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
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.concurrent.ExecutionException;

import static com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil.clearMaidDataResidue;

public class MaidCropBlockRenderer implements BlockEntityRenderer<MaidCropBlockEntity> {
    @Override
    public void render(MaidCropBlockEntity blockEntity, float v, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int i1) {
        renderEntity(poseStack, multiBufferSource, blockEntity, i);
    }

    public void renderEntity(PoseStack poseStack, MultiBufferSource multiBufferSource, MaidCropBlockEntity blockEntity, int light){
        if(blockEntity.getModelID() == null)return;
        var level = blockEntity.getLevel();
        if(level == null)return;
        long posId = blockEntity.getBlockPos().asLong();
        EntityMaid maid;
        try {
            maid = EntityCacheUtil.STATUE_CACHE.get(posId, () -> new EntityMaid(level));
        } catch (ExecutionException e) {return;}

        clearMaidDataResidue(maid, true);
        maid.renderState = MaidRenderState.GARAGE_KIT;
        if (YsmCompat.isInstalled() && maid.isYsmModel()) {
            maid.tickCount = (int) level.getGameTime();
        } else {
            maid.tickCount = 0;
        }
        maid.setModelId(blockEntity.getModelID());

        poseStack.pushPose();
        poseStack.translate(-0.5, 0, -0.5);
        poseStack.translate(1, -1.5 + 0.9 * (blockEntity.getBlockState().getValue(CropBlock.AGE) / 7d), 1);
        switch (blockEntity.getBlockState().getValue(HorizontalDirectionalBlock.FACING)) {
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

        EntityRenderDispatcher render = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean isShowHitBox = render.shouldRenderHitBoxes();
        render.setRenderHitBoxes(false);
        render.render(maid, 0, 0, 0, 0, 0,
                poseStack, multiBufferSource, light);
        render.setRenderHitBoxes(isShowHitBox);
        poseStack.popPose();
    }
}
