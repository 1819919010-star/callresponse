package com.github.JumDa5he.callresponse.compat.intimidation;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IntimidationKeyHandler {
    private static final KeyMapping CAST = new KeyMapping("key.callresponse.intimidation",
            KeyConflictContext.IN_GAME, KeyModifier.ALT, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L, "key.categories.callresponse");

    private IntimidationKeyHandler() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CAST);
        MinecraftForge.EVENT_BUS.addListener(IntimidationKeyHandler::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        while (CAST.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                CallResponseMod.CHANNEL.sendToServer(new IntimidationCastC2SPacket());
            }
        }
    }
}
