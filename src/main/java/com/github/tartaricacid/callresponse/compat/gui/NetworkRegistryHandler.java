package com.github.tartaricacid.callresponse.compat.gui;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkRegistryHandler {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.1");
        registrar.playToServer(EmotionBookUpdateC2SPacket.TYPE, EmotionBookUpdateC2SPacket.STREAM_CODEC, EmotionBookUpdateC2SPacket::handle);
        registrar.playToServer(DropMaidC2SPacket.TYPE, DropMaidC2SPacket.STREAM_CODEC, DropMaidC2SPacket::handle);

        // 防服务端崩溃
        if(FMLEnvironment.dist.isClient()){
            registrar.playToClient(OpenEmotionBookScreenS2CPacket.TYPE, OpenEmotionBookScreenS2CPacket.STREAM_CODEC, OpenEmotionBookScreenS2CPacket::handle);
        }else {
            registrar.playToClient(OpenEmotionBookScreenS2CPacket.TYPE, OpenEmotionBookScreenS2CPacket.STREAM_CODEC, (openEmotionBookScreenS2CPacket, iPayloadContext) -> {});
        }
    }
}
