package com.github.tartaricacid.callresponse.compat.hunger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class SyncHungerPacket {
    private final UUID maidUUID;
    private final int hunger;

    public SyncHungerPacket(UUID maidUUID, int hunger) {
        this.maidUUID = maidUUID;
        this.hunger = hunger;
    }

    public SyncHungerPacket(FriendlyByteBuf buf) {
        this.maidUUID = buf.readUUID();
        this.hunger = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidUUID);
        buf.writeInt(hunger);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 更新客户端缓存
            HungerClientCache.setHunger(maidUUID, hunger);
        });
        ctx.get().setPacketHandled(true);
    }
}