package com.github.JumDa5he.callresponse.compat.jade;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.api.event.AddJadeInfoEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class CallResponseJadePlugin implements IWailaPlugin{
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        NeoForge.EVENT_BUS.register(CallResponseJadePlugin.class);
    }

    @SubscribeEvent
    public static void addJadeInfo(AddJadeInfoEvent event){
        if(Minecraft.getInstance().hasShiftDown()){
            var mood = EmotionData.get(event.getMaid(), Minecraft.getInstance().getGameProfile().id());
            var hunger = HungerData.get(event.getMaid());
            event.getTooltip().add(Component.translatable("text.callresponse.jade.trust", mood.trust()));
            event.getTooltip().add(Component.translatable("text.callresponse.jade.fear", mood.fear()));
            event.getTooltip().add(Component.translatable("text.callresponse.jade.hunger", hunger));
        }else{
            var name = EmotionData.getTendency(event.getMaid(), Minecraft.getInstance().getGameProfile().id()).getName();
            event.getTooltip().add(Component.translatable("text.callresponse.jade.all", name.getString()));
        }
    }
}
