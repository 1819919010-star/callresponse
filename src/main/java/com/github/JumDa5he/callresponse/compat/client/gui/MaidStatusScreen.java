package com.github.JumDa5he.callresponse.compat.client.gui;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;


public class MaidStatusScreen extends Screen {
    private static final Identifier MAID_GUI_SIDE =
            Identifier.fromNamespaceAndPath(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");

    private final Screen parent;
    private final EntityMaid maid;
    private int left;
    private int top;

    public MaidStatusScreen(Screen parent, EntityMaid maid) {
        super(Component.literal("女仆状态"));
        this.parent = parent;
        this.maid = maid;
    }

    @Override
    protected void init() {
        this.left = (this.width - 206) / 2;
        this.top = (this.height - 132) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(this.left + 126, this.top + 98, 64, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

        graphics.fill(left, top, left + 206, top + 132, 0xE8202028);
        graphics.fill(left + 1, top + 1, left + 205, top + 131, 0xF03A3444);
        graphics.fill(left + 4, top + 4, left + 202, top + 128, 0xD0101018);

        graphics.centeredText(this.font, this.title, this.left + 103, this.top + 13, 0xFFE8C776);
        graphics.centeredText(this.font, this.maid.getDisplayName(), this.left + 103, this.top + 28, 0xFFFFFFFF);

        int hunger = Math.round(HungerData.get(maid));
        EmotionData.EmotionValues emotions = getEmotions();
        drawStatusBar(graphics, Component.literal("饥饿"), hunger, 100, top + 47, 18);
        drawStatusBar(graphics, Component.literal("信任"), emotions.trust(), 100, top + 65, 28);
        drawStatusBar(graphics, Component.literal("恐惧"), emotions.fear(), 100, top + 83, 23);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private EmotionData.EmotionValues getEmotions() {
        if (Minecraft.getInstance().player == null) {
            return EmotionData.EmotionValues.DEFAULT;
        }
        return EmotionData.get(maid, Minecraft.getInstance().player.getUUID());
    }


    private void drawStatusBar(GuiGraphicsExtractor graphics, Component name, int value, int maximum, int y, int fillV) {
        int clamped = Math.max(0, Math.min(maximum, value));
        int barX = left + 40;
        graphics.text(this.font, name, left + 14, y + 1, ChatFormatting.WHITE.getColor(), false);
        graphics.blit(RenderPipelines.GUI_TEXTURED, MAID_GUI_SIDE, barX, y, 0.0F, 9.0F, 47, 9, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, MAID_GUI_SIDE, barX + 2, y + 2, 2.0F, (float) fillV, (int) (43 * (clamped / (double) maximum)), 5, 256, 256);
        String amount = clamped + " / " + maximum;
        graphics.text(this.font, amount, barX + 53, y + 1, 0xFFE0E0E0, false);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
