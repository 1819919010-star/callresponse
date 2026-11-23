package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only indexes travel over the network; each client uses its complete selected language text. */
public record OpenRemnantMemoryS2CPacket(int first, int second) implements CustomPacketPayload {
    public static final Type<OpenRemnantMemoryS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_remnant_memory"));
    public static final StreamCodec<FriendlyByteBuf, OpenRemnantMemoryS2CPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenRemnantMemoryS2CPacket::write, OpenRemnantMemoryS2CPacket::decode);

    public boolean valid() {
        return first >= 0 && first < RemnantMemoryItem.MEMORY_COUNT
                && second >= 0 && second < RemnantMemoryItem.MEMORY_COUNT && first != second;
    }
    public static void encode(OpenRemnantMemoryS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.first);
        buffer.writeVarInt(message.second);
    }
    private void write(FriendlyByteBuf buffer) { encode(this, buffer); }
    public static OpenRemnantMemoryS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenRemnantMemoryS2CPacket(buffer.readVarInt(), buffer.readVarInt());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
