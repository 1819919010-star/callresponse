package com.github.JumDa5he.callresponse;

import com.github.JumDa5he.callresponse.compat.menu.ModMenuClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CallResponseMod.MOD_ID, dist = Dist.CLIENT)
public class CallResponseModClient {
    public CallResponseModClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(ModMenuClientEvents::clientSetup);
    }
}
