package com.github.JumDa5he.callresponse.compat.gui;

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

        // 防服务端崩溃
        if(FMLEnvironment.dist.isClient()){
            registrar.playToClient(OpenEmotionBookScreenS2CPacket.TYPE, OpenEmotionBookScreenS2CPacket.STREAM_CODEC, OpenEmotionBookScreenS2CPacket::handle);
            registrar.playToClient(CopyEntityUuidS2CPacket.TYPE, CopyEntityUuidS2CPacket.STREAM_CODEC, CopyEntityUuidS2CPacket::handle);
            registrar.playToClient(MaidListS2CPacket.TYPE, MaidListS2CPacket.STREAM_CODEC, MaidListS2CPacket::handle);
            registrar.playToClient(OpenHuntOrderScreenS2CPacket.TYPE, OpenHuntOrderScreenS2CPacket.STREAM_CODEC, OpenHuntOrderScreenS2CPacket::handle);
        }else {
            registrar.playToClient(OpenEmotionBookScreenS2CPacket.TYPE, OpenEmotionBookScreenS2CPacket.STREAM_CODEC, (openEmotionBookScreenS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(CopyEntityUuidS2CPacket.TYPE, CopyEntityUuidS2CPacket.STREAM_CODEC, (copyEntityUuidS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(MaidListS2CPacket.TYPE, MaidListS2CPacket.STREAM_CODEC, (maidListS2CPacket, iPayloadContext) -> {});
            registrar.playToClient(OpenHuntOrderScreenS2CPacket.TYPE, OpenHuntOrderScreenS2CPacket.STREAM_CODEC, (openHuntOrderScreenS2CPacket, iPayloadContext) -> {});
        }
    }
}
