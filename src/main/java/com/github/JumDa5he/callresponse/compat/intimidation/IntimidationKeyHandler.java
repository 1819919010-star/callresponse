package com.github.JumDa5he.callresponse.compat.intimidation;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class IntimidationKeyHandler {
    private static final KeyMapping CAST = new KeyMapping("key.callresponse.intimidation",
            KeyConflictContext.IN_GAME, KeyModifier.ALT, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L, "key.categories.callresponse");

    private IntimidationKeyHandler() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CAST);
        NeoForge.EVENT_BUS.addListener(IntimidationKeyHandler::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (CAST.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new IntimidationCastC2SPacket());
            }
        }
    }
}
