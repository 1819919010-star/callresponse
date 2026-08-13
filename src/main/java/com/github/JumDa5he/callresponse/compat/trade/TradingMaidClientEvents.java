package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
                        CallResponseMod.CHANNEL.sendToServer(new RequestTradingMaidScreenC2SPacket(traderId)))
                .pos(left + 178, top - 23).size(94, 20).build());
        TradingMaidClientState.clear();
    }
}
