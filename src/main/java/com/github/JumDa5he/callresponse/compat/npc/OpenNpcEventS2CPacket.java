package com.github.JumDa5he.callresponse.compat.npc;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 服务器只发送翻译键，最终显示语言由玩家客户端决定。 */
public record OpenNpcEventS2CPacket(int entityId, UUID maidId, String titleKey,
                                    String descriptionKey, List<String> optionKeys) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenNpcEventS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_npc_event"));

    public static final StreamCodec<FriendlyByteBuf, OpenNpcEventS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, OpenNpcEventS2CPacket pkt) {
            buf.writeVarInt(pkt.entityId);
            buf.writeUUID(pkt.maidId);
            buf.writeUtf(pkt.titleKey);
            buf.writeUtf(pkt.descriptionKey);
            buf.writeVarInt(pkt.optionKeys.size());
            pkt.optionKeys.forEach(buf::writeUtf);
        }

        @Override
        public OpenNpcEventS2CPacket decode(FriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            UUID maidId = buf.readUUID();
            String titleKey = buf.readUtf();
            String descriptionKey = buf.readUtf();
            int size = Math.min(16, Math.max(0, buf.readVarInt()));
            List<String> options = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                options.add(buf.readUtf());
            }
            return new OpenNpcEventS2CPacket(entityId, maidId, titleKey, descriptionKey, List.copyOf(options));
        }
    };

    public OpenNpcEventS2CPacket {
        optionKeys = List.copyOf(optionKeys);
    }

    public static void handle(OpenNpcEventS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> NpcEventScreen.open(message.entityId, message.maidId,
                message.titleKey, message.descriptionKey, message.optionKeys));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
