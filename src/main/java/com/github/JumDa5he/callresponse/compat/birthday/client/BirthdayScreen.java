package com.github.JumDa5he.callresponse.compat.birthday.client;

import com.github.JumDa5he.callresponse.compat.birthday.BirthdaySetC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 生日设置界面：输入月/日，保存或清除。 */
public final class BirthdayScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.callresponse.birthday.title");

    private final int initialMonth;
    private final int initialDay;
    private EditBox monthInput;
    private EditBox dayInput;

    private BirthdayScreen(int month, int day) {
        super(TITLE);
        this.initialMonth = month;
        this.initialDay = day;
    }

    public static void open(int month, int day) {
        Minecraft.getInstance().setScreen(new BirthdayScreen(month, day));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int inputY = height / 2 - 24;

        monthInput = new EditBox(font, centerX - 78, inputY, 56, 20, Component.translatable("gui.callresponse.birthday.month"));
        monthInput.setMaxLength(2);
        monthInput.setFilter(value -> value.matches("\\d{0,2}"));
        monthInput.setHint(Component.translatable("gui.callresponse.birthday.month_hint"));
        if (initialMonth > 0) {
            monthInput.setValue(String.valueOf(initialMonth));
        }
        addRenderableWidget(monthInput);

        dayInput = new EditBox(font, centerX + 22, inputY, 56, 20, Component.translatable("gui.callresponse.birthday.day"));
        dayInput.setMaxLength(2);
        dayInput.setFilter(value -> value.matches("\\d{0,2}"));
        dayInput.setHint(Component.translatable("gui.callresponse.birthday.day_hint"));
        if (initialDay > 0) {
            dayInput.setValue(String.valueOf(initialDay));
        }
        addRenderableWidget(dayInput);

        int buttonY = height / 2 + 22;
        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.birthday.save"),
                        button -> onSave())
                .bounds(centerX - 105, buttonY, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.birthday.clear"),
                        button -> onClear())
                .bounds(centerX - 33, buttonY, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.birthday.cancel"),
                        button -> onClose())
                .bounds(centerX + 39, buttonY, 66, 20).build());
    }

    private void onSave() {
        int month = parse(monthInput.getValue());
        int day = parse(dayInput.getValue());
        if (month > 0 && day > 0) {
            PacketDistributor.sendToServer(new BirthdaySetC2SPacket(month, day));
        }
        onClose();
    }

    private void onClear() {
        PacketDistributor.sendToServer(BirthdaySetC2SPacket.clear());
        onClose();
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int panelLeft = centerX - 120;
        int panelTop = height / 2 - 78;
        int panelRight = centerX + 120;
        int panelBottom = height / 2 + 54;

        graphics.fill(panelLeft - 6, panelTop - 6, panelRight + 6, panelBottom + 6, 0x60000000);
        graphics.fill(panelLeft - 3, panelTop - 3, panelRight + 3, panelBottom + 3, 0xB016101A);
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE171821);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 26, 0xFF63334F);
        graphics.fill(panelLeft, panelTop + 26, panelRight, panelTop + 28, 0xFFD6A75C);
        graphics.renderOutline(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFFD6A75C);

        graphics.drawCenteredString(font, title, centerX, panelTop + 8, 0xFFFFE9B9);
        graphics.drawString(font, Component.translatable("gui.callresponse.birthday.month"),
                centerX - 78, height / 2 - 36, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.callresponse.birthday.day"),
                centerX + 22, height / 2 - 36, 0xFFFFFF, false);

        Component current = initialMonth > 0
                ? Component.translatable("gui.callresponse.birthday.current", initialMonth, initialDay)
                : Component.translatable("gui.callresponse.birthday.unset");
        graphics.drawCenteredString(font, current, centerX, height / 2 + 2, 0xFFBCAFC0);
        graphics.drawCenteredString(font, Component.translatable("gui.callresponse.birthday.hint"),
                centerX, panelBottom - 18, 0x8F8C99);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
