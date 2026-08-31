package com.github.JumDa5he.callresponse.compat.game.client;

import com.github.JumDa5he.callresponse.compat.game.BoardGameManager;
import com.github.tartaricacid.touhoulittlemaid.blockentity.BlockEntityJoy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Replaces TLM's single-player result text with the actual two-seat winner. */
public final class BoardGameWinnerRenderer {
    private static final Map<BlockPos, Component> WINNER_TEXT = new ConcurrentHashMap<>();

    private BoardGameWinnerRenderer() {
    }

    /** Called while the block entity is still available, before TLM submits its immutable render state. */
    public static void capture(BlockEntityJoy tile) {
        Component winner = BoardGameManager.getWinnerDisplayText(tile);
        if (winner == null) {
            WINNER_TEXT.remove(tile.getBlockPos());
        } else {
            WINNER_TEXT.put(tile.getBlockPos().immutable(), winner);
        }
    }

    public static boolean render(BlockPos pos, boolean inTipsRenderDistance, int chessCounter,
                                 String resetKey, Font font, PoseStack poseStack,
                                 SubmitNodeCollector collector, CameraRenderState camera, int packedLight) {
        Component winnerTips = WINNER_TEXT.get(pos);
        if (winnerTips == null || !inTipsRenderDistance) {
            return false;
        }

        MutableComponent resetTips = Component.translatable(resetKey)
                .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.AQUA);
        MutableComponent roundText = Component.translatable(
                "message.touhou_little_maid.gomoku.round", chessCounter)
                .withStyle(ChatFormatting.WHITE);
        MutableComponent roundTips = Component.literal("? ").withStyle(ChatFormatting.GREEN)
                .append(roundText).append(Component.literal(" ?").withStyle(ChatFormatting.GREEN));

        FormattedCharSequence winnerSeq = winnerTips.getVisualOrderText();
        FormattedCharSequence resetSeq = resetTips.getVisualOrderText();
        FormattedCharSequence roundSeq = roundTips.getVisualOrderText();
        float winnerWidth = -font.width(winnerSeq) / 2f;
        float resetWidth = -font.width(resetSeq) / 2f;
        float roundWidth = -font.width(roundSeq) / 2f;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.75, 0.5);
        poseStack.mulPose(Axis.YN.rotationDegrees(180 + camera.yRot));
        poseStack.mulPose(Axis.XN.rotationDegrees(camera.xRot));
        poseStack.scale(0.03F, -0.03F, 0.03F);
        collector.submitText(poseStack, winnerWidth, -10, winnerSeq, true,
                Font.DisplayMode.POLYGON_OFFSET, packedLight, 0xFFFFFFFF, 0, 0);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        collector.submitText(poseStack, roundWidth, -30, roundSeq, true,
                Font.DisplayMode.POLYGON_OFFSET, packedLight, 0xFFFFFFFF, 0, 0);
        collector.submitText(poseStack, resetWidth, 0, resetSeq, true,
                Font.DisplayMode.POLYGON_OFFSET, packedLight, 0xFFFFFFFF, 0, 0);
        poseStack.popPose();
        return true;
    }
}
