package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionDevotedManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderInteractListener;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * C2S：在狩猎终端里点选某个死忠女仆，请求打开她的名单管理界面。
 * 服务端校验归属/死忠后回发 OpenHuntOrderScreenS2CPacket。
 */
public record RequestHuntOrderScreenC2SPacket(UUID maidUuid) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestHuntOrderScreenC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("callresponse", "request_hunt_order_screen"));

    public static final StreamCodec<ByteBuf, RequestHuntOrderScreenC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, RequestHuntOrderScreenC2SPacket pkt) {
            buf.writeLong(pkt.maidUuid.getMostSignificantBits());
            buf.writeLong(pkt.maidUuid.getLeastSignificantBits());
        }

        @Override
        public RequestHuntOrderScreenC2SPacket decode(ByteBuf buf) {
            return new RequestHuntOrderScreenC2SPacket(new UUID(buf.readLong(), buf.readLong()));
        }
    };

    public static void handle(RequestHuntOrderScreenC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            if (!holdingHuntOrder(player)) return;

            Entity entity = HuntOrderManager.resolveEntity(server, message.maidUuid);
            if (!(entity instanceof EntityMaid maid)) {
                player.sendSystemMessage(Component.literal("§c[狩猎令] 该女仆不在线或未加载。"));
                return;
            }
            if (!player.getUUID().equals(maid.getOwnerUUID())) {
                player.sendSystemMessage(Component.literal("§c[狩猎令] 这不是你的女仆。"));
                return;
            }
            if (!EmotionDevotedManager.isDevoted(maid, player)) {
                player.sendSystemMessage(Component.literal("§c[狩猎令] 她还不是死忠女仆，无法下达狩猎令。"));
                return;
            }
            PacketDistributor.sendToPlayer(player,
                    new OpenHuntOrderScreenS2CPacket(message.maidUuid, HuntOrderInteractListener.buildEntryPayload(maid)));
        });
    }

    private static boolean holdingHuntOrder(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.HUNT_ORDER.get())
                || player.getOffhandItem().is(ModItems.HUNT_ORDER.get());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
