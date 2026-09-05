package com.github.JumDa5he.callresponse.compat.game.client;

import com.github.JumDa5he.callresponse.compat.game.BoardGameManager;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityJoy;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 仅替换呼应双席位棋局的胜者大字，其他本体结算提示仍由原渲染器处理。 */
public final class BoardGameWinnerRenderer {
    private static final int TIPS_RENDER_DISTANCE = 16;

    private BoardGameWinnerRenderer() {
    }

    public static boolean render(TileEntityJoy tile, Font font, PoseStack poseStack,
                                 MultiBufferSource buffers, int packedLight) {
        Component winnerTips = BoardGameManager.getWinnerDisplayText(tile);
        if (winnerTips == null) return false;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos pos = tile.getBlockPos();
        if (camera.getPosition().distanceToSqr(pos.getX(), pos.getY(), pos.getZ())
                >= TIPS_RENDER_DISTANCE * TIPS_RENDER_DISTANCE) return false;

        String resetKey = tile instanceof TileEntityGomoku
                ? "message.touhou_little_maid.gomoku.reset"
                : tile instanceof TileEntityCChess
                ? "message.touhou_little_maid.cchess.reset"
                : "message.touhou_little_maid.wchess.reset";
        MutableComponent resetTips = Component.translatable(resetKey)
                .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.AQUA);
        MutableComponent roundText = Component.translatable(
                "message.touhou_little_maid.gomoku.round", chessCounter(tile))
                .withStyle(ChatFormatting.WHITE);
        MutableComponent roundTips = Component.literal("⏹ ").withStyle(ChatFormatting.GREEN)
                .append(roundText).append(Component.literal(" ⏹").withStyle(ChatFormatting.GREEN));

        float winnerWidth = (float) (-font.width(winnerTips) / 2);
        float resetWidth = (float) (-font.width(resetTips) / 2);
        float roundWidth = (float) (-font.width(roundTips) / 2);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.75, 0.5);
        poseStack.mulPose(Axis.YN.rotationDegrees(180 + camera.getYRot()));
        poseStack.mulPose(Axis.XN.rotationDegrees(camera.getXRot()));
        poseStack.scale(0.03F, -0.03F, 0.03F);
        font.drawInBatch(winnerTips, winnerWidth, -10, 0xFFFFFF, true, poseStack.last().pose(),
                buffers, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        font.drawInBatch(roundTips, roundWidth, -30, 0xFFFFFF, true, poseStack.last().pose(),
                buffers, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        font.drawInBatch(resetTips, resetWidth, 0, 0xFFFFFF, true, poseStack.last().pose(),
                buffers, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poseStack.popPose();
        return true;
    }

    private static int chessCounter(TileEntityJoy tile) {
        if (tile instanceof TileEntityGomoku gomoku) return gomoku.getChessCounter();
        if (tile instanceof TileEntityCChess cChess) return cChess.getChessCounter();
        if (tile instanceof TileEntityWChess wChess) return wChess.getChessCounter();
        return 0;
    }
}
