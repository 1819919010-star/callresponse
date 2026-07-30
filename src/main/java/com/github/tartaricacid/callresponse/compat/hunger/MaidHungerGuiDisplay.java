package com.github.tartaricacid.callresponse.compat.hunger;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;

public class MaidHungerGuiDisplay {

    // 调试计数器
    private int frameCount = 0;

    @SubscribeEvent
    public void onMaidGuiRender(MaidContainerGuiEvent.Render event) {
        frameCount++;

        if (!(Minecraft.getInstance().screen instanceof AbstractMaidContainerGui<?> gui)) {
            return;
        }

        EntityMaid maid = gui.getMaid();
        if (maid == null) return;

        // 从本地读取
        var hunger = Math.round(HungerData.get(maid));

        GuiGraphics graphics = event.getGraphics();
        int leftPos = event.getLeftPos();
        int topPos = event.getTopPos();

        // ----- 调试信息（每20帧刷新一次，显示在界面） -----
        if (frameCount % 500 == 0) {
            String debugMsg = "饱食度: " + hunger;
            // 输出到游戏聊天栏
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e" + debugMsg));
            }
        }

        // 主显示
        String text = "饥饿值: " + hunger;
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.literal(text),
                leftPos + 35,
                topPos + 30,
                0xFFFFFF
        );
    }
}