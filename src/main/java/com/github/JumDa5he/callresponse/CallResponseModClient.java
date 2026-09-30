package com.github.JumDa5he.callresponse;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.birthday.client.BirthdayClientData;
import com.github.JumDa5he.callresponse.compat.client.renderer.MaidCropBlockRenderer;
import com.github.JumDa5he.callresponse.compat.hunger.MaidHungerGuiDisplay;
import com.github.JumDa5he.callresponse.compat.menu.ModMenuClientEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CallResponseMod.MOD_ID, dist = Dist.CLIENT)
public class CallResponseModClient {
    public CallResponseModClient(IEventBus modEventBus, ModContainer modContainer) {
        // Configured skips mods that already register a screen factory; keep NeoForge's fallback only when absent.
        if (FMLLoader.getLoadingModList() != null
                && FMLLoader.getLoadingModList().getModFileById("cloth_config") != null) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parent) -> new com.github.JumDa5he.callresponse.config.client.ClothConfigScreens(parent));
        } else if (FMLLoader.getLoadingModList() == null
                || FMLLoader.getLoadingModList().getModFileById("configured") == null) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
        modEventBus.addListener(ModMenuClientEvents::clientSetup);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.github.JumDa5he.callresponse.compat.outpost.entity.OutpostEntities.REVENGE_MAID.get(),
                        com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer::new));
        NeoForge.EVENT_BUS.register(new MaidHungerGuiDisplay());
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

        @SubscribeEvent
        public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            BirthdayClientData.onDisconnect();
        }
    }
}
