package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderData;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderEntry;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 狩猎名单管理界面。名单数据与网络协议保持不变，仅改善布局和操作反馈。 */
public class HuntOrderScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.callresponse.hunt.targets.title");
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 250;
    private static final int ROW_HEIGHT = 22;

    private final UUID maidUUID;
    private List<HuntOrderEntry> entries = new ArrayList<>();
    private EditBox input;
    private int page;
    private int visibleRows;

    protected HuntOrderScreen(UUID maidUUID, List<HuntOrderEntry> entries) {
        super(TITLE);
        this.maidUUID = maidUUID;
        this.entries = new ArrayList<>(entries);
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
        return panelTop() + 82;
    }

    private int footerY() {
        return panelTop() + panelHeight() - 28;
    }

    private int maxPage() {
        return Math.max(1, (entries.size() + visibleRows - 1) / visibleRows);
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        visibleRows = Math.max(1, Math.min(7, (footerY() - listTop() - 4) / ROW_HEIGHT));
        page = Math.min(page, maxPage() - 1);

        int inputY = top + 51;
        input = new EditBox(font, left + 16, inputY, panelWidth - 88, 20,
                Component.translatable("gui.callresponse.hunt.input.narration"));
        input.setMaxLength(64);
        input.setHint(Component.translatable("gui.callresponse.hunt.input.hint"));
        addRenderableWidget(input);

        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.hunt.add"), btn -> onAdd())
                .bounds(left + panelWidth - 66, inputY, 50, 20).build());

        int start = page * visibleRows;
        int end = Math.min(start + visibleRows, entries.size());
        for (int i = start; i < end; i++) {
            HuntOrderEntry entry = entries.get(i);
            int y = listTop() + (i - start) * ROW_HEIGHT + 2;
            addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.hunt.remove"),
                            btn -> onRemove(entry.uuid))
                    .bounds(left + panelWidth - 55, y, 39, 18).build());
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), btn -> changePage(-1))
                .bounds(left + 16, footerY(), 24, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), btn -> changePage(1))
                .bounds(left + 44, footerY(), 24, 20).build());
        next.active = page < maxPage() - 1;

        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.hunt.close"), btn -> onClose())
                .bounds(left + panelWidth - 72, footerY(), 56, 20).build());

        setInitialFocus(input);
    }

    private void changePage(int delta) {
        int next = Math.max(0, Math.min(maxPage() - 1, page + delta));
        if (next != page) {
            page = next;
            rebuildWidgets();
        }
    }

    private void onAdd() {
        String value = input.getValue().trim();
        if (value.isEmpty() || entries.size() >= HuntOrderData.MAX_ENTRIES) return;
        input.setValue("");
        PacketDistributor.sendToServer(new HuntOrderUpdateC2SPacket(maidUUID, false, value));
    }

    private void onRemove(UUID uuid) {
        HuntOrderEntry found = entries.stream().filter(entry -> entry.uuid.equals(uuid)).findFirst().orElse(null);
        if (found == null) return;
        PacketDistributor.sendToServer(new HuntOrderUpdateC2SPacket(maidUUID, true, uuid.toString()));
        entries.remove(found);
        page = Math.min(page, maxPage() - 1);
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && input != null && input.isFocused()) {
            onAdd();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta, double deltaY) {
        if (delta != 0) {
            changePage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta, deltaY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();

        drawPanel(graphics, left, top, right, bottom);
        graphics.renderItem(new ItemStack(ModItems.HUNT_ORDER.get()), left + 14, top + 11);
        graphics.drawString(font, getTitle(), left + 38, top + 12, 0xFFF2D18A, false);
        graphics.drawString(font, Component.translatable("gui.callresponse.hunt.maid", shortUuid(maidUUID)),
                left + 38, top + 27, 0xFF9A9A9A, false);
        Component count = Component.translatable("gui.callresponse.hunt.count", entries.size(), HuntOrderData.MAX_ENTRIES);
        graphics.drawString(font, count, right - 16 - font.width(count), top + 18, 0xFFD66A6A, false);

        int start = page * visibleRows;
        int end = Math.min(start + visibleRows, entries.size());
        if (entries.isEmpty()) {
            Component empty = Component.translatable("gui.callresponse.hunt.empty");
            graphics.drawCenteredString(font, empty, width / 2, listTop() + 17, 0xFF858585);
        }
        for (int i = start; i < end; i++) {
            HuntOrderEntry entry = entries.get(i);
            int y = listTop() + (i - start) * ROW_HEIGHT;
            int rowColor = (i & 1) == 0 ? 0x9A21191D : 0x9A181419;
            graphics.fill(left + 14, y, right - 14, y + 21, rowColor);
            graphics.fill(left + 14, y, left + 17, y + 21, entry.isPlayer ? 0xFFE1A34C : 0xFF5FAED0);

            Component type = Component.translatable(entry.isPlayer
                    ? "gui.callresponse.hunt.type.player"
                    : "gui.callresponse.hunt.type.entity");
            int typeColor = entry.isPlayer ? 0xFFFFC86B : 0xFF72C9EE;
            graphics.drawString(font, type, left + 23, y + 6, typeColor, false);
            int nameX = left + 23 + font.width(type) + 6;
            int maxNameWidth = right - 62 - nameX;
            String label = entry.name + "  §8#" + shortUuid(entry.uuid);
            graphics.drawString(font, font.plainSubstrByWidth(label, Math.max(20, maxNameWidth)),
                    nameX, y + 6, 0xFFE6E6E6, false);
        }

        Component pageText = Component.translatable("gui.callresponse.hunt.page", page + 1, maxPage());
        graphics.drawCenteredString(font, pageText, width / 2, footerY() + 6, 0xFFA7A7A7);
    }

    private static void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, 0xE8110E12);
        graphics.fill(left + 1, top + 1, right - 1, top + 42, 0xE52B1219);
        graphics.hLine(left, right - 1, top, 0xFFD75A54);
        graphics.hLine(left, right - 1, bottom - 1, 0xFF6E3437);
        graphics.vLine(left, top, bottom - 1, 0xFF6E3437);
        graphics.vLine(right - 1, top, bottom - 1, 0xFF6E3437);
        graphics.hLine(left + 10, right - 11, top + 42, 0xFF4C272D);
    }

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void refresh(UUID maidUUID, List<HuntOrderEntry> entries) {
        if (Minecraft.getInstance().screen instanceof HuntOrderScreen screen && screen.maidUUID.equals(maidUUID)) {
            screen.entries = new ArrayList<>(entries);
            screen.page = Math.min(screen.page, screen.maxPage() - 1);
            screen.rebuildWidgets();
        } else {
            open(maidUUID, entries);
        }
    }

    public static void open(UUID maidUUID, List<HuntOrderEntry> entries) {
        Minecraft.getInstance().setScreen(new HuntOrderScreen(maidUUID, entries));
    }
}
