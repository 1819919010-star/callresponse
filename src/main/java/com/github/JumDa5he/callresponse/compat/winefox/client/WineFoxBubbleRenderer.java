package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxDialogue;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntity;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.EntityGraphics;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.IChatBubbleRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.implement.TextChatBubbleRenderer;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;

import java.util.HashMap;
import java.util.Map;

/** Adapts only TLM's text drawing primitives, without an EntityMaid or a maid event/packet. */
@OnlyIn(Dist.CLIENT)
final class WineFoxBubbleRenderer {
    private final Map<Integer, TextChatBubbleRenderer> lines = new HashMap<>();
    private Language language;

    void clear() { lines.clear(); language = null; }

    private TextChatBubbleRenderer layout(int line) {
        // Language/resource reload may replace fonts/translations while this renderer is retained.
        if (language != Language.getInstance()) {
            lines.clear();
            language = Language.getInstance();
        }
        return lines.computeIfAbsent(line, key -> new TextChatBubbleRenderer(
                Component.translatable(WineFoxDialogue.translationKey(key)), IChatBubbleData.TYPE_2,
                IChatBubbleRenderer.Position.CENTER));
    }

    void render(WineFoxEntity fox, float partialTick, PoseStack pose, MultiBufferSource buffer,
                int light, EntityRenderDispatcher dispatcher) {
        int line = fox.getDialogueLine();
        long age = fox.level().getGameTime() - fox.getDialogueStart();
        if (line < 1 || line > WineFoxDialogue.LINE_COUNT || age < 0 || age >= WineFoxDialogue.DISPLAY_TICKS
                || fox.isInvisible() || !MaidConfig.GLOBAL_MAID_SHOW_CHAT_BUBBLE.get()
                || !ClientHooks.isNameplateInRenderDistance(fox, dispatcher.distanceToSqr(fox))) return;
        TextChatBubbleRenderer text = layout(line);
        int width = text.getWidth() + 10, height = text.getHeight() + 10;
        pose.pushPose();
        pose.translate(0, fox.getBbHeight() + 0.35, 0);
        pose.mulPose(dispatcher.cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        // TLM 1.5.3's text renderer and these graphics methods never read the maid/renderer arguments.
        // Null means there is no maid, rather than manufacturing a proxy or pretending strict IMaid access.
        EntityGraphics graphics = new EntityGraphics(buffer, pose, null, light, partialTick);
        RenderSystem.enableDepthTest();
        graphics.blitNineSliced(IChatBubbleData.TYPE_2, -width / 2, -height, width, height, 8, 8, 48, 24, 0, 0);
        pose.translate(0, 0, -0.01);
        graphics.blit(IChatBubbleData.TYPE_2, -8, -8, 16, 24, 16, 16);
        pose.translate(-width / 2d + 5, -height + 5, -0.01);
        text.render(null, graphics);
        pose.popPose();
    }
}
