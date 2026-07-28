package com.github.tartaricacid.callresponse.compat.broadcast;

import com.github.tartaricacid.callresponse.config.BroadcastConfig;
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

        MaidResponder.debug(player, "§e[调试] 监听到: " + message);

        String prefix = BroadcastConfig.TRIGGER_PREFIX.get();
        if (!message.startsWith(prefix)) {
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
                (maid) -> maid.isAlive() && maid.getOwner() != null
        );

        MaidResponder.debug(player, "§e[调试] 找到女仆: " + maids.size());

        if (maids.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[广播] 周围没有女仆..."));
            return;
        }

        player.sendSystemMessage(
                Component.literal("§a[广播] 已向 " + maids.size() + " 位女仆传达指令")
        );

        // 玩家指令传入 true
        MaidResponder.processBroadcast(player, maids, command, true);
    }
}