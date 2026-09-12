package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.compat.menu.OpenMaidStatusC2SPacket;
import com.github.JumDa5he.callresponse.compat.trade.EnableTradingMaidButtonS2CPacket;
import com.github.JumDa5he.callresponse.compat.trade.OpenTradingMaidScreenS2CPacket;
import com.github.JumDa5he.callresponse.compat.trade.RequestTradingMaidScreenC2SPacket;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidActionC2SPacket;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchActionC2SPacket;
import com.github.JumDa5he.callresponse.compat.dispatch.OpenDispatchScreenS2CPacket;
import com.github.JumDa5he.callresponse.compat.npc.NpcEventChoiceC2SPacket;
import com.github.JumDa5he.callresponse.compat.npc.OpenNpcEventS2CPacket;
import com.github.JumDa5he.callresponse.compat.menu.PrincessCarryActionC2SPacket;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkRegistryHandler {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.1");
        registrar.playToServer(EmotionBookUpdateC2SPacket.TYPE, EmotionBookUpdateC2SPacket.STREAM_CODEC, EmotionBookUpdateC2SPacket::handle);
        registrar.playToServer(DropMaidC2SPacket.TYPE, DropMaidC2SPacket.STREAM_CODEC, DropMaidC2SPacket::handle);
        registrar.playToServer(HuntOrderUpdateC2SPacket.TYPE, HuntOrderUpdateC2SPacket.STREAM_CODEC, HuntOrderUpdateC2SPacket::handle);
        registrar.playToServer(RequestHuntOrderScreenC2SPacket.TYPE, RequestHuntOrderScreenC2SPacket.STREAM_CODEC, RequestHuntOrderScreenC2SPacket::handle);
        registrar.playToServer(UpdateWanderingSkinPoolC2SPacket.TYPE, UpdateWanderingSkinPoolC2SPacket.STREAM_CODEC, UpdateWanderingSkinPoolC2SPacket::handle);
        registrar.playToServer(WanderingMaidDecisionC2SPacket.TYPE, WanderingMaidDecisionC2SPacket.STREAM_CODEC, WanderingMaidDecisionC2SPacket::handle);
        registrar.playToServer(ExpelMaidC2SPacket.TYPE, ExpelMaidC2SPacket.STREAM_CODEC, ExpelMaidC2SPacket::handle);
        registrar.playToServer(OpenMaidStatusC2SPacket.TYPE, OpenMaidStatusC2SPacket.STREAM_CODEC, OpenMaidStatusC2SPacket::handle);
        registrar.playToServer(RequestTradingMaidScreenC2SPacket.TYPE, RequestTradingMaidScreenC2SPacket.STREAM_CODEC, RequestTradingMaidScreenC2SPacket::handle);
        registrar.playToServer(TradingMaidActionC2SPacket.TYPE, TradingMaidActionC2SPacket.STREAM_CODEC, TradingMaidActionC2SPacket::handle);
        registrar.playToServer(DispatchActionC2SPacket.TYPE, DispatchActionC2SPacket.STREAM_CODEC, DispatchActionC2SPacket::handle);
        registrar.playToServer(NpcEventChoiceC2SPacket.TYPE, NpcEventChoiceC2SPacket.STREAM_CODEC, NpcEventChoiceC2SPacket::handle);
        registrar.playToServer(PrincessCarryActionC2SPacket.TYPE, PrincessCarryActionC2SPacket.STREAM_CODEC, PrincessCarryActionC2SPacket::handle);

        // 防服务端崩溃
        if(FMLEnvironment.dist.isClient()){
            registrar.playToClient(OpenEmotionBookScreenS2CPacket.TYPE, OpenEmotionBookScreenS2CPacket.STREAM_CODEC, OpenEmotionBookScreenS2CPacket::handle);
            registrar.playToClient(CopyEntityUuidS2CPacket.TYPE, CopyEntityUuidS2CPacket.STREAM_CODEC, CopyEntityUuidS2CPacket::handle);
            registrar.playToClient(MaidListS2CPacket.TYPE, MaidListS2CPacket.STREAM_CODEC, MaidListS2CPacket::handle);
            registrar.playToClient(OpenHuntOrderScreenS2CPacket.TYPE, OpenHuntOrderScreenS2CPacket.STREAM_CODEC, OpenHuntOrderScreenS2CPacket::handle);
            registrar.playToClient(OpenWanderingMaidRequestS2CPacket.TYPE, OpenWanderingMaidRequestS2CPacket.STREAM_CODEC, OpenWanderingMaidRequestS2CPacket::handle);
            registrar.playToClient(OpenWanderingSkinPoolS2CPacket.TYPE, OpenWanderingSkinPoolS2CPacket.STREAM_CODEC, OpenWanderingSkinPoolS2CPacket::handle);
            registrar.playToClient(EnableTradingMaidButtonS2CPacket.TYPE, EnableTradingMaidButtonS2CPacket.STREAM_CODEC, EnableTradingMaidButtonS2CPacket::handle);
            registrar.playToClient(OpenTradingMaidScreenS2CPacket.TYPE, OpenTradingMaidScreenS2CPacket.STREAM_CODEC, OpenTradingMaidScreenS2CPacket::handle);
            registrar.playToClient(OpenDispatchScreenS2CPacket.TYPE, OpenDispatchScreenS2CPacket.STREAM_CODEC, OpenDispatchScreenS2CPacket::handle);
            registrar.playToClient(OpenNpcEventS2CPacket.TYPE, OpenNpcEventS2CPacket.STREAM_CODEC, OpenNpcEventS2CPacket::handle);
        }else {
            registrar.playToClient(OpenEmotionBookScreenS2CPacket.TYPE, OpenEmotionBookScreenS2CPacket.STREAM_CODEC, (openEmotionBookScreenS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(CopyEntityUuidS2CPacket.TYPE, CopyEntityUuidS2CPacket.STREAM_CODEC, (copyEntityUuidS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(MaidListS2CPacket.TYPE, MaidListS2CPacket.STREAM_CODEC, (maidListS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(OpenHuntOrderScreenS2CPacket.TYPE, OpenHuntOrderScreenS2CPacket.STREAM_CODEC, (openHuntOrderScreenS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(OpenWanderingMaidRequestS2CPacket.TYPE, OpenWanderingMaidRequestS2CPacket.STREAM_CODEC, (openWanderingMaidRequestS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(OpenWanderingSkinPoolS2CPacket.TYPE, OpenWanderingSkinPoolS2CPacket.STREAM_CODEC, (openWanderingSkinPoolS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(EnableTradingMaidButtonS2CPacket.TYPE, EnableTradingMaidButtonS2CPacket.STREAM_CODEC, (enableTradingMaidButtonS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(OpenTradingMaidScreenS2CPacket.TYPE, OpenTradingMaidScreenS2CPacket.STREAM_CODEC, (openTradingMaidScreenS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(OpenDispatchScreenS2CPacket.TYPE, OpenDispatchScreenS2CPacket.STREAM_CODEC, (openDispatchScreenS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(OpenNpcEventS2CPacket.TYPE, OpenNpcEventS2CPacket.STREAM_CODEC, (openNpcEventS2CPacket, iPayloadContext) -> {});
        }
    }
}
