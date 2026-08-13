package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.network.OpenEmotionBookScreenS2CPacket;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class EmotionEventListener {

    @SubscribeEvent
    public void onMaidInteract(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();
        ItemStack stack = event.getStack();

        if (EmotionBetrayalManager.isBetraying(maid)) {
            event.setCanceled(true);
            return;
        }

        if (stack.is(ModItems.EMOTION_BOOK.get())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                EmotionData.EmotionValues v = EmotionData.get(maid, serverPlayer);
                int hunger = Math.round(HungerData.get(maid));
                PacketDistributor.sendToPlayer(serverPlayer,
                        new OpenEmotionBookScreenS2CPacket(maid.getUUID(), v.trust(), v.fear(), hunger));
            }
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
