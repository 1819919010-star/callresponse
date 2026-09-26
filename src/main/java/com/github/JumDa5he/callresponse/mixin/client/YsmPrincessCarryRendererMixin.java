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
@Mixin(targets = "com.elfmcys.yesstevemodel.OO0OOoo0ooooOoO0O0o00Ooo", remap = false)
public abstract class YsmPrincessCarryRendererMixin {
    private static final String RENDER_METHOD =
            "oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;" +
            "Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String CALCULATE_POSE =
            "Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;" +
            "oOoo00O0o0oO0o0oO00OO0O0(F)Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;";

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
