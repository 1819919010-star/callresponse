package com.github.JumDa5he.callresponse.compat.npc;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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

/** RPG 风格的女仆日常事件选择界面。 */
public final class NpcEventScreen extends Screen {
    private final int entityId;
    private final UUID maidId;
    private final String titleKey;
    private final String descriptionKey;
    private final List<String> optionKeys;

    private NpcEventScreen(int entityId, UUID maidId, String titleKey,
                           String descriptionKey, List<String> optionKeys) {
        super(Component.translatable(titleKey));
        this.entityId = entityId;
        this.maidId = maidId;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.optionKeys = List.copyOf(optionKeys);
    }

    public static void open(int entityId, UUID maidId, String titleKey,
                            String descriptionKey, List<String> optionKeys) {
        Minecraft.getInstance().setScreen(new NpcEventScreen(entityId, maidId,
                titleKey, descriptionKey, optionKeys));
    }

    @Override
    protected void init() {
        int panelTop = height / 2 - 105;
        int left = width / 2 - 25;
        int buttonWidth = 176;
        // 为最多四个选项预留完整空间，整组选项向上收紧但保持20像素按钮高度。
        int startY = panelTop + 101;
        int spacing = optionKeys.size() > 3 ? 22 : 25;
        for (int i = 0; i < optionKeys.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.translatable(optionKeys.get(i)),
                            button -> choose(index))
                    .pos(left, startY + i * spacing).size(buttonWidth, 20).build());
        }
    }

    private void choose(int index) {
        PacketDistributor.sendToServer(new NpcEventChoiceC2SPacket(maidId, index));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int top = height / 2 - 105;
        int left = centerX - 164;
        int right = centerX + 164;
        int bottom = height / 2 + 105;

        graphics.fill(left - 5, top - 5, right + 5, bottom + 5, 0x70000000);
        graphics.fill(left, top, right, bottom, 0xF0181822);
        graphics.fill(left, top, right, top + 34, 0xFF55364F);
        graphics.fill(left, top + 34, right, top + 36, 0xFFE0B76A);
        graphics.fill(left + 13, top + 48, left + 122, bottom - 14, 0x70302938);
        graphics.fill(left + 132, top + 48, right - 13, bottom - 14, 0x40372A36);
        graphics.renderOutline(left, top, 328, 210, 0xFFE0B76A);
        graphics.renderOutline(left + 4, top + 4, 320, 202, 0xFF805D79);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, top + 10, 0);
        graphics.pose().scale(1.25F, 1.25F, 1.0F);
        graphics.drawCenteredString(font, Component.translatable(titleKey), 0, 0, 0xFFFFE8B5);
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int top = height / 2 - 105;
        int left = centerX - 164;

        Entity entity = minecraft != null && minecraft.level != null
                ? minecraft.level.getEntity(entityId) : null;
        if (entity instanceof EntityMaid maid) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    left + 32, top + 133, left + 102, top + 203, 48,
                    0, mouseX, mouseY, maid);
            graphics.drawCenteredString(font, maid.getDisplayName(), left + 67,
                    top + 181, 0xFFE8DCE7);
        }

        List<FormattedCharSequence> lines = font.split(Component.translatable(descriptionKey), 176);
        int textY = top + 49;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, left + 139, textY, 0xFFE9E1E8, false);
            textY += 12;
            if (textY > top + 82) break;
        }
        graphics.drawString(font, Component.translatable("gui.callresponse.npc_event.choose"),
                left + 139, top + 88, 0xFFDABF82, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
