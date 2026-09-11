package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.menu.MaidStatusContainer;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MaidStatusContainerGui extends AbstractMaidContainerGui<MaidStatusContainer> {
    private static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");
    private boolean expelChoicesOpen;
    private boolean frightenUsedLocally;
    private Button expelButton;

    public MaidStatusContainerGui(MaidStatusContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.callresponse.maid_status.title"));
    }

    @Override
    protected void initAdditionWidgets() {
        this.expelButton = null;

        if (expelChoicesOpen) {
            Button frighten = Button.builder(Component.translatable(frightenUsedLocally
                            ? "gui.callresponse.maid_status.frighten_used"
                            : "gui.callresponse.maid_status.frighten"),
                            ignored -> frightenMaid())
                    .bounds(leftPos + 92, topPos + 138, 74, 20)
                    .build();
            frighten.active = !frightenUsedLocally;
            this.addRenderableWidget(frighten);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.maid_status.expel_confirm").withStyle(ChatFormatting.RED),
                            ignored -> expelMaid())
                    .bounds(leftPos + 171, topPos + 138, 74, 20)
                    .build());
        } else {
            this.expelButton = Button.builder(
                            Component.translatable("gui.callresponse.maid_status.expel").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                            ignored -> {
                                expelChoicesOpen = true;
                                init();
                            })
                    .bounds(leftPos + 196, topPos + 137, 48, 20)
                    .build();
            this.addRenderableWidget(this.expelButton);
        }
    }

    private void frightenMaid() {
        if (frightenUsedLocally) {
            return;
        }
        CallResponseMod.CHANNEL.sendToServer(new ExpelMaidC2SPacket(maid.getUUID(), false));
        frightenUsedLocally = true;
        init();
    }

    private void expelMaid() {
        CallResponseMod.CHANNEL.sendToServer(new ExpelMaidC2SPacket(maid.getUUID(), true));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTicks, mouseX, mouseY);
        // The dedicated status container only contains the player's inventory. Maid
        // equipment slots therefore do not exist on this page and cannot bleed through.
        graphics.fill(leftPos + 78, topPos + 27, leftPos + 249, topPos + 162, 0xFF4A4A4A);
        graphics.fill(leftPos + 80, topPos + 29, leftPos + 247, topPos + 160, 0xFFC6C6C6);
    }

    @Override
    protected void renderAddition(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.drawCenteredString(font, Component.translatable("gui.callresponse.maid_status.title"),
                leftPos + 164, topPos + 41, 0xFF404040);

        int hunger = Math.round(HungerData.get(maid));
        EmotionData.EmotionValues emotions = getEmotions();
        drawStatusBar(graphics, Component.translatable("gui.callresponse.maid_status.hunger"), hunger, topPos + 58, 18);
        drawStatusBar(graphics, Component.translatable("gui.callresponse.maid_status.trust"), emotions.trust(), topPos + 79, 28);
        drawStatusBar(graphics, Component.translatable("gui.callresponse.maid_status.fear"), emotions.fear(), topPos + 100, 23);

        if (expelChoicesOpen) {
            graphics.drawCenteredString(font, Component.translatable("gui.callresponse.maid_status.expel_question"),
                    leftPos + 166, topPos + 123, 0xFF8B2020);
        } else if (expelButton != null) {
            drawButtonFrame(graphics, expelButton);
        }
    }

    private void drawButtonFrame(GuiGraphics graphics, Button button) {
        int x1 = button.getX() - 2;
        int y1 = button.getY() - 2;
        int x2 = button.getX() + button.getWidth() + 2;
        int y2 = button.getY() + button.getHeight() + 2;
        int outer = 0xFF5A1010;
        int inner = 0xFFD05050;
        graphics.fill(x1, y1, x2, y1 + 1, outer);
        graphics.fill(x1, y2 - 1, x2, y2, outer);
        graphics.fill(x1, y1, x1 + 1, y2, outer);
        graphics.fill(x2 - 1, y1, x2, y2, outer);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, inner);
        graphics.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, inner);
        graphics.fill(x1 + 1, y1 + 1, x1 + 2, y2 - 1, inner);
        graphics.fill(x2 - 2, y1 + 1, x2 - 1, y2 - 1, inner);
    }

    private EmotionData.EmotionValues getEmotions() {
        if (Minecraft.getInstance().player == null) {
            return EmotionData.EmotionValues.DEFAULT;
        }
        return EmotionData.get(maid, Minecraft.getInstance().player.getUUID());
    }

    private void drawStatusBar(GuiGraphics graphics, Component name, int value, int y, int fillV) {
        int clamped = Math.max(0, Math.min(100, value));
        int barX = leftPos + 112;
        graphics.drawString(font, name, leftPos + 86, y + 1, 0xFF404040, false);
        graphics.blit(SIDE, barX, y, 0, 9, 47, 9);
        graphics.blit(SIDE, barX + 2, y + 2, 2, fillV,
                (int) (43 * (clamped / 100.0)), 5);
        graphics.drawString(font, clamped + " / 100", barX + 52, y + 1, 0xFF404040, false);
    }
}
