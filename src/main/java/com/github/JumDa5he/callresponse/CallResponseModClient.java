package com.github.JumDa5he.callresponse;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.github.JumDa5he.callresponse.compat.birthday.client.BirthdayClientData;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT)
public class CallResponseModClient {
    public CallResponseModClient() {
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        BirthdayClientData.onDisconnect();
    }
}
