package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.menu.PrincessCarryActionC2SPacket;
import com.github.JumDa5he.callresponse.compat.menu.PrincessCarryContainer;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PrincessCarryScreen extends MaidTaskConfigGui<PrincessCarryContainer> {
    private static final ResourceLocation BG = new ResourceLocation(TouhouLittleMaid.MOD_ID,
            "textures/gui/default_task_config.png");

    public PrincessCarryScreen(PrincessCarryContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void initAdditionWidgets() {
        int x = leftPos + 104;
        int y = topPos + 89;
        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.princess_carry.maid"),
                        button -> send())
                .pos(x, y).size(128, 20).build());
    }

    private void send() {
        CallResponseMod.CHANNEL.sendToServer(new PrincessCarryActionC2SPacket(maid.getId()));
        onClose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTicks, mouseX, mouseY);
        graphics.blit(BG, leftPos + 80, topPos + 28, 0, 0, imageWidth, 137);
    }

    @Override
    protected void renderAddition(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.drawCenteredString(font, Component.translatable("gui.callresponse.princess_carry.title"),
                leftPos + 168, topPos + 46, ChatFormatting.DARK_GRAY.getColor());
    }
}
