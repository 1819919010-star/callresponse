package com.github.JumDa5he.callresponse.compat.playercarry.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.playercarry.PlayerCarryManager;
import com.github.JumDa5he.callresponse.compat.playercarry.PlayerCarrySyncS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** No renderer changes: keep vanilla camera/look/chat and use normal passenger positioning. */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT)
public final class PlayerCarryClient {
    public static void accept(PlayerCarrySyncS2CPacket packet) {
        if (packet.holderId() == null) com.github.JumDa5he.callresponse.compat.client.ysm.YsmPrincessCarryBridge.forgetPlayer(packet.playerId());
        if (Minecraft.getInstance().level != null) PlayerCarryManager.acceptClient(packet);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void input(MovementInputUpdateEvent event) {
        if (!PlayerCarryManager.isHeld(event.getEntity())) return;
        var input = event.getInput();
        input.up = input.down = input.left = input.right = input.jumping = false;
        input.forwardImpulse = input.leftImpulse = 0;
        // Sneak is deliberately retained as vanilla's emergency dismount request.
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && PlayerCarryManager.isHeld(player)) {
            event.setCanceled(true); event.setSwingHand(false);
        }
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerCarryManager.clearClient();
        com.github.JumDa5he.callresponse.compat.client.ysm.YsmPrincessCarryBridge.clearPlayers();
    }
    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof Player player) {
            PlayerCarryManager.forgetClient(player.getUUID());
            com.github.JumDa5he.callresponse.compat.client.ysm.YsmPrincessCarryBridge.forgetPlayer(player.getUUID());
        }
    }
    private PlayerCarryClient() {}
}
