package com.github.tartaricacid.callresponse.compat.hunger;

import com.github.tartaricacid.callresponse.CallResponseMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record SyncHungerPacket(UUID maidUUID, int hunger) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncHungerPacket> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "sync_hunger"));
    public static final StreamCodec<ByteBuf, SyncHungerPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, SyncHungerPacket pkt) {
            buf.writeLong(pkt.maidUUID.getMostSignificantBits());
            buf.writeLong(pkt.maidUUID.getLeastSignificantBits());
            buf.writeInt(pkt.hunger);
        }
        @Override
        public SyncHungerPacket decode(ByteBuf buf) {
            UUID uuid = new UUID(buf.readLong(), buf.readLong());
            int hunger = buf.readInt();
            return new SyncHungerPacket(uuid, hunger);
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0");
        registrar.playToClient(TYPE, STREAM_CODEC, SyncHungerPacket::handle);
    }

    public static void handle(SyncHungerPacket message, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> HungerClientCache.setHunger(message.maidUUID, message.hunger));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
