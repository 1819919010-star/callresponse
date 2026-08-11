package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 狩猎终端：选择要配置狩猎名单的死忠女仆。 */
public class MaidListScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.callresponse.hunt.terminal.title");
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 238;
    private static final int ROW_HEIGHT = 24;

    private final List<MaidListS2CPacket.MaidInfo> maids;
    private int page;
    private int visibleRows;

    protected MaidListScreen(List<MaidListS2CPacket.MaidInfo> maids) {
        super(TITLE);
        this.maids = new ArrayList<>(maids);
        this.maids.sort(Comparator.comparing(MaidListScreen::displayName, String.CASE_INSENSITIVE_ORDER));
    }

    private static String displayName(MaidListS2CPacket.MaidInfo info) {
        return info.customName == null || info.customName.isBlank() ? info.typeKey : info.customName;
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, width - 20);
    }

    private int panelHeight() {
        return Math.min(PANEL_HEIGHT, height - 16);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }

    private int listTop() {
        return panelTop() + 58;
    }

    private int footerY() {
        return panelTop() + panelHeight() - 28;
    }

    private int maxPage() {
        return Math.max(1, (maids.size() + visibleRows - 1) / visibleRows);
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int right = left + panelWidth();
        visibleRows = Math.max(1, Math.min(6, (footerY() - listTop() - 3) / ROW_HEIGHT));
        page = Math.min(page, maxPage() - 1);

        int start = page * visibleRows;
        int end = Math.min(start + visibleRows, maids.size());
        for (int i = start; i < end; i++) {
            MaidListS2CPacket.MaidInfo info = maids.get(i);
            int y = listTop() + (i - start) * ROW_HEIGHT + 2;
            addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.hunt.manage"),
                            btn -> select(info.uuid))
                    .bounds(right - 62, y, 46, 19).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), btn -> changePage(-1))
                .bounds(left + 16, footerY(), 24, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), btn -> changePage(1))
                .bounds(left + 44, footerY(), 24, 20).build());
        next.active = page < maxPage() - 1;

        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.hunt.close"), btn -> onClose())
                .bounds(right - 72, footerY(), 56, 20).build());
    }

    private void changePage(int delta) {
        int next = Math.max(0, Math.min(maxPage() - 1, page + delta));
        if (next != page) {
            page = next;
            rebuildWidgets();
        }
    }

    private void select(UUID maidUuid) {
        CallResponseMod.CHANNEL.sendToServer(new RequestHuntOrderScreenC2SPacket(maidUuid));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            changePage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();

        drawPanel(graphics, left, top, right, bottom);
        graphics.renderItem(new ItemStack(ModItems.HUNT_ORDER.get()), left + 14, top + 11);
        graphics.drawString(font, getTitle(), left + 38, top + 12, 0xFFF2D18A, false);
        Component subtitle = Component.translatable("gui.callresponse.hunt.terminal.subtitle", maids.size());
        graphics.drawString(font, subtitle, left + 38, top + 27, 0xFF9A9A9A, false);

        int start = page * visibleRows;
        int end = Math.min(start + visibleRows, maids.size());
        if (maids.isEmpty()) {
            Component empty = Component.translatable("gui.callresponse.hunt.terminal.empty");
            graphics.drawCenteredString(font, empty, width / 2, listTop() + 22, 0xFFD16A6A);
            Component hint = Component.translatable("gui.callresponse.hunt.terminal.empty.hint");
            graphics.drawCenteredString(font, hint, width / 2, listTop() + 38, 0xFF888888);
        }

        for (int i = start; i < end; i++) {
            MaidListS2CPacket.MaidInfo info = maids.get(i);
            int y = listTop() + (i - start) * ROW_HEIGHT;
            graphics.fill(left + 14, y, right - 14, y + 23, (i & 1) == 0 ? 0x9A21191D : 0x9A181419);
            graphics.fill(left + 14, y, left + 17, y + 23, 0xFFC1484E);

            String name = info.customName == null || info.customName.isBlank()
                    ? Component.translatable(info.typeKey).getString()
                    : info.customName;
            graphics.drawString(font, font.plainSubstrByWidth(name, panelWidth() - 145),
                    left + 23, y + 4, 0xFFF0E2C7, false);
            String detail = "#" + shortUuid(info.uuid) + "  " + Component.translatable(info.typeKey).getString();
            graphics.drawString(font, font.plainSubstrByWidth(detail, panelWidth() - 145),
                    left + 23, y + 14, 0xFF858585, false);
        }

        Component pageText = Component.translatable("gui.callresponse.hunt.page", page + 1, maxPage());
        graphics.drawCenteredString(font, pageText, width / 2, footerY() + 6, 0xFFA7A7A7);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, 0xE8110E12);
        graphics.fill(left + 1, top + 1, right - 1, top + 43, 0xE52B1219);
        graphics.hLine(left, right - 1, top, 0xFFD75A54);
        graphics.hLine(left, right - 1, bottom - 1, 0xFF6E3437);
        graphics.vLine(left, top, bottom - 1, 0xFF6E3437);
        graphics.vLine(right - 1, top, bottom - 1, 0xFF6E3437);
        graphics.hLine(left + 10, right - 11, top + 43, 0xFF4C272D);
    }

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(List<MaidListS2CPacket.MaidInfo> maids) {
        Minecraft.getInstance().setScreen(new MaidListScreen(maids));
    }
}
