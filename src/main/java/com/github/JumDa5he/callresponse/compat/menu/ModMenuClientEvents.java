package com.github.JumDa5he.callresponse.compat.menu;

import com.github.JumDa5he.callresponse.compat.client.gui.MaidStatusContainerGui;
import com.github.JumDa5he.callresponse.compat.client.gui.RewardBoxScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ModMenuClientEvents {
    private ModMenuClientEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MAID_STATUS.get(), MaidStatusContainerGui::new);
        event.register(ModMenus.REWARD_BOX.get(), RewardBoxScreen::new);
    }
}
