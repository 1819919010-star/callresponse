package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionDevotedManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderInteractListener;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S：在狩猎终端里点选某个死忠女仆，请求打开她的名单管理界面。
 * 服务端校验归属/死忠后回发 OpenHuntOrderScreenS2CPacket。
 */
public class RequestHuntOrderScreenC2SPacket {
    private final UUID maidUuid;

    public RequestHuntOrderScreenC2SPacket(UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    public RequestHuntOrderScreenC2SPacket(FriendlyByteBuf buf) {
        this.maidUuid = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidUuid);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            if (!holdingHuntOrder(player)) return;

            Entity entity = HuntOrderManager.resolveEntity(server, maidUuid);
            if (!(entity instanceof EntityMaid maid)) {
                player.sendSystemMessage(Component.translatable("message.callresponse.hunt.offline"));
                return;
            }
            if (!player.getUUID().equals(maid.getOwnerUUID())) {
                player.sendSystemMessage(Component.translatable("message.callresponse.hunt.not_owner"));
                return;
            }
            if (!EmotionDevotedManager.isDevoted(maid, player)) {
                player.sendSystemMessage(Component.translatable("message.callresponse.hunt.not_devoted"));
                return;
            }
            CallResponseMod.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new OpenHuntOrderScreenS2CPacket(maidUuid, HuntOrderInteractListener.buildEntryPayload(maid)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean holdingHuntOrder(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.HUNT_ORDER.get())
                || player.getOffhandItem().is(ModItems.HUNT_ORDER.get());
    }
}
