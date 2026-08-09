package com.github.tartaricacid.callresponse.compat.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C：右键狩猎令时，服务端把所有在线死忠女仆的列表发回客户端。
 */
public class MaidListS2CPacket {
    public static class MaidInfo {
        public final UUID uuid;
        public final String typeKey;
        public final String customName;

        public MaidInfo(UUID uuid, String typeKey, String customName) {
            this.uuid = uuid;
            this.typeKey = typeKey;
            this.customName = customName;
        }
    }

    private final List<MaidInfo> maids;

    public MaidListS2CPacket(List<MaidInfo> maids) {
        this.maids = new ArrayList<>(maids);
    }

    public MaidListS2CPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.maids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUUID();
            String typeKey = buf.readUtf(128);
            boolean hasCustom = buf.readBoolean();
            String customName = hasCustom ? buf.readUtf(64) : null;
            maids.add(new MaidInfo(uuid, typeKey, customName));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(maids.size());
        for (MaidInfo info : maids) {
            buf.writeUUID(info.uuid);
            buf.writeUtf(info.typeKey, 128);
            buf.writeBoolean(info.customName != null);
            if (info.customName != null) {
                buf.writeUtf(info.customName, 64);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> MaidListScreen.open(maids));
        ctx.get().setPacketHandled(true);
    }
}
