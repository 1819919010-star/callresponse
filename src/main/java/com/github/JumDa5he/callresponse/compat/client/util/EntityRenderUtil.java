package com.github.JumDa5he.callresponse.compat.client.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EntityRenderUtil {
    private static final Vector3f ZERO = new Vector3f();

    private EntityRenderUtil() {
    }

    public static void renderMaid(GuiGraphicsExtractor graphics, EntityMaid maid, int centerX, int centerY,
                                  float scale, float yawDegrees) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(maid);
        EntityRenderState renderState = renderer.createRenderState(maid, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = 180.0F + yawDegrees;
            livingState.yRot = yawDegrees;
            livingState.xRot = 0.0F;
            livingState.boundingBoxWidth /= livingState.scale;
            livingState.boundingBoxHeight /= livingState.scale;
            livingState.scale = 1.0F;
        }

        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateY((float) Math.toRadians(yawDegrees));

        int halfW = (int) scale;
        int halfH = (int) (2.0F * scale);
        graphics.entity(renderState, scale, ZERO, pose, null,
                centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH);
    }
}