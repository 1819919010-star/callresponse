package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.emotion.MaidExpelManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;


public final class ExpelMaidC2SPacket {
    private final UUID maidId;
    private final boolean confirm;

    public ExpelMaidC2SPacket(UUID maidId, boolean confirm) {
        this.maidId = maidId;
        this.confirm = confirm;
    }

    public ExpelMaidC2SPacket(FriendlyByteBuf buffer) {
        maidId = buffer.readUUID();
        confirm = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidId);
        buffer.writeBoolean(confirm);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!(player.serverLevel().getEntity(maidId) instanceof EntityMaid maid) || !maid.isOwnedBy(player)) {
                return;
            }
            if (confirm) {
                MaidExpelManager.expel(player, maid);
            } else {
                MaidExpelManager.frighten(player, maid);
            }
        });
        context.setPacketHandled(true);
    }
}
