package com.github.JumDa5he.callresponse.compat.menu;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenMaidStatusC2SPacket(int maidId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenMaidStatusC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_maid_status"));

    public static final StreamCodec<FriendlyByteBuf, OpenMaidStatusC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buffer, OpenMaidStatusC2SPacket pkt) {
            buffer.writeVarInt(pkt.maidId);
        }

        @Override
        public OpenMaidStatusC2SPacket decode(FriendlyByteBuf buffer) {
            return new OpenMaidStatusC2SPacket(buffer.readVarInt());
        }
    };

    public static void handle(OpenMaidStatusC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                return;
            }
            Entity entity = player.level().getEntity(message.maidId);
            if (!(entity instanceof EntityMaid maid) || !maid.isOwnedBy(player)
                    || !maid.isAlive() || maid.isSleeping() || !player.canInteractWithEntity(maid, 4.0)) {
                return;
            }
            maid.getNavigation().stop();
            player.openMenu(MaidStatusContainer.create(message.maidId),
                    buffer -> buffer.writeInt(message.maidId));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
