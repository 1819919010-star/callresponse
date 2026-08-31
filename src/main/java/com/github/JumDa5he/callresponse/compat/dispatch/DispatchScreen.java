package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.client.util.EntityRenderUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DispatchScreen extends Screen {
    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "textures/gui/dispatch_background.png");
    private static final Identifier BUTTON =
            Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "textures/gui/dispatch_button.png");
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 226;
    private static final int BUTTON_TEXTURE_WIDTH = 284;
    private static final int BUTTON_TEXTURE_HEIGHT = 40;
    private static final int TEXT_COLOR = 0xFF55263E;
    private static final int SUB_TEXT_COLOR = 0xFF735267;
    private static final int HEADER_COLOR = 0xFFFFF1F7;
    private static final float HEADER_SCALE = 1.25F;

    private enum Page { HOME, EVENTS, MAIDS }

    private final List<OpenDispatchScreenS2CPacket.EventInfo> events;
    private final List<OpenDispatchScreenS2CPacket.MaidInfo> maids;
    private final List<OpenDispatchScreenS2CPacket.ActiveInfo> active;
    private final int limit;
    private Page page = Page.HOME;
    private String category = "work";
    private OpenDispatchScreenS2CPacket.EventInfo selected;
    private int scroll;

    private DispatchScreen(List<OpenDispatchScreenS2CPacket.EventInfo> events, List<OpenDispatchScreenS2CPacket.MaidInfo> maids,
                           List<OpenDispatchScreenS2CPacket.ActiveInfo> active, int limit) {
        super(Component.translatable("gui.callresponse.dispatch.title"));
        this.events = events;
        this.maids = maids;
        this.active = active;
        this.limit = limit;
    }

    public static void open(List<OpenDispatchScreenS2CPacket.EventInfo> events, List<OpenDispatchScreenS2CPacket.MaidInfo> maids,
                            List<OpenDispatchScreenS2CPacket.ActiveInfo> active, int limit) {
        Minecraft.getInstance().setScreen(new DispatchScreen(events, maids, active, limit));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int cx = width / 2;
        int top = height / 2 - 105;
        if (page == Page.HOME) {
            addButton(Component.translatable("gui.callresponse.dispatch.play"), cx - 145, top + 38, 135, 42,
                    b -> { page = Page.EVENTS; category = "play"; scroll = 0; rebuild(); });
            addButton(Component.translatable("gui.callresponse.dispatch.work"), cx + 10, top + 38, 135, 42,
                    b -> { page = Page.EVENTS; category = "work"; scroll = 0; rebuild(); });
            int from = Math.min(active.size(), scroll * 4);
            int to = Math.min(active.size(), from + 4);
            for (int i = from; i < to; i++) {
                int index = i;
                int row = i - from;
                addButton(Component.translatable("gui.callresponse.dispatch.recall"), cx + 69, top + 104 + row * 28, 70, 20,
                        b -> recall(active.get(index).dispatchId()));
            }
            addPaging(cx, top, active.size(), 4);
        } else {
            addButton(Component.translatable("gui.callresponse.dispatch.back"), cx - 150, top + 5, 60, 20,
                    b -> { page = page == Page.MAIDS ? Page.EVENTS : Page.HOME; scroll = 0; rebuild(); });
            if (page == Page.EVENTS) {
                List<OpenDispatchScreenS2CPacket.EventInfo> shown = events.stream()
                        .filter(e -> e.category().equals(category)).toList();
                int from = Math.min(shown.size(), scroll * 5);
                int to = Math.min(shown.size(), from + 5);
                for (int i = from; i < to; i++) {
                    var event = shown.get(i);
                    int row = i - from;
                    addButton(event.title(), cx - 142, top + 34 + row * 34, 284, 28,
                            b -> { selected = event; page = Page.MAIDS; scroll = 0; rebuild(); });
                }
                addPaging(cx, top, shown.size(), 5);
            } else {
                int from = Math.min(maids.size(), scroll * 5);
                int to = Math.min(maids.size(), from + 5);
                for (int i = from; i < to; i++) {
                    var maid = maids.get(i);
                    int row = i - from;
                    addButton(Component.literal(maid.name()), cx - 70, top + 35 + row * 32, 210, 26,
                            b -> start(maid.id()));
                }
                addPaging(cx, top, maids.size(), 5);
            }
        }
    }

    private void addButton(Component text, int x, int y, int width, int height, Button.OnPress onPress) {
        addRenderableWidget(new DispatchTextureButton(x, y, width, height, text, onPress));
    }

    private void addPaging(int cx, int top, int total, int pageSize) {
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        if (pages <= 1) {
            return;
        }
        addButton(Component.literal("‹"), cx - 35, top + 188, 28, 20,
                b -> { scroll = Math.max(0, scroll - 1); rebuild(); });
        addButton(Component.literal("›"), cx + 7, top + 188, 28, 20,
                b -> { scroll = Math.min(pages - 1, scroll + 1); rebuild(); });
    }

    private void start(UUID maid) {
        if (selected != null) {
            Minecraft.getInstance().getConnection().send(
                    new DispatchActionC2SPacket(DispatchActionC2SPacket.Action.START, selected.id(), maid));
        }
    }

    private void recall(UUID dispatch) {
        Minecraft.getInstance().getConnection().send(
                new DispatchActionC2SPacket(DispatchActionC2SPacket.Action.RECALL, "", dispatch));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blurBeforeThisStratum();
        int cx = width / 2;
        int top = height / 2 - 105;
        graphics.blit(BACKGROUND, cx - 160, top - 8, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
        Component header = title;
        if (page == Page.EVENTS) {
            header = Component.translatable(category.equals("work")
                    ? "gui.callresponse.dispatch.header.work" : "gui.callresponse.dispatch.header.play");
        } else if (page == Page.MAIDS && selected != null) {
            header = Component.translatable("gui.callresponse.dispatch.choose_maid", selected.title());
        }
        drawHeader(graphics, header, cx, top + 3);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int cx = width / 2;
        int top = height / 2 - 105;
        ItemStack hoveredStack = ItemStack.EMPTY;
        Component hoveredDescription = null;
        if (page == Page.HOME) {
            graphics.centeredText(font, Component.translatable("gui.callresponse.dispatch.choose"), cx, top + 27, TEXT_COLOR);
            graphics.text(font, Component.translatable("gui.callresponse.dispatch.active", active.size(), limit),
                    cx - 142, top + 93, TEXT_COLOR);
            int from = Math.min(active.size(), scroll * 4);
            int to = Math.min(active.size(), from + 4);
            for (int i = from; i < to; i++) {
                var info = active.get(i);
                long seconds = Math.max(0, (info.finishAt() - System.currentTimeMillis()) / 1000);
                graphics.text(font, Component.translatable("gui.callresponse.dispatch.active_line",
                                info.name().getString(), info.title().getString(), format(seconds)),
                        cx - 137, top + 111 + (i - from) * 28, SUB_TEXT_COLOR);
            }
        } else if (page == Page.EVENTS) {
            List<OpenDispatchScreenS2CPacket.EventInfo> shown = events.stream()
                    .filter(e -> e.category().equals(category)).toList();
            int from = Math.min(shown.size(), scroll * 5);
            int to = Math.min(shown.size(), from + 5);
            for (int i = from; i < to; i++) {
                var event = shown.get(i);
                int y = top + 34 + (i - from) * 34;
                if (mouseX >= cx - 142 && mouseX < cx + 142 && mouseY >= y && mouseY < y + 28) {
                    hoveredDescription = event.description();
                }
                graphics.text(font, Component.translatable("gui.callresponse.dispatch.duration",
                                event.durationMin(), event.durationMax()), cx + 64, y + 9, 0xFF8A735F);
                int x = cx - 103;
                for (ItemStack stack : event.rewards()) {
                    graphics.item(stack, x, y + 6);
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 6 && mouseY < y + 22) {
                        hoveredStack = stack;
                    }
                    x += 18;
                }
            }
        } else if (selected != null) {
            graphics.text(font, selected.description(), cx - 145, top + 198, SUB_TEXT_COLOR);
            if (!maids.isEmpty() && minecraft != null && minecraft.level != null) {
                int from = Math.min(maids.size(), scroll * 5);
                int to = Math.min(maids.size(), from + 5);
                for (int i = from; i < to; i++) {
                    var info = maids.get(i);
                    int y = top + 35 + (i - from) * 32;
                    EntityMaid preview = InitEntities.MAID.get().create(minecraft.level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                    if (preview != null) {
                        preview.setModelId(info.modelId());
                        EntityRenderUtil.renderMaid(graphics, preview, cx - 112, y + 26, 18, 0);
                    }
                }
            }
        }
        if (!hoveredStack.isEmpty()) {
            graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
        } else if (hoveredDescription != null) {
            graphics.setTooltipForNextFrame(font, hoveredDescription, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Component text, int centerX, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, y);
        graphics.pose().scale(HEADER_SCALE, HEADER_SCALE);
        graphics.centeredText(font, text, 0, 0, HEADER_COLOR);
        graphics.pose().popMatrix();
    }

    private static String format(long seconds) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class DispatchTextureButton extends Button {
        private DispatchTextureButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int tint = this.active ? 0xFFFFFFFF : 0x88FFFFFF;
            graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON, getX(), getY(), 0, 0,
                    width, height, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, tint);
            if (this.isHoveredOrFocused()) {
                graphics.outline(this.getX(), this.getY(), this.width, this.height, 0xFFF7D4E4);
            }
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, this.active ? 0xFF57243E : 0xFF8B7180);
        }
    }
}
