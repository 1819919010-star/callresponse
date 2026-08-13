package com.github.JumDa5he.callresponse.compat.client.gui;

import com.github.JumDa5he.callresponse.network.WanderingMaidDecisionC2SPacket;
import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public final class WanderingMaidRequestScreen extends Screen {
    private final int entityId;
    private final UUID maidId;
    private final Component maidName;

    private WanderingMaidRequestScreen(int entityId, UUID maidId, Component maidName) {
        super(Component.translatable("gui.callresponse.wandering.request.title"));
        this.entityId = entityId;
        this.maidId = maidId;
        this.maidName = maidName;
    }

    public static void open(int entityId, UUID maidId, Component maidName) {
        Minecraft.getInstance().setScreen(new WanderingMaidRequestScreen(entityId, maidId, maidName));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonY = height / 2 + 70;
        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.wandering.request.accept"),
                        button -> decide(true))
                .pos(centerX - 112, buttonY).size(104, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.wandering.request.reject"),
                        button -> decide(false))
                .pos(centerX + 8, buttonY).size(104, 22).build());
    }

    private void decide(boolean accept) {
        PacketDistributor.sendToServer(new WanderingMaidDecisionC2SPacket(maidId, accept));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int panelLeft = centerX - 158;
        int panelTop = height / 2 - 98;
        int panelRight = centerX + 158;
        int panelBottom = height / 2 + 102;
        graphics.fill(panelLeft - 6, panelTop - 6, panelRight + 6, panelBottom + 6, 0x60000000);
        graphics.fill(panelLeft - 3, panelTop - 3, panelRight + 3, panelBottom + 3, 0xB016101A);
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE171821);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 31, 0xFF63334F);
        graphics.fill(panelLeft, panelTop + 31, panelRight, panelTop + 33, 0xFFD6A75C);
        graphics.fill(panelLeft + 12, panelTop + 43, centerX - 48, panelBottom - 40, 0x802B2632);
        graphics.fill(centerX - 42, panelTop + 47, panelRight - 12, panelBottom - 42, 0x402A202B);
        graphics.renderOutline(panelLeft, panelTop, 316, 200, 0xFFD6A75C);
        graphics.renderOutline(panelLeft + 4, panelTop + 4, 308, 192, 0xFF754E68);
        graphics.renderOutline(panelLeft + 12, panelTop + 43, 98, 112, 0xFF8F6D83);
        drawCornerOrnaments(graphics, panelLeft, panelTop, panelRight, panelBottom);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, panelTop + 8, 0);
        graphics.pose().scale(1.35f, 1.35f, 1.0f);
        graphics.drawCenteredString(font, title, 0, 0, 0xFFFFE9B9);
        graphics.pose().popPose();
        graphics.drawCenteredString(font,
                Component.translatable("gui.callresponse.wandering.request.subtitle"),
                centerX, panelTop + 36, 0xFFBCAFC0);

        Entity entity = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(entityId) : null;
        if (entity instanceof EntityMaid maid) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    panelLeft + 12, panelTop + 43, centerX - 48, panelBottom - 40, 45,
                    0, mouseX, mouseY, maid);
        }

        Component shownName = maidName;
        if (entity instanceof EntityMaid maid) {
            shownName = CustomPackLoader.MAID_MODELS.getInfo(maid.getModelId()).<Component>map(
                            info -> Component.literal(ParseI18n.getI18nValue(info.getName())))
                    .orElse(maidName);
        }
        Component description = Component.translatable("gui.callresponse.wandering.request.description", shownName);
        graphics.drawString(font, Component.translatable("gui.callresponse.wandering.request.name", shownName),
                centerX - 38, panelTop + 57, 0xFFFFD58A, false);
        graphics.fill(centerX - 38, panelTop + 72, panelRight - 14, panelTop + 73, 0xFF6D5667);
        List<FormattedCharSequence> lines = font.split(description, 176);
        int textY = panelTop + 82;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, centerX - 38, textY, 0xFFE8E1E7, false);
            textY += 13;
        }
        graphics.drawString(font, Component.translatable("gui.callresponse.wandering.request.hint"),
                centerX - 38, panelBottom - 52, 0xFF9E929C, false);
    }

    private static void drawCornerOrnaments(GuiGraphics graphics, int left, int top, int right, int bottom) {
        int gold = 0xFFD6A75C;
        int rose = 0xFF8F5877;
        graphics.fill(left + 7, top + 7, left + 28, top + 9, gold);
        graphics.fill(left + 7, top + 7, left + 9, top + 22, gold);
        graphics.fill(right - 28, top + 7, right - 7, top + 9, gold);
        graphics.fill(right - 9, top + 7, right - 7, top + 22, gold);
        graphics.fill(left + 7, bottom - 9, left + 28, bottom - 7, rose);
        graphics.fill(left + 7, bottom - 22, left + 9, bottom - 7, rose);
        graphics.fill(right - 28, bottom - 9, right - 7, bottom - 7, rose);
        graphics.fill(right - 9, bottom - 22, right - 7, bottom - 7, rose);
        graphics.fill(left + 116, top + 56, left + 118, bottom - 49, 0x906B4C61);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
