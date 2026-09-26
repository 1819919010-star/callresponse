package com.github.JumDa5he.callresponse.compat.sign.client;

import com.github.JumDa5he.callresponse.compat.sign.MaidSignData;
import com.github.JumDa5he.callresponse.compat.sign.MaidSignManager;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;

/**
 * 非 Gecko 模型的女仆（老式 Bedrock/默认模型）身上挂的示众牌。
 * <p>这条路径走原版 {@code RenderLayer}，PoseStack 是实体空间，和 Gecko 骨位空间不同。
 */
public class MaidSignVanillaLayer extends RenderLayer<Mob, BedrockModel<Mob>> {
    /** 和背旗同高，再挪到女仆身前；-Z 是正前方。进游戏按模型微调。 */
    private static final double OFFSET_X = 0.0;
    private static final double OFFSET_Y = 1.03;
    private static final double OFFSET_Z = -0.47;

    private final MaidSignRenderHelper helper = new MaidSignRenderHelper();

    public MaidSignVanillaLayer(RenderLayerParent<Mob, BedrockModel<Mob>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Mob entity,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        EntityMaid maid = IMaid.convertToMaid(entity);
        if (maid == null || entity.isInvisible() || entity.isSleeping()) {
            return;
        }
        MaidSignData data = MaidSignManager.get(maid);
        if (!data.enabled()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(OFFSET_X, OFFSET_Y, OFFSET_Z);
        MaidSignRenderHelper.enterVanillaSpace(poseStack);
        this.helper.render(poseStack, bufferSource, packedLight, data);
        poseStack.popPose();
    }
}
