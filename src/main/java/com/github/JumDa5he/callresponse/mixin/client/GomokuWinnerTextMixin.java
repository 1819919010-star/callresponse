package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.game.client.BoardGameWinnerRenderer;
import com.github.tartaricacid.touhoulittlemaid.blockentity.BlockEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.blockentity.GomokuRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.blockentity.state.GomokuRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GomokuRenderer.class)
public abstract class GomokuWinnerTextMixin {
    @Shadow(remap = false) @Final private Font font;

    @Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
    private void callresponse$captureWinner(BlockEntityGomoku tile, GomokuRenderState state,
                                            float partialTicks, Vec3 cameraPosition,
                                            ModelFeatureRenderer.CrumblingOverlay breakProgress,
                                            CallbackInfo ci) {
        BoardGameWinnerRenderer.capture(tile);
    }

    @Inject(method = "renderTipsText", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$renderActualWinner(GomokuRenderState state, PoseStack poseStack,
                                                 SubmitNodeCollector collector, CameraRenderState camera,
                                                 CallbackInfo ci) {
        if (BoardGameWinnerRenderer.render(state.blockPos, state.inTipsRenderDistance, state.chessCounter,
                "message.touhou_little_maid.gomoku.reset", font, poseStack, collector, camera, state.lightCoords)) {
            ci.cancel();
        }
    }
}
