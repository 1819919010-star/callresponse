package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT)
public final class TradingMaidClientEvents {
    private TradingMaidClientEvents() {
    }

    @SubscribeEvent
    public static void onMerchantScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MerchantScreen screen)) {
            return;
        }
        int traderId = TradingMaidClientState.currentTraderId();
        if (traderId < 0) {
            return;
        }
        int left = (screen.width - 276) / 2;
        int top = (screen.height - 166) / 2;
        event.addListener(Button.builder(Component.translatable("gui.callresponse.trade.open"), button ->
                        ClientPacketDistributor.sendToServer(new RequestTradingMaidScreenC2SPacket(traderId)))
                .pos(left + 178, top - 23).size(94, 20).build());
        TradingMaidClientState.clear();
    }
}
