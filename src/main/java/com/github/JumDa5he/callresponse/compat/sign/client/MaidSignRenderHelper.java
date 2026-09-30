package com.github.JumDa5he.callresponse.compat.sign.client;

import com.github.JumDa5he.callresponse.compat.sign.MaidSignData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * 女仆身上示众牌的实际绘制。
 * <p>板面和文字都交给原版 {@code SignRenderer}，所以排版、发光表现和告示牌方块完全一致。
 * <p>两条渲染路径（Gecko/YSM 的骨位空间、原版 RenderLayer 的实体空间）坐标系不一样，
 * 这里各给一个进入方法，调用方只要先摆好挂点位置即可。
 */
public final class MaidSignRenderHelper {
    /** 牌子的整体缩放，进游戏按模型微调。 */
    private static final float SIGN_SCALE = 0.6F;
    /** 牌子倾斜角，纯粹为了贴在身上自然一点。 */
    private static final float TILT_DEGREES = 5.0F;
    /**
     * 原版牌面中心在牌子本地空间里的位置。
     * {@code SignRenderer} 内部自带 (0.5, 0.5, 0.5) 的平移和 0.6667 的缩放，
     * 板面中心大致落在 (0.5, 0.52, 0.0625)，这里补偿掉让牌子中心对准挂点。
     */
    private static final double ANCHOR_X = 0.5;
    private static final double ANCHOR_Y = 0.52;
    private static final double ANCHOR_Z = 0.0625;

    private final MaidSignBlockEntity holder = new MaidSignBlockEntity(Items.OAK_SIGN, new SignText());
    private BlockEntityRenderer<SignBlockEntity> renderer;
    private MaidSignData renderedData;

    /**
     * Gecko（以及被 TLM 转接过去的 YSM）骨位空间：X、Z 反向，Y 朝上，
     * 所以原版 y 朝上的告示牌模型用 (-S, S, -S) 就能正立。
     */
    public static void enterGeoSpace(PoseStack poseStack) {
        poseStack.scale(-SIGN_SCALE, SIGN_SCALE, -SIGN_SCALE);
        poseStack.mulPose(Axis.XN.rotationDegrees(TILT_DEGREES));
        poseStack.translate(-ANCHOR_X, -ANCHOR_Y, -ANCHOR_Z);
    }

    /**
     * 原版 RenderLayer 的实体空间：X、Y 反向，Z 保持，
     * 所以这里用 (S, -S, -S)。
     */
    public static void enterVanillaSpace(PoseStack poseStack) {
        poseStack.scale(SIGN_SCALE, -SIGN_SCALE, -SIGN_SCALE);
        poseStack.mulPose(Axis.XN.rotationDegrees(TILT_DEGREES));
        poseStack.translate(-ANCHOR_X, -ANCHOR_Y, -ANCHOR_Z);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, MaidSignData data) {
        if (this.renderer == null) {
            this.renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(this.holder);
            if (this.renderer == null) {
                return;
            }
        }
        if (this.renderedData != data) {
            // 附件同步时数据整体替换，这里按引用比较就够
            this.holder.setSignText(data.text().setHasGlowingText(true));
            // 牌面材质跟着挂上去的那件物品走
            this.holder.setSignState(MaidSignBlockEntity.wallSignState(data.item()));
            this.renderedData = data;
        }
        // 发光文字由 SignRenderer 内部用满亮度绘制，与光照无关
        this.renderer.render(this.holder, 0.0F, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
