package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionActiveDialogue;
import com.github.JumDa5he.callresponse.compat.npc.NpcEventManager;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;

public class ChatEventListener {

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getRawText();
        EmotionActiveDialogue.onPlayerSpoke(event.getPlayer());

        MaidResponder.debug(player, Component.translatable("message.callresponse.debug.heard", message));

        String prefix = BroadcastConfig.TRIGGER_PREFIX.get();
        if (!message.startsWith(prefix)) {
            TalkEventManager.handleOwnerChat(event.getPlayer(), message);
            return;
        }

        event.setCanceled(true);

        String command = message.substring(prefix.length()).trim();
        if (command.isEmpty()) {
            MaidResponder.debug(player, Component.translatable("message.callresponse.debug.empty_command"));
            return;
        }

        MaidResponder.debug(player, Component.translatable("message.callresponse.debug.command", command));

        Level level = player.level();
        int radius = BroadcastConfig.SEARCH_RADIUS.get();
        AABB area = new AABB(player.blockPosition()).inflate(radius);
        List<EntityMaid> maids = level.getEntitiesOfClass(
                EntityMaid.class, area,
                (maid) -> maid.isAlive() && maid.getOwnerUUID() != null
        );
        // 只记录玩家主动发出的广播，不改变 MaidResponder 的选择与 AI 回应流程。
        maids.stream().filter(maid -> maid.isOwnedBy(event.getPlayer()))
                .forEach(maid -> NpcEventManager.recordOwnerInteraction(maid, event.getPlayer()));
        int foundBeforeTalkRouting = maids.size();
        maids = TalkEventManager.routeBroadcastCommand(event.getPlayer(), maids, command);

        MaidResponder.debug(player, Component.translatable("message.callresponse.debug.maid_count", maids.size()));

        if (maids.isEmpty()) {
            if (foundBeforeTalkRouting > 0) {
                player.displayClientMessage(Component.translatable("message.callresponse.broadcast.talking_only"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.callresponse.broadcast.no_maids"), false);
            }
            return;
        }

        player.displayClientMessage(
                Component.translatable("message.callresponse.broadcast.sent", maids.size()),
                false
        );

        // 玩家指令传入 true
        MaidResponder.processBroadcast(player, maids, command, true);
    }
}
