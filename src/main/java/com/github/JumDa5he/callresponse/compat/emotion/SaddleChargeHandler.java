package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.gui.DropMaidC2SPacket;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SaddleChargeHandler {
    private static float chargePercent = 0;
    private static boolean pressed = false;

    private static Consumer<Boolean> callback = (b -> {});

    @SubscribeEvent
    public static void onMousePress(InputEvent.MouseButton.Pre event) {
        if (!Minecraft.getInstance().options.keyUse.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!player.isShiftKeyDown()) return;
        if (!(player.getFirstPassenger() instanceof EntityMaid maid) || !player.getMainHandItem().is(Items.SADDLE) && !player.getOffhandItem().is(Items.SADDLE)) return;
        if (event.getAction() == 0) {
            pressed = false;
            SaddleLaunchHandler.dropMaid(maid, player, chargePercent);
            CallResponseMod.CHANNEL.sendToServer(new DropMaidC2SPacket(chargePercent));
        } else {
            chargePercent = 0;
            pressed = true;
        }

        callback.accept(pressed);  // 埋下伏笔
        event.setCanceled(true);
        // 修复：取消事件后手动复位右键状态，防止 MC 认为右键一直被按住（自动持续右键）
        Minecraft.getInstance().options.keyUse.setDown(false);
    }

    public static void setCallback(Consumer<Boolean> callback) {
        SaddleChargeHandler.callback = callback;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!pressed) return;
        chargePercent += 0.03f;
        if (chargePercent > 1) {
            chargePercent = 1;
            pressed = false;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (!player.isShiftKeyDown()) return;
            if (!(player.getFirstPassenger() instanceof EntityMaid maid) || !player.getMainHandItem().is(Items.SADDLE) && !player.getOffhandItem().is(Items.SADDLE)) return;
            SaddleLaunchHandler.dropMaid(maid, player, chargePercent);
            CallResponseMod.CHANNEL.sendToServer(new DropMaidC2SPacket(chargePercent));
            // 修复：自动丢出后同样复位右键状态，防止后续自动持续右键
            Minecraft.getInstance().options.keyUse.setDown(false);
        }
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
