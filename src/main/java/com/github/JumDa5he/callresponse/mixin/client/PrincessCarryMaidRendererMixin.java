package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让“女仆抱女仆”也使用 TLM 原版玩家抱女仆的同一组横抱变换。 */
@Mixin(EntityMaidRenderer.class)
public abstract class PrincessCarryMaidRendererMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"), remap = false)
    private void callresponse$applyPrincessCarryPose(Mob mob, PoseStack poseStack, float ageInTicks,
                                                     float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (!(mob instanceof EntityMaid carried) || !(carried.getVehicle() instanceof EntityMaid carrier)
                || !PrincessCarryManager.isMaidCarrySession(carrier, carried)) {
            return;
        }
        poseStack.translate(-0.375D, 0.8325D, 0.375D);
        poseStack.mulPose(Axis.ZN.rotationDegrees(65.0F));
        poseStack.mulPose(Axis.YN.rotationDegrees(-80.0F));
    }
}
