package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.client.ysm.YsmPrincessCarryBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 YSM 计算原始姿势前恢复上一帧，计算后叠加被公主抱姿势。 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo", remap = false)
public abstract class YsmPrincessCarryRendererMixin {
    private static final String RENDER_METHOD =
            "Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/o0O0oOooOo0OoOo0oOo00O00;" +
            "Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String CALCULATE_POSE =
            "Lcom/elfmcys/yesstevemodel/o0O0oOooOo0OoOo0oOo00O00;" +
            "o0OOooo0o0OO00OoOOOo0o0O(F)Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;";

    @Inject(method = RENDER_METHOD, at = @At(value = "INVOKE", target = CALCULATE_POSE))
    private void callresponse$restorePrincessPose(@Coerce Object animatable, ResourceLocation texture,
                                                   float entityYaw, float partialTick, PoseStack poseStack,
                                                   MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        YsmPrincessCarryBridge.before(animatable);
    }

    @Inject(method = RENDER_METHOD,
            at = @At(value = "INVOKE", target = CALCULATE_POSE, shift = At.Shift.AFTER))
    private void callresponse$applyPrincessPose(@Coerce Object animatable, ResourceLocation texture,
                                                 float entityYaw, float partialTick, PoseStack poseStack,
                                                 MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        YsmPrincessCarryBridge.after(animatable);
    }
}
