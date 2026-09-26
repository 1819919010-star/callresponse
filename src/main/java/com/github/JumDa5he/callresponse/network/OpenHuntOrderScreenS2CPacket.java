package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.client.gui.HuntOrderScreen;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record OpenHuntOrderScreenS2CPacket(UUID maidUUID, List<HuntOrderEntry> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenHuntOrderScreenS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_hunt_order"));

    public static final StreamCodec<ByteBuf, OpenHuntOrderScreenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, OpenHuntOrderScreenS2CPacket pkt) {
            buf.writeLong(pkt.maidUUID.getMostSignificantBits());
            buf.writeLong(pkt.maidUUID.getLeastSignificantBits());
            buf.writeInt(pkt.entries.size());
            for (HuntOrderEntry entry : pkt.entries) {
                buf.writeLong(entry.uuid.getMostSignificantBits());
                buf.writeLong(entry.uuid.getLeastSignificantBits());
                byte[] name = entry.name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                buf.writeShort(name.length);
                buf.writeBytes(name);
                buf.writeBoolean(entry.isPlayer);
            }
        }

        @Override
        public OpenHuntOrderScreenS2CPacket decode(ByteBuf buf) {
            UUID maidUUID = new UUID(buf.readLong(), buf.readLong());
            int count = buf.readInt();
            List<HuntOrderEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID uuid = new UUID(buf.readLong(), buf.readLong());
                int nameLen = buf.readShort();
                byte[] nameBytes = new byte[nameLen];
                buf.readBytes(nameBytes);
                String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
                boolean isPlayer = buf.readBoolean();
                entries.add(new HuntOrderEntry(uuid, name, isPlayer));
            }
            return new OpenHuntOrderScreenS2CPacket(maidUUID, entries);
        }
    };

    public static void handle(OpenHuntOrderScreenS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> HuntOrderScreen.refresh(message.maidUUID, message.entries));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
