package com.github.JumDa5he.callresponse.compat.capability;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
    private ModCapabilities() {
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlocks.REWARD_BOX_ENTITY.get(),
                (box, direction) -> box.getItemHandler());
    }
}
