package com.github.JumDa5he.callresponse.compat.menu;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public final class OpenMaidStatusC2SPacket {
    private final int maidId;

    public OpenMaidStatusC2SPacket(int maidId) {
        this.maidId = maidId;
    }

    public OpenMaidStatusC2SPacket(FriendlyByteBuf buffer) {
        this.maidId = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(maidId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.level().getEntity(maidId);
            if (!(entity instanceof EntityMaid maid) || !maid.isOwnedBy(player)
                    || !maid.isAlive() || maid.isSleeping() || !player.canReach(maid, 3)) {
                return;
            }
            maid.getNavigation().stop();
            NetworkHooks.openScreen(player, MaidStatusContainer.create(maidId),
                    buffer -> buffer.writeInt(maidId));
        });
        context.setPacketHandled(true);
    }
}
