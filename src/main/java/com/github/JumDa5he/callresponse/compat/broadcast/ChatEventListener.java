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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.ServerChatEvent;

import java.util.List;

public class ChatEventListener {

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getRawText();
        EmotionActiveDialogue.onPlayerSpoke(event.getPlayer());

        MaidResponder.debug(player, "§e[调试] 监听到: " + message);

        String prefix = BroadcastConfig.TRIGGER_PREFIX.get();
        if (!message.startsWith(prefix)) {
            TalkEventManager.handleOwnerChat(event.getPlayer(), message);
            return;
        }

        event.setCanceled(true);

        String command = message.substring(prefix.length()).trim();
        if (command.isEmpty()) {
            MaidResponder.debug(player, "§c[广播] 指令为空");
            return;
        }

        MaidResponder.debug(player, "§e[调试] 命令: " + command);

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

        MaidResponder.debug(player, "§e[调试] 找到女仆: " + maids.size());

        if (maids.isEmpty()) {
            if (foundBeforeTalkRouting > 0) {
                player.displayClientMessage(Component.literal("§e[广播] 谈话中的女仆只会坐在原地回应"), false);
            } else {
                player.displayClientMessage(Component.literal("§c[广播] 周围没有女仆..."), false);
            }
            return;
        }

        player.displayClientMessage(
                Component.literal("§a[广播] 已向 " + maids.size() + " 位女仆传达指令"),
                false
        );

        // 玩家指令传入 true
        MaidResponder.processBroadcast(player, maids, command, true);
    }
}
