package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.model.MaidModelGui;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WanderingMaidSkinPoolScreen extends MaidModelGui {
    private final Set<String> selectedModels;
    private boolean dirty;

    private WanderingMaidSkinPoolScreen(EntityMaid previewMaid, Collection<String> selectedModels) {
        super(previewMaid);
        this.selectedModels = new LinkedHashSet<>(selectedModels);
        if (!this.selectedModels.isEmpty()) {
            previewMaid.setModelId(this.selectedModels.iterator().next());
        }
    }

    public static void open(Collection<String> selectedModels) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        EntityMaid preview = EntityMaid.TYPE.create(minecraft.level);
        if (preview != null) {
            minecraft.setScreen(new WanderingMaidSkinPoolScreen(preview, selectedModels));
        }
    }

    @Override
    protected void notifyModelChange(EntityMaid maid, MaidModelInfo info) {
        String id = info.getModelId().toString();
        if (!selectedModels.add(id)) {
            selectedModels.remove(id);
        }
        maid.setIsYsmModel(false);
        maid.setModelId(id);
        dirty = true;
    }

    @Override
    protected void openDetailsGui(EntityMaid maid, MaidModelInfo info) {
        notifyModelChange(maid, info);
    }

    @Override
    protected void addModelCustomTips(MaidModelInfo info, List<Component> tooltips) {
        super.addModelCustomTips(info, tooltips);
        boolean selected = selectedModels.contains(info.getModelId().toString());
        tooltips.add(Component.translatable(selected
                        ? "gui.callresponse.wandering.skin.selected"
                        : "gui.callresponse.wandering.skin.unselected")
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    @Override
    protected void drawRightEntity(GuiGraphics graphics, int posX, int posY, MaidModelInfo info) {
        super.drawRightEntity(graphics, posX, posY, info);
        boolean selected = selectedModels.contains(info.getModelId().toString());
        int color = selected ? 0xFF39D36D : 0xFF7A4A4A;
        graphics.renderOutline(posX - 9, posY - 25, 18, 26, color);
        graphics.fill(posX + 2, posY - 25, posX + 10, posY - 16,
                selected ? 0xE0208A48 : 0xD04A3030);
        graphics.drawString(font, selected ? "✓" : "×", posX + 3, posY - 25,
                selected ? 0xFFFFFFFF : 0xFFFFB0B0, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int textX = 14;
        int textY = height / 2 - 24;
        graphics.fill(textX - 6, textY - 7, textX + 126, textY + 40, 0xA0181820);
        graphics.renderOutline(textX - 6, textY - 7, 132, 47, 0xFF6E91A7);
        graphics.drawString(font,
                Component.translatable("gui.callresponse.wandering.skin.title"),
                textX, textY, 0xFFFFFF, false);
        graphics.drawString(font,
                Component.translatable("gui.callresponse.wandering.skin.count", selectedModels.size()),
                textX, textY + 13, 0xA0E8FF, false);
        graphics.drawString(font,
                Component.translatable("gui.callresponse.wandering.skin.legend"),
                textX, textY + 26, 0xD8D8D8, false);
        graphics.drawCenteredString(font,
                Component.translatable("gui.callresponse.wandering.skin.help"),
                width / 2, height - 12, 0xB0B0B0);
    }

    @Override
    protected void onClickCloseButton() {
        saveIfDirty();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        saveIfDirty();
        super.removed();
    }

    private void saveIfDirty() {
        if (dirty) {
            CallResponseMod.CHANNEL.sendToServer(new UpdateWanderingSkinPoolC2SPacket(selectedModels));
            dirty = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
