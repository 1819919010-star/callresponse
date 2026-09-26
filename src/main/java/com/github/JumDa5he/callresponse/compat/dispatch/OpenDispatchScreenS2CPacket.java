package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record OpenDispatchScreenS2CPacket(List<EventInfo> events, List<MaidInfo> maids,
                                          List<ActiveInfo> active, int limit) implements CustomPacketPayload {
    public record EventInfo(String id, String category, Component title, Component description, int durationMin,
                            int durationMax, List<ItemStack> rewards) {
    }

    public record MaidInfo(UUID id, String name, String modelId) {
    }

    public record ActiveInfo(UUID dispatchId, Component name, String modelId, Component title, long finishAt) {
    }

    public static final CustomPacketPayload.Type<OpenDispatchScreenS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_dispatch_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDispatchScreenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, OpenDispatchScreenS2CPacket pkt) {
            buf.writeVarInt(pkt.limit);
            writeEvents(buf, pkt.events);
            writeMaids(buf, pkt.maids);
            writeActive(buf, pkt.active);
        }

        @Override
        public OpenDispatchScreenS2CPacket decode(RegistryFriendlyByteBuf buf) {
            int limit = buf.readVarInt();
            return new OpenDispatchScreenS2CPacket(readEvents(buf), readMaids(buf), readActive(buf), limit);
        }
    };

    public OpenDispatchScreenS2CPacket {
        events = List.copyOf(events);
        maids = List.copyOf(maids);
        active = List.copyOf(active);
    }

    public static void handle(OpenDispatchScreenS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> DispatchScreen.open(message.events, message.maids, message.active, message.limit));
    }

    private static void writeEvents(RegistryFriendlyByteBuf buf, List<EventInfo> events) {
        buf.writeVarInt(events.size());
        for (EventInfo event : events) {
            buf.writeUtf(event.id(), 256);
            buf.writeUtf(event.category(), 16);
            ComponentSerialization.STREAM_CODEC.encode(buf, event.title);
            ComponentSerialization.STREAM_CODEC.encode(buf, event.description);
            buf.writeVarInt(event.durationMin());
            buf.writeVarInt(event.durationMax());
            buf.writeVarInt(event.rewards().size());
            for (ItemStack stack : event.rewards()) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            }
        }
    }

    private static List<EventInfo> readEvents(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<EventInfo> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf(256);
            String category = buf.readUtf(16);
            Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
            Component description = ComponentSerialization.STREAM_CODEC.decode(buf);
            int min = buf.readVarInt();
            int max = buf.readVarInt();
            int rewardSize = buf.readVarInt();
            List<ItemStack> rewards = new ArrayList<>(rewardSize);
            for (int j = 0; j < rewardSize; j++) {
                rewards.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            result.add(new EventInfo(id, category, title, description, min, max, List.copyOf(rewards)));
        }
        return result;
    }

    private static void writeMaids(RegistryFriendlyByteBuf buf, List<MaidInfo> maids) {
        buf.writeVarInt(maids.size());
        for (MaidInfo maid : maids) {
            buf.writeUUID(maid.id());
            buf.writeUtf(maid.name(), 256);
            buf.writeUtf(maid.modelId(), 256);
        }
    }

    private static List<MaidInfo> readMaids(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MaidInfo> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new MaidInfo(buf.readUUID(), buf.readUtf(256), buf.readUtf(256)));
        }
        return result;
    }

    private static void writeActive(RegistryFriendlyByteBuf buf, List<ActiveInfo> active) {
        buf.writeVarInt(active.size());
        for (ActiveInfo info : active) {
            buf.writeUUID(info.dispatchId());
            ComponentSerialization.STREAM_CODEC.encode(buf, info.name);
            buf.writeUtf(info.modelId(), 256);
            ComponentSerialization.STREAM_CODEC.encode(buf, info.title);
            buf.writeLong(info.finishAt());
        }
    }

    private static List<ActiveInfo> readActive(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ActiveInfo> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new ActiveInfo(buf.readUUID(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(256), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readLong()));
        }
        return result;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
