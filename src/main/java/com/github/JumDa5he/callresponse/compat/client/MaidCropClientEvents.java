package com.github.JumDa5he.callresponse.compat.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.client.renderer.MaidCropBlockRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MaidCropClientEvents {
    private MaidCropClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.github.JumDa5he.callresponse.compat.outpost.entity.OutpostEntities.REVENGE_MAID.get(),
                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.MAID_CROP_BLOCK_ENTITY.get(), MaidCropBlockRenderer::new);
    }
}
