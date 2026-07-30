package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

public class EmotionEventListener {

    @SubscribeEvent
    public void onMaidInteract(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();

        if (EmotionBetrayalManager.isBetraying(maid)) {
            event.setCanceled(true);
            return;
        }

        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                EmotionData.EmotionValues values = EmotionData.get(maid, serverPlayer);
                serverPlayer.sendSystemMessage(
                        Component.literal("")
                                .append(maid.getName().copy().withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("§6 的信任值: §a" + values.trust() + " §6恐惧值: §c" + values.fear()))
                );
            }
        }
    }
}
