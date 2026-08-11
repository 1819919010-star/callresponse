package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderData;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderInteractListener;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * C2S：添加（名字或 UUID）或删除（UUID）狩猎名单条目。
 * 服务端校验归属后修改女仆 NBT，并把最新名单回发。
 */
public record HuntOrderUpdateC2SPacket(UUID maidUUID, boolean remove, String value) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HuntOrderUpdateC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "hunt_order_update"));

    public static final StreamCodec<ByteBuf, HuntOrderUpdateC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, HuntOrderUpdateC2SPacket pkt) {
            buf.writeLong(pkt.maidUUID.getMostSignificantBits());
            buf.writeLong(pkt.maidUUID.getLeastSignificantBits());
            buf.writeBoolean(pkt.remove);
            byte[] value = pkt.value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeShort(value.length);
            buf.writeBytes(value);
        }

        @Override
        public HuntOrderUpdateC2SPacket decode(ByteBuf buf) {
            UUID maidUUID = new UUID(buf.readLong(), buf.readLong());
            boolean remove = buf.readBoolean();
            int len = buf.readShort();
            byte[] valueBytes = new byte[len];
            buf.readBytes(valueBytes);
            return new HuntOrderUpdateC2SPacket(maidUUID, remove,
                    new String(valueBytes, java.nio.charset.StandardCharsets.UTF_8));
        }
    };

    public static void handle(HuntOrderUpdateC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            Entity entity = HuntOrderManager.resolveEntity(server, message.maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!player.getUUID().equals(maid.getOwnerUUID())) return;

            if (message.remove) {
                try {
                    HuntOrderData.removeEntry(maid, UUID.fromString(message.value));
                } catch (IllegalArgumentException ignored) {
                }
            } else {
                UUID targetId = resolveTargetUuid(server, message.value);
                if (targetId != null) {
                    String name = message.value;
                    Entity target = HuntOrderManager.resolveEntity(server, targetId);
                    if (target != null) {
                        name = target.getName().getString();
                    }
                    boolean isPlayer = target instanceof Player;
                    if (!HuntOrderData.addEntry(maid, targetId, name, isPlayer)) {
                        player.sendSystemMessage(Component.literal("§c[狩猎令] 名单已满（最多 10 条）或目标已存在。"));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("§c[狩猎令] 找不到玩家 '" + message.value + "' 或无法解析 UUID。"));
                }
            }

            PacketDistributor.sendToPlayer(player,
                    new OpenHuntOrderScreenS2CPacket(message.maidUUID,
                            HuntOrderInteractListener.buildEntryPayload(maid)));
        });
    }

    /**
     * 优先按玩家名解析，其次按 UUID 字符串解析。
     */
    private static UUID resolveTargetUuid(MinecraftServer server, String value) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(value);
        if (online != null) {
            return online.getUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
