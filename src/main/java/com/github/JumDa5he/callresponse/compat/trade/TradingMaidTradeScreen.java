package com.github.JumDa5he.callresponse.compat.trade;

import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Two-column wandering-trader maid market. All transactions are revalidated by the server. */
public final class TradingMaidTradeScreen extends Screen {
    private final int traderId;
    private final List<OpenTradingMaidScreenS2CPacket.MaidInfo> buyList;
    private final List<OpenTradingMaidScreenS2CPacket.MaidInfo> sellList;
    private final MerchantScreen parent;
    private int buyIndex;
    private int sellIndex;

    private TradingMaidTradeScreen(int traderId,
                                   List<OpenTradingMaidScreenS2CPacket.MaidInfo> buyList,
                                   List<OpenTradingMaidScreenS2CPacket.MaidInfo> sellList,
                                   MerchantScreen parent) {
        super(Component.translatable("gui.callresponse.trade.title"));
        this.traderId = traderId;
        this.buyList = buyList;
        this.sellList = sellList;
        this.parent = parent;
    }

    public static void open(int traderId,
                            List<OpenTradingMaidScreenS2CPacket.MaidInfo> buyList,
                            List<OpenTradingMaidScreenS2CPacket.MaidInfo> sellList) {
        Minecraft minecraft = Minecraft.getInstance();
        MerchantScreen parent = minecraft.screen instanceof TradingMaidTradeScreen old ? old.parent
                : minecraft.screen instanceof MerchantScreen merchant ? merchant : null;
        minecraft.setScreen(new TradingMaidTradeScreen(traderId, buyList, sellList, parent));
    }

    @Override
    protected void init() {
        int left = width / 2 - 182;
        int top = height / 2 - 102;
        addColumnButtons(left, top, true);
        addColumnButtons(left + 184, top, false);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .pos(width / 2 - 40, top + 208).size(80, 20).build());
    }

    private void addColumnButtons(int left, int top, boolean buying) {
        List<OpenTradingMaidScreenS2CPacket.MaidInfo> list = buying ? buyList : sellList;
        Button previous = Button.builder(Component.literal("<"), button -> changePage(buying, -1))
                .pos(left + 12, top + 153).size(24, 20).build();
        Button next = Button.builder(Component.literal(">"), button -> changePage(buying, 1))
                .pos(left + 144, top + 153).size(24, 20).build();
        previous.active = list.size() > 1;
        next.active = list.size() > 1;
        addRenderableWidget(previous);
        addRenderableWidget(next);

        Component label = Component.translatable(buying ? "gui.callresponse.trade.buy" : "gui.callresponse.trade.sell");
        Button action = Button.builder(label, button -> act(buying))
                .pos(left + 50, top + 176).size(80, 20).build();
        action.active = !list.isEmpty();
        addRenderableWidget(action);
    }

    private void changePage(boolean buying, int amount) {
        List<OpenTradingMaidScreenS2CPacket.MaidInfo> list = buying ? buyList : sellList;
        if (list.isEmpty()) {
            return;
        }
        if (buying) {
            buyIndex = Math.floorMod(buyIndex + amount, list.size());
        } else {
            sellIndex = Math.floorMod(sellIndex + amount, list.size());
        }
    }

    private void act(boolean buying) {
        List<OpenTradingMaidScreenS2CPacket.MaidInfo> list = buying ? buyList : sellList;
        int index = buying ? buyIndex : sellIndex;
        if (index < 0 || index >= list.size()) {
            return;
        }
        PacketDistributor.sendToServer(new TradingMaidActionC2SPacket(traderId, list.get(index).uuid(),
                buying ? TradingMaidActionC2SPacket.Action.BUY : TradingMaidActionC2SPacket.Action.SELL));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 182;
        int top = height / 2 - 102;
        graphics.fill(left - 4, top - 4, left + 368, top + 204, 0xD0101117);
        graphics.fill(left, top, left + 180, top + 200, 0xEE1D2930);
        graphics.fill(left + 184, top, left + 364, top + 200, 0xEE30231F);
        graphics.fill(left, top, left + 180, top + 27, 0xFF315B54);
        graphics.fill(left + 184, top, left + 364, top + 27, 0xFF6A4035);
        graphics.renderOutline(left, top, 180, 200, 0xFF72B8A9);
        graphics.renderOutline(left + 184, top, 180, 200, 0xFFD18A70);
        graphics.drawCenteredString(font, Component.translatable("gui.callresponse.trade.buy_side"),
                left + 90, top + 9, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.callresponse.trade.sell_side"),
                left + 274, top + 9, 0xFFFFFFFF);
        renderEntry(graphics, left, top, buyList, buyIndex, mouseX, mouseY, true);
        renderEntry(graphics, left + 184, top, sellList, sellIndex, mouseX, mouseY, false);
    }

    private void renderEntry(GuiGraphics graphics, int left, int top,
                             List<OpenTradingMaidScreenS2CPacket.MaidInfo> list, int index,
                             int mouseX, int mouseY, boolean buying) {
        if (list.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(buying
                    ? "gui.callresponse.trade.empty_buy" : "gui.callresponse.trade.empty_sell"),
                    left + 90, top + 88, 0xFFA9A9A9);
            return;
        }
        OpenTradingMaidScreenS2CPacket.MaidInfo info = list.get(Math.min(index, list.size() - 1));
        Entity entity = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(info.entityId()) : null;
        if (entity instanceof EntityMaid maid) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    left + 55, top + 52, left + 125, top + 126, 45,
                    0.0625f, mouseX, mouseY, maid);
        }
        Component modelName = CustomPackLoader.MAID_MODELS.getInfo(info.modelId())
                .<Component>map(model -> Component.literal(ParseI18n.getI18nValue(model.getName())))
                .orElse(Component.literal(info.modelId()));
        graphics.drawCenteredString(font, modelName, left + 90, top + 129, 0xFFD8D8D8);
        if (info.customNamed()) {
            graphics.pose().pushPose();
            graphics.pose().translate(left + 90, top + 31, 0);
            graphics.pose().scale(1.2F, 1.2F, 1.0F);
            graphics.drawCenteredString(font, info.displayName(), 0, 0, 0xFFFFD36A);
            graphics.pose().popPose();
        } else {
            graphics.drawCenteredString(font, info.displayName(), left + 90, top + 33, 0xFFFFFFFF);
        }
        if (buying) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.callresponse.trade.price", info.emeraldValue()),
                    left + 90, top + 143, 0xFF69D67C);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.callresponse.trade.sale_reward", info.emeraldValue()),
                    left + 90, top + 143, 0xFFE2BA73);
        }
        graphics.drawCenteredString(font, Component.literal((Math.min(index, list.size() - 1) + 1) + " / " + list.size()),
                left + 90, top + 159, 0xFFB8B8B8);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
