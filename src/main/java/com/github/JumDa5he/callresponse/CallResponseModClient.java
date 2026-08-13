package com.github.JumDa5he.callresponse;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.client.renderer.MaidCropBlockRenderer;
import com.github.JumDa5he.callresponse.compat.menu.ModMenuClientEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CallResponseMod.MOD_ID, dist = Dist.CLIENT)
public class CallResponseModClient {
    public CallResponseModClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(ModMenuClientEvents::clientSetup);
    }

    @EventBusSubscriber(Dist.CLIENT)
    public static class EventHandler{
        @SubscribeEvent
        public static void onRegistryRenderer(FMLClientSetupEvent event){
            BlockEntityRenderers.register(
                    ModBlocks.MAID_CROP_BLOCK_ENTITY.get(),
                    context -> new MaidCropBlockRenderer()
            );
        }
    }
}
