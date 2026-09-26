package com.github.JumDa5he.callresponse.compat.sign.client;

import com.github.JumDa5he.callresponse.compat.sign.MaidSignData;
import com.github.JumDa5he.callresponse.compat.sign.MaidSignManager;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Gecko 模型（以及 TLM 会把 Gecko layer 复制过去的 YSM 模型）身上挂的示众牌。
 * <p>挂在“背旗”骨位上（没有该骨位的模型退化到头部骨位），再挪到女仆身前。
 */
public class MaidSignLayer<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {
    /** 挂点相对骨位的偏移，进游戏按模型微调。骨位空间里 -Z 是女仆正前方，所以“挂前面”用负值。 */
    private static final double OFFSET_X = 0.0;
    private static final double OFFSET_Y = 0;
    private static final double OFFSET_Z = -0.2;

    private final MaidSignRenderHelper helper = new MaidSignRenderHelper();

    public MaidSignLayer(R entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRendererIn) {
        return new MaidSignLayer<>(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T entity,
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
        ILocationModel model = getLocationModel(entity);
        if (model == null) {
            return;
        }
        List<? extends ILocationBone> bones = model.backpackBones();
        if (bones.isEmpty()) {
            bones = model.headBones();
        }
        if (bones.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        RenderUtils.prepMatrixForLocator(poseStack, bones);
        poseStack.translate(OFFSET_X, OFFSET_Y, OFFSET_Z);
        MaidSignRenderHelper.enterGeoSpace(poseStack);
        this.helper.render(poseStack, bufferSource, packedLight, data);
        poseStack.popPose();
    }
}
