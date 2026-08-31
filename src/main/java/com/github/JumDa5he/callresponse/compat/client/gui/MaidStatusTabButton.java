package com.github.JumDa5he.callresponse.compat.client.gui;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.gui.ITooltipButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;


public class MaidStatusTabButton extends Button implements ITooltipButton {
    private static final Identifier SIDE =
            Identifier.fromNamespaceAndPath(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");
    private static final int TAB_TEXTURE_X = 182;
    private final boolean sideLayout;

    public MaidStatusTabButton(int x, int y, boolean selected, boolean sideLayout, Button.OnPress onPress) {
        super(Button.builder(Component.empty(), onPress).pos(x, y).size(sideLayout ? 26 : 24, sideLayout ? 24 : 26));
        this.active = !selected;
        this.sideLayout = sideLayout;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (sideLayout) {
            int border = this.active ? 0xFF555555 : 0xFF303030;
            int background = this.active ? 0xFF9A9A9A : 0xFFC6C6C6;
            graphics.fill(getX() + 2, getY(), getX() + width, getY() + height, border);
            graphics.fill(getX() + 3, getY() + 1, getX() + width - 1, getY() + height - 1, background);
            graphics.item(ModItems.HUNT_ORDER.get().getDefaultInstance(), getX() + 6, getY() + 4);
            return;
        }
        if (!this.active) {
            graphics.blit(SIDE, getX(), getY(), TAB_TEXTURE_X, 21, width, height, 256, 256);
        }
        graphics.item(ModItems.HUNT_ORDER.get().getDefaultInstance(), getX() + 4, getY() + 6);
    }

    @Override
    public boolean isTooltipHovered() {
        return this.active && this.isHovered();
    }

    @Override
    public void renderTooltip(GuiGraphicsExtractor graphics, Minecraft minecraft, int mouseX, int mouseY) {
        graphics.setComponentTooltipForNextFrame(minecraft.font,
                List.of(Component.literal("女仆状态"), Component.literal("查看饥饿、信任与恐惧")),
                mouseX, mouseY);
    }
}