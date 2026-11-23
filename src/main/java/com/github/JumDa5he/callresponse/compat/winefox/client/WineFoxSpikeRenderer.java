package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxSpike;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/** Untextured purple-black column: no external art, lightning entity, or client damage logic. */
@OnlyIn(Dist.CLIENT)
public final class WineFoxSpikeRenderer extends EntityRenderer<WineFoxSpike> {
    public WineFoxSpikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WineFoxSpike spike, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        Matrix4f matrix = poses.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        prism(vertices, matrix, WineFoxSpike.WIDTH / 2, WineFoxSpike.HEIGHT, 37, 5, 56, 245);
        prism(vertices, matrix, WineFoxSpike.WIDTH * 0.62F, WineFoxSpike.HEIGHT, 119, 24, 172, 105);
        super.render(spike, yaw, partialTick, poses, buffers, light);
    }

    private static void prism(VertexConsumer v, Matrix4f m, float r, float h, int red, int green, int blue, int alpha) {
        float[][] points = {{-r,0,-r},{r,0,-r},{r,0,r},{-r,0,r},
                {-r,h,-r},{r,h,-r},{r,h,r},{-r,h,r}};
        int[][] faces = {{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{4,5,6,7},{3,2,1,0}};
        for (int[] face : faces) for (int index : face) {
            float[] p = points[index];
            v.addVertex(m, p[0], p[1], p[2]).setColor(red, green, blue, alpha);
        }
    }

    @Override public ResourceLocation getTextureLocation(WineFoxSpike entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
