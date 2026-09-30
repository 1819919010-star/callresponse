package com.github.JumDa5he.callresponse.compat.sign.client;

import com.github.JumDa5he.callresponse.compat.sign.MaidSignData;
import com.github.JumDa5he.callresponse.compat.sign.MaidSignManager;
import com.github.JumDa5he.callresponse.compat.sign.MaidSignUpdateC2SPacket;
import com.github.JumDa5he.callresponse.mixin.client.AbstractSignEditScreenAccessor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import com.github.JumDa5he.callresponse.CallResponseMod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 游街示众牌的编辑界面。
 * <p>直接继承原版 {@link AbstractSignEditScreen}：四行文字、光标、方向键换行、背景板
 * 全部复用原版逻辑，这里只做两件事——加一条颜色选择栏，以及把“完成/取消/取下”
 * 换成能回到女仆界面的按钮（原版的“完成”只会把界面关掉）。
 */
public class MaidSignEditScreen extends AbstractSignEditScreen {
    private static final Component TITLE = Component.translatable("gui.callresponse.maid_sign.title");
    private static final int SWATCH_SIZE = 12;
    private static final int SWATCH_GAP = 2;
    private static final int SWATCH_PADDING = 4;
    private static final Vector3f TEXT_SCALE = new Vector3f(0.9765628F, 0.9765628F, 0.9765628F);

    private final Screen parent;
    private final EntityMaid maid;
    @Nullable
    private SignRenderer.SignModel signModel;
    private DyeColor color;
    private int rowLeft;
    private int rowTop;
    private int rowWidth;

    private MaidSignEditScreen(Screen parent, EntityMaid maid, MaidSignBlockEntity holder, DyeColor color) {
        super(holder, true, Minecraft.getInstance().isTextFilteringEnabled(), TITLE);
        this.parent = parent;
        this.maid = maid;
        this.color = color;
    }

    public static MaidSignEditScreen create(Screen parent, EntityMaid maid, MaidSignData data) {
        SignText text = data.text().setHasGlowingText(true);
        return new MaidSignEditScreen(parent, maid, new MaidSignBlockEntity(text), text.getColor());
    }

    @Override
    protected void init() {
        super.init();
        this.signModel = SignRenderer.createSignModel(this.minecraft.getEntityModels(), this.woodType);
        // 原版的“完成”按钮逻辑在私有方法里，只会 setScreen(null)，会直接丢掉女仆界面
        this.clearWidgets();

        DyeColor[] colors = DyeColor.values();
        this.rowWidth = colors.length * SWATCH_SIZE + (colors.length - 1) * SWATCH_GAP;
        this.rowLeft = (this.width - this.rowWidth) / 2;
        this.rowTop = this.height / 4 + 118;
        for (int i = 0; i < colors.length; i++) {
            int x = this.rowLeft + i * (SWATCH_SIZE + SWATCH_GAP);
            this.addRenderableWidget(new ColorSwatch(x, this.rowTop, colors[i]));
        }

        int buttonY = this.height / 4 + 144;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> finish())
                .bounds(this.width / 2 - 100, buttonY, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> backToParent())
                .bounds(this.width / 2, buttonY, 48, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.callresponse.maid_sign.remove"), ignored -> detach())
                .bounds(this.width / 2 + 50, buttonY, 50, 20).build());
    }

    @Override
    protected void offsetSign(GuiGraphics graphics, BlockState state) {
        super.offsetSign(graphics, state);
        if (!(state.getBlock() instanceof StandingSignBlock)) {
            graphics.pose().translate(0.0F, 35.0F, 0.0F);
        }
    }

    @Override
    protected void renderSignBackground(GuiGraphics graphics, BlockState state) {
        if (this.signModel == null) {
            return;
        }
        boolean standing = state.getBlock() instanceof StandingSignBlock;
        graphics.pose().translate(0.0F, 31.0F, 0.0F);
        graphics.pose().scale(62.500004F, 62.500004F, -62.500004F);
        Material material = Sheets.getSignMaterial(this.woodType);
        VertexConsumer consumer = material.buffer(graphics.bufferSource(), this.signModel::renderType);
        this.signModel.stick.visible = standing;
        this.signModel.root.render(graphics.pose(), consumer, 15728880, OverlayTexture.NO_OVERLAY);
    }

    @Override
    protected Vector3f getSignTextScale() {
        return TEXT_SCALE;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        super.renderBackground(graphics);
        if (this.rowWidth > 0) {
            graphics.fill(this.rowLeft - SWATCH_PADDING, this.rowTop - SWATCH_PADDING,
                    this.rowLeft + this.rowWidth + SWATCH_PADDING, this.rowTop + SWATCH_SIZE + SWATCH_PADDING,
                    0x80000000);
        }
    }

    private void applyColor(DyeColor dye) {
        this.color = dye;
        AbstractSignEditScreenAccessor accessor = (AbstractSignEditScreenAccessor) this;
        accessor.callresponse$setSignText(accessor.callresponse$getSignText().setColor(dye).setHasGlowingText(true));
    }

    private void finish() {
        AbstractSignEditScreenAccessor accessor = (AbstractSignEditScreenAccessor) this;
        SignText text = accessor.callresponse$getSignText().setColor(this.color).setHasGlowingText(true);
        List<String> lines = new ArrayList<>(MaidSignManager.MAX_LINES);
        for (int i = 0; i < MaidSignManager.MAX_LINES; i++) {
            lines.add(text.getMessage(i, false).getString());
        }
        CallResponseMod.CHANNEL.sendToServer(new MaidSignUpdateC2SPacket(this.maid.getId(), lines, this.color, false));
        backToParent();
    }

    private void detach() {
        CallResponseMod.CHANNEL.sendToServer(new MaidSignUpdateC2SPacket(this.maid.getId(), List.of(), this.color, true));
        backToParent();
    }

    private void backToParent() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void onClose() {
        backToParent();
    }

    @Override
    public void removed() {
        // 女仆身上的牌子不是方块，不发原版的告示牌更新包；结果由上面的按钮显式提交
    }

    /** 一个色块按钮，选中的颜色用白色描边区分。 */
    private class ColorSwatch extends Button {
        private final DyeColor dye;

        private ColorSwatch(int x, int y, DyeColor dye) {
            super(x, y, SWATCH_SIZE, SWATCH_SIZE, Component.empty(), ignored -> applyColor(dye), DEFAULT_NARRATION);
            this.dye = dye;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int right = x + getWidth();
            int bottom = y + getHeight();
            int border = this.dye == MaidSignEditScreen.this.color ? 0xFFFFFFFF : 0xFF101010;
            graphics.fill(x, y, right, bottom, border);
            graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFF000000 | this.dye.getTextColor());
        }
    }
}
