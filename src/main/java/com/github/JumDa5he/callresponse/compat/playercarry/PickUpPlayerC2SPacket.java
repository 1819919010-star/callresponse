package com.github.JumDa5he.callresponse.compat.playercarry;

import com.github.JumDa5he.callresponse.compat.menu.PrincessCarryContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** The sender is the only possible passenger. The existing maid-pickup packet is unchanged. */
public record PickUpPlayerC2SPacket(int maidId) {
    public static void encode(PickUpPlayerC2SPacket packet, FriendlyByteBuf buffer) { buffer.writeVarInt(packet.maidId); }
    public static PickUpPlayerC2SPacket decode(FriendlyByteBuf buffer) { return new PickUpPlayerC2SPacket(buffer.readVarInt()); }
    public static void handle(PickUpPlayerC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            boolean started = player.containerMenu instanceof PrincessCarryContainer menu
                    && menu.getMaid() != null && menu.getMaid().getId() == packet.maidId
                    && menu.stillValid(player) && PlayerCarryManager.start(player, menu.getMaid());
            player.displayClientMessage(Component.translatable(started
                    ? "message.callresponse.player_hold.hint" : "message.callresponse.player_hold.failed"), true);
        });
        context.setPacketHandled(true);
    }
}
