package com.github.tartaricacid.callresponse.compat.jade;

import com.github.tartaricacid.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.api.event.AddJadeInfoEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class CallResponseJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        MinecraftForge.EVENT_BUS.register(CallResponseJadePlugin.class);
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
    }

    @SubscribeEvent
    public static void addJadeInfo(AddJadeInfoEvent event) {
        var playerId = Minecraft.getInstance().getUser().getGameProfile().getId();
        if (Screen.hasShiftDown()) {
            var mood = EmotionData.get(event.getMaid(), playerId);
            var hunger = HungerData.get(event.getMaid());
            event.getTooltip().add(Component.translatable("text.callresponse.jade.trust", mood.trust()));
            event.getTooltip().add(Component.translatable("text.callresponse.jade.fear", mood.fear()));
            event.getTooltip().add(Component.translatable("text.callresponse.jade.hunger", hunger));
        } else {
            var name = EmotionData.getTendency(event.getMaid(), playerId).getName();
            event.getTooltip().add(Component.translatable("text.callresponse.jade.all", name.getString()));
        }
    }
}
