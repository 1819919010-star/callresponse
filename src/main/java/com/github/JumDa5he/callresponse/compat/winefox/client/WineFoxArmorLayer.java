package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;

import java.util.LinkedList;
import java.util.List;

/** Native equipment meshes attached to this model's actual animated bones. */
@OnlyIn(Dist.CLIENT)
public final class WineFoxArmorLayer extends GeoLayerRenderer<WineFoxEntity, WineFoxRenderer> {
    private final EntityModelSet models;
    private final HumanoidModel<WineFoxEntity> armor;

    public WineFoxArmorLayer(WineFoxRenderer renderer, EntityModelSet models) {
        super(renderer);
        this.models = models;
        this.armor = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public GeoLayerRenderer<WineFoxEntity, WineFoxRenderer> copy(WineFoxRenderer renderer) {
        return new WineFoxArmorLayer(renderer, models);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffer, int light, WineFoxEntity fox,
                       float limbSwing, float limbAmount, float partialTick, float age,
                       float headYaw, float headPitch) {
        if (fox.isInvisible()) return;
        AnimatedGeoModel model = entityRenderer.getAnimatableEntity(fox).getCurrentModel();
        if (model == null) return;
        ItemStack head = fox.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof ArmorItem item && item.getEquipmentSlot() == EquipmentSlot.HEAD) {
            renderPart(poses, buffer, light, fox, head, EquipmentSlot.HEAD, model, "Head", armor.head,
                    0, 0.9F, 0.9F, 0.9F);
        }
        ItemStack chest = fox.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof ArmorItem item && item.getEquipmentSlot() == EquipmentSlot.CHEST) {
            // UpperBody pivot is 23.9644; the mesh's shoulder line is 29.1 model pixels.
            renderPart(poses, buffer, light, fox, chest, EquipmentSlot.CHEST, model, "UpperBody", armor.body,
                    (29.1F - 23.9644F) / 16, 1.05F, 0.8F, 1.3F);
            renderPart(poses, buffer, light, fox, chest, EquipmentSlot.CHEST, model, "RightArm", armor.rightArm,
                    0, 0.9F, 0.65F, 1.0F);
            renderPart(poses, buffer, light, fox, chest, EquipmentSlot.CHEST, model, "LeftArm", armor.leftArm,
                    0, 0.9F, 0.65F, 1.0F);
        }
    }

    private void renderPart(PoseStack poses, MultiBufferSource buffer, int light, WineFoxEntity fox,
                            ItemStack stack, EquipmentSlot slot, AnimatedGeoModel model, String boneName,
                            ModelPart part, float offsetY, float scaleX, float scaleY, float scaleZ) {
        List<AnimatedGeoBone> bones = hierarchy(model, boneName);
        if (bones.isEmpty()) return;
        poses.pushPose();
        try {
            if (RenderUtils.prepMatrixForLocator(poses, bones)) return;
            poses.translate(0, offsetY, 0);
            // Convert vanilla model axes to the existing Gecko model axes.
            poses.scale(-scaleX, -scaleY, scaleZ);
            part.resetPose();
            part.setPos(0, 0, 0);
            part.xRot = part.yRot = part.zRot = 0;
            part.visible = true;
            ArmorItem item = (ArmorItem) stack.getItem();
            // Kept available for model tooling, but deliberately not added to the Wine Fox renderer.
            for (var layer : item.getMaterial().value().layers()) {
                ResourceLocation texture = ClientHooks.getArmorTexture(fox, stack, layer, false, slot);
                part.render(poses, buffer.getBuffer(RenderType.armorCutoutNoCull(texture)), light,
                        OverlayTexture.NO_OVERLAY, -1);
            }
            if (stack.hasFoil()) part.render(poses, buffer.getBuffer(RenderType.armorEntityGlint()), light,
                    OverlayTexture.NO_OVERLAY, -1);
        } finally {
            poses.popPose();
        }
    }

    public static List<AnimatedGeoBone> hierarchy(AnimatedGeoModel model, String name) {
        LinkedList<AnimatedGeoBone> result = new LinkedList<>();
        AnimatedGeoBone bone = model.bones().get(name);
        while (bone != null) {
            result.addFirst(bone);
            bone = bone.geoBone().parent() == null ? null : model.bones().get(bone.geoBone().parent().name());
        }
        return result;
    }
}
