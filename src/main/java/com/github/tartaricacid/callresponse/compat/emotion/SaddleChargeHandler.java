package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.callresponse.compat.gui.DropMaidC2SPacket;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

@EventBusSubscriber(Dist.CLIENT)
public class SaddleChargeHandler {
    private static float chargePercent = 0;
    private static boolean pressed = false;

    private static Consumer<Boolean> callback = (b -> {});
    @SubscribeEvent
    public static void onMousePress(InputEvent.MouseButton.Pre event){
        if(!Minecraft.getInstance().options.keyUse.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(event.getButton())))return;
        var player = Minecraft.getInstance().player;
        if(player == null)return;
        if(!player.isShiftKeyDown())return;
        if(!(player.getFirstPassenger() instanceof EntityMaid maid) || !player.getMainHandItem().is(Items.SADDLE) && !player.getOffhandItem().is(Items.SADDLE))return;
        if(event.getAction() == 0){
            pressed = false;
            SaddleLaunchHandler.dropMaid(maid, player, chargePercent);
            PacketDistributor.sendToServer(new DropMaidC2SPacket(chargePercent));
        } else {
            chargePercent = 0;
            pressed = true;
        }

        callback.accept(pressed);  // 埋下伏笔
        event.setCanceled(true);
    }

    public static void setCallback(Consumer<Boolean> callback) {
        SaddleChargeHandler.callback = callback;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event){
        if(!pressed)return;
        chargePercent += 0.03f;
        chargePercent = Math.clamp(chargePercent, 0, 1);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!pressed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth = 60;
        int barHeight = 8;
        int x = (screenWidth - barWidth) / 2;
        int y = screenHeight / 2 + 25;

        int fillWidth = (int)(chargePercent * barWidth);

        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + barHeight, 0xFF00FF00);
        }
        guiGraphics.renderOutline(x, y, barWidth, barHeight, 0xFFFFFFFF);
    }
}
