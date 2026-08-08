package com.github.tartaricacid.callresponse.compat.gui;

import com.github.tartaricacid.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class EmotionBookUpdateC2SPacket {
    private final UUID maidUUID;
    private final int trust;
    private final int fear;
    private final int hunger;

    public EmotionBookUpdateC2SPacket(UUID maidUUID, int trust, int fear, int hunger) {
        this.maidUUID = maidUUID;
        this.trust = trust;
        this.fear = fear;
        this.hunger = hunger;
    }

    public EmotionBookUpdateC2SPacket(FriendlyByteBuf buf) {
        this.maidUUID = buf.readUUID();
        this.trust = buf.readInt();
        this.fear = buf.readInt();
        this.hunger = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidUUID);
        buf.writeInt(trust);
        buf.writeInt(fear);
        buf.writeInt(hunger);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity entity = player.serverLevel().getEntity(maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!maid.isTame() || !player.getUUID().equals(maid.getOwnerUUID())) return;
            EmotionData.set(maid, player.getUUID(),
                    Math.max(0, Math.min(100, trust)),
                    Math.max(0, Math.min(100, fear)));
            HungerData.set(maid, Math.max(0, Math.min(100, hunger)));
        });
        ctx.get().setPacketHandled(true);
    }
}
