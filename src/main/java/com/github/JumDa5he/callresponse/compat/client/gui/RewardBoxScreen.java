package com.github.JumDa5he.callresponse.compat.client.gui;

import com.github.JumDa5he.callresponse.compat.menu.RewardBoxMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RewardBoxScreen extends AbstractContainerScreen<RewardBoxMenu> {
    public RewardBoxScreen(RewardBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 232, 266);
        inventoryLabelY = 172;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFF0E5D2);
        graphics.outline(x, y, imageWidth, imageHeight, 0xFF5A3845);
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 12; col++) {
                graphics.outline(x + 7 + col * 18, y + 17 + row * 18, 18, 18, 0xFF8B6B62);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.outline(x + 34 + col * 18, y + 183 + row * 18, 18, 18, 0xFF8B6B62);
            }
        }
        for (int col = 0; col < 9; col++) {
            graphics.outline(x + 34 + col * 18, y + 241, 18, 18, 0xFF8B6B62);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }
}
