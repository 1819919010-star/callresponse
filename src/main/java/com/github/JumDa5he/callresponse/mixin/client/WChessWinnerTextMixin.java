package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.game.client.BoardGameWinnerRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityWChessRenderer;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityWChessRenderer.class)
public abstract class WChessWinnerTextMixin {
    @Shadow(remap = false) @Final private Font font;

    @Inject(method = "renderTipsText", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$renderActualWinner(TileEntityWChess tile, PoseStack poseStack,
                                                 MultiBufferSource buffers, int packedLight,
                                                 CallbackInfo ci) {
        if (BoardGameWinnerRenderer.render(tile, font, poseStack, buffers, packedLight)) ci.cancel();
    }
}
