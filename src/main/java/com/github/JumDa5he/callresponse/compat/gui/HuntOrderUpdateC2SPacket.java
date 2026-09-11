package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderData;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S：添加（名字或 UUID）或删除（UUID）狩猎名单条目。
 * 服务端校验归属后修改女仆 NBT，并把最新名单回发。
 */
public class HuntOrderUpdateC2SPacket {
    private final UUID maidUUID;
    private final boolean remove;
    private final String value;

    public HuntOrderUpdateC2SPacket(UUID maidUUID, boolean remove, String value) {
        this.maidUUID = maidUUID;
        this.remove = remove;
        this.value = value;
    }

    public HuntOrderUpdateC2SPacket(FriendlyByteBuf buf) {
        this.maidUUID = buf.readUUID();
        this.remove = buf.readBoolean();
        this.value = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidUUID);
        buf.writeBoolean(remove);
        buf.writeUtf(value, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            Entity entity = HuntOrderManager.resolveEntity(server, maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!player.getUUID().equals(maid.getOwnerUUID())) return;

            if (remove) {
                try {
                    HuntOrderData.removeEntry(maid, UUID.fromString(value));
                } catch (IllegalArgumentException ignored) {
                }
            } else {
                UUID targetId = resolveTargetUuid(server, value);
                if (targetId != null) {
                    String name = value;
                    Entity target = HuntOrderManager.resolveEntity(server, targetId);
                    if (target != null) {
                        name = target.getName().getString();
                    }
                    boolean isPlayer = target instanceof net.minecraft.world.entity.player.Player;
                    if (!HuntOrderData.addEntry(maid, targetId, name, isPlayer)) {
                        player.sendSystemMessage(Component.translatable("message.callresponse.hunt.list_full"));
                    }
                } else if (targetId == null) {
                    player.sendSystemMessage(Component.translatable("message.callresponse.hunt.player_not_found", value));
                }
            }

            CallResponseMod.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new OpenHuntOrderScreenS2CPacket(maidUUID,
                            com.github.JumDa5he.callresponse.compat.hunt.HuntOrderInteractListener.buildEntryPayload(maid)));
        });
        ctx.get().setPacketHandled(true);
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
}