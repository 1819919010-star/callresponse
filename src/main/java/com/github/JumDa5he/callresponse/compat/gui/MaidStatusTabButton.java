package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.gui.ITooltipButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


public class MaidStatusTabButton extends Button implements ITooltipButton {
    private static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");
    private static final int TAB_TEXTURE_X = 182;
    private final boolean sideLayout;

    public MaidStatusTabButton(int x, int y, boolean selected, boolean sideLayout, Button.OnPress onPress) {
        super(Button.builder(Component.empty(), onPress).pos(x, y).size(sideLayout ? 26 : 24, sideLayout ? 24 : 26));
        this.active = !selected;
        this.sideLayout = sideLayout;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableDepthTest();
        if (sideLayout) {
            int border = this.active ? 0xFF555555 : 0xFF303030;
            int background = this.active ? 0xFF9A9A9A : 0xFFC6C6C6;
            graphics.fill(getX() + 2, getY(), getX() + width, getY() + height, border);
            graphics.fill(getX() + 3, getY() + 1, getX() + width - 1, getY() + height - 1, background);
            graphics.renderItem(ModItems.HUNT_ORDER.get().getDefaultInstance(), getX() + 6, getY() + 4);
            return;
        }
        if (!this.active) {
            graphics.blit(SIDE, getX(), getY(), TAB_TEXTURE_X, 21, width, height, 256, 256);
        }
        graphics.renderItem(ModItems.HUNT_ORDER.get().getDefaultInstance(), getX() + 4, getY() + 6);
    }

    @Override
    public boolean isTooltipHovered() {
        return this.active && this.isHovered();
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.renderComponentTooltip(font,
                java.util.List.of(Component.translatable("gui.callresponse.maid_status.title"), Component.translatable("gui.callresponse.maid_status.tooltip")),
                mouseX, mouseY);
    }
}
