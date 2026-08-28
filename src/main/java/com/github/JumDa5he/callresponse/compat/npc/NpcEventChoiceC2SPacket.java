package com.github.JumDa5he.callresponse.compat.npc;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class NpcEventChoiceC2SPacket {
    private final UUID maidId;
    private final int optionIndex;

    public NpcEventChoiceC2SPacket(UUID maidId, int optionIndex) {
        this.maidId = maidId;
        this.optionIndex = optionIndex;
    }

    public NpcEventChoiceC2SPacket(FriendlyByteBuf buf) {
        maidId = buf.readUUID();
        optionIndex = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidId);
        buf.writeVarInt(optionIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) NpcEventManager.handleChoice(player, maidId, optionIndex);
        });
        context.setPacketHandled(true);
    }
}
