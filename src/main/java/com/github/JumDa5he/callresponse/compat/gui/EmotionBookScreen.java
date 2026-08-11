package com.github.JumDa5he.callresponse.compat.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class EmotionBookScreen extends Screen {
    private static final Component TITLE = Component.literal("数值调整器");

    private final UUID maidUUID;
    private final int initialTrust;
    private final int initialFear;
    private final int initialHunger;
    private EditBox trustInput;
    private EditBox fearInput;
    private EditBox hungerInput;

    protected EmotionBookScreen(UUID maidUUID, int trust, int fear, int hunger) {
        super(TITLE);
        this.maidUUID = maidUUID;
        this.initialTrust = trust;
        this.initialFear = fear;
        this.initialHunger = hunger;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int labelY = height / 2 - 60;
        int inputX = centerX - 40;

        trustInput = new EditBox(font, inputX, labelY, 80, 18, Component.literal("信任值"));
        trustInput.setMaxLength(3);
        trustInput.setFilter(s -> s.matches("\\d{0,3}"));
        trustInput.setValue(String.valueOf(initialTrust));
        addRenderableWidget(trustInput);

        fearInput = new EditBox(font, inputX, labelY + 28, 80, 18, Component.literal("恐惧值"));
        fearInput.setMaxLength(3);
        fearInput.setFilter(s -> s.matches("\\d{0,3}"));
        fearInput.setValue(String.valueOf(initialFear));
        addRenderableWidget(fearInput);

        hungerInput = new EditBox(font, inputX, labelY + 56, 80, 18, Component.literal("饥饿值"));
        hungerInput.setMaxLength(3);
        hungerInput.setFilter(s -> s.matches("\\d{0,3}"));
        hungerInput.setValue(String.valueOf(initialHunger));
        addRenderableWidget(hungerInput);

        addRenderableWidget(Button.builder(Component.literal("确认"), btn -> onConfirm())
                .bounds(centerX - 55, labelY + 90, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), btn -> onClose())
                .bounds(centerX + 5, labelY + 90, 50, 20).build());
    }

    private void onConfirm() {
        try {
            int trust = Integer.parseInt(trustInput.getValue());
            int fear = Integer.parseInt(fearInput.getValue());
            int hunger = Integer.parseInt(hungerInput.getValue());
            PacketDistributor.sendToServer(new EmotionBookUpdateC2SPacket(maidUUID, trust, fear, hunger));
        } catch (NumberFormatException ignored) {
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int labelX = centerX - 90;
        int labelY = height / 2 - 58;
        graphics.drawString(font, "信任值:", labelX, labelY + 1, 0xFFFFFF);
        graphics.drawString(font, "恐惧值:", labelX, labelY + 29, 0xFFFFFF);
        graphics.drawString(font, "饥饿值:", labelX, labelY + 57, 0xFFFFFF);
        graphics.drawString(font, getTitle(), centerX - font.width(getTitle()) / 2, labelY - 25, 0xFFFF55);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(UUID maidUUID, int trust, int fear, int hunger) {
        Minecraft.getInstance().setScreen(new EmotionBookScreen(maidUUID, trust, fear, hunger));
    }
}
