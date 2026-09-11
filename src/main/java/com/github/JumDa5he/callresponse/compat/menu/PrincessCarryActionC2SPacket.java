package com.github.JumDa5he.callresponse.compat.menu;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 设置页“抱起女仆”按钮使用的服务器指令。 */
public final class PrincessCarryActionC2SPacket {
    private final int maidId;

    public PrincessCarryActionC2SPacket(int maidId) {
        this.maidId = maidId;
    }

    public PrincessCarryActionC2SPacket(FriendlyByteBuf buffer) {
        maidId = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(maidId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Entity entity = player.level().getEntity(maidId);
            if (entity instanceof EntityMaid maid) {
                PrincessCarryManager.request(player, maid);
            }
        });
        context.setPacketHandled(true);
    }
}
