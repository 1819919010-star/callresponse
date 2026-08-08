package com.github.tartaricacid.callresponse.compat.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class OpenEmotionBookScreenS2CPacket {
    private final UUID maidUUID;
    private final int trust;
    private final int fear;
    private final int hunger;

    public OpenEmotionBookScreenS2CPacket(UUID maidUUID, int trust, int fear, int hunger) {
        this.maidUUID = maidUUID;
        this.trust = trust;
        this.fear = fear;
        this.hunger = hunger;
    }

    public OpenEmotionBookScreenS2CPacket(FriendlyByteBuf buf) {
        this.maidUUID = buf.readUUID();
        this.trust = buf.readInt();
        this.fear = buf.readInt();
        this.hunger = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidUUID);
        buf.writeInt(trust);
        buf.writeInt(fear);
        buf.writeInt(hunger);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> EmotionBookScreen.open(maidUUID, trust, fear, hunger));
        ctx.get().setPacketHandled(true);
    }
}
