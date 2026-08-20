package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.menu.RewardBoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RewardBoxScreen extends AbstractContainerScreen<RewardBoxMenu> {
    public RewardBoxScreen(RewardBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 232; imageHeight = 266; inventoryLabelY = 172;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFF0E5D2);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF5A3845);
        for (int row = 0; row < 9; row++) for (int col = 0; col < 12; col++) graphics.renderOutline(x + 7 + col * 18, y + 17 + row * 18, 18, 18, 0xFF8B6B62);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) graphics.renderOutline(x + 34 + col * 18, y + 183 + row * 18, 18, 18, 0xFF8B6B62);
        for (int col = 0; col < 9; col++) graphics.renderOutline(x + 34 + col * 18, y + 241, 18, 18, 0xFF8B6B62);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, partial); renderTooltip(graphics, mouseX, mouseY); }
}
