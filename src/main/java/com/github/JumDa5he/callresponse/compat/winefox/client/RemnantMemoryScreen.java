package com.github.JumDa5he.callresponse.compat.winefox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A plain paginated reader: each memory starts a new page and no paragraph is truncated. */
@OnlyIn(Dist.CLIENT)
public final class RemnantMemoryScreen extends Screen {
    private record Page(List<FormattedCharSequence> heading, List<FormattedCharSequence> lines) {}
    private final int first, second;
    private final List<Page> pages = new ArrayList<>();
    private int page, left, top, panelWidth, panelHeight;
    private Button previous, next;

    public RemnantMemoryScreen(int first, int second) {
        super(Component.translatable("item.callresponse.remnant_memory"));
        this.first = first;
        this.second = second;
    }

    public static void open(int first, int second) {
        Minecraft.getInstance().setScreen(new RemnantMemoryScreen(first, second));
    }

    public static String key(int memory, String part) {
        return String.format(Locale.ROOT, "memory.callresponse.remnant.%02d.%s", memory + 1, part);
    }

    @Override protected void init() {
        panelWidth = Math.min(380, width - 24);
        panelHeight = Math.min(300, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        pages.clear();
        paginate(first);
        paginate(second);
        page = Math.max(0, Math.min(page, pages.size() - 1));
        int buttonsY = top + panelHeight - 26;
        previous = addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.remnant.previous"),
                button -> turn(-1)).bounds(left + 12, buttonsY, 58, 20).build());
        next = addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.remnant.next"),
                button -> turn(1)).bounds(left + panelWidth - 70, buttonsY, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 28, buttonsY, 56, 20).build());
        updateButtons();
    }

    private void paginate(int memory) {
        int textWidth = Math.max(40, panelWidth - 32);
        List<FormattedCharSequence> heading = font.split(Component.translatable(key(memory, "title")), textWidth);
        int headerHeight = heading.size() * 11 + 12;
        int capacity = Math.max(1, (panelHeight - headerHeight - 56) / 11);
        List<FormattedCharSequence> lines = new ArrayList<>();
        String content = Component.translatable(key(memory, "content")).getString();
        for (String paragraph : content.split("\\n", -1)) {
            if (paragraph.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
            else lines.addAll(font.split(Component.literal(paragraph), textWidth));
        }
        for (int offset = 0; offset < lines.size(); offset += capacity) {
            pages.add(new Page(heading, List.copyOf(lines.subList(offset, Math.min(lines.size(), offset + capacity)))));
        }
    }

    private void updateButtons() {
        previous.active = page > 0;
        next.active = page + 1 < pages.size();
    }
    private void turn(int offset) {
        page = Math.max(0, Math.min(pages.size() - 1, page + offset));
        updateButtons();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF3B2449);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFFF0E6D9);
        if (!pages.isEmpty()) {
            Page current = pages.get(page);
            int y = top + 12;
            for (FormattedCharSequence line : current.heading) {
                graphics.drawString(font, line, left + 16, y, 0x553363, false);
                y += 11;
            }
            graphics.fill(left + 14, y + 1, left + panelWidth - 14, y + 2, 0xFFB9A2BF);
            y += 12;
            for (FormattedCharSequence line : current.lines) {
                graphics.drawString(font, line, left + 16, y, 0x302635, false);
                y += 11;
            }
            graphics.drawCenteredString(font, Component.translatable("gui.callresponse.remnant.page", page + 1, pages.size()),
                    width / 2, top + panelHeight - 40, 0x77647E);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) { turn(delta > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_RIGHT) { turn(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_LEFT) { turn(-1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
}
