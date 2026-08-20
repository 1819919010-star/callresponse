package com.github.JumDa5he.callresponse.compat.dispatch;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class DispatchActionC2SPacket {
    public enum Action { REQUEST, START, RECALL }
    private final Action action;
    private final String eventId;
    private final UUID targetId;

    public DispatchActionC2SPacket(Action action, String eventId, UUID targetId) { this.action = action; this.eventId = eventId == null ? "" : eventId; this.targetId = targetId == null ? new UUID(0, 0) : targetId; }
    public DispatchActionC2SPacket(FriendlyByteBuf buf) { action = buf.readEnum(Action.class); eventId = buf.readUtf(256); targetId = buf.readUUID(); }
    public void encode(FriendlyByteBuf buf) { buf.writeEnum(action); buf.writeUtf(eventId, 256); buf.writeUUID(targetId); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender();
        context.enqueueWork(() -> { if (player != null) DispatchManager.handle(player, action, eventId, targetId); }); context.setPacketHandled(true);
    }
}
