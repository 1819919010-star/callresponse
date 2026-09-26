package com.github.JumDa5he.callresponse.compat.menu;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 设置页“抱起女仆”按钮使用的服务器指令。 */
public record PrincessCarryActionC2SPacket(int maidId) implements CustomPacketPayload {
    public static final Type<PrincessCarryActionC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "princess_carry_action"));
    public static final StreamCodec<FriendlyByteBuf, PrincessCarryActionC2SPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> buffer.writeVarInt(packet.maidId),
                    buffer -> new PrincessCarryActionC2SPacket(buffer.readVarInt()));

    public static void handle(PrincessCarryActionC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Entity entity = player.level().getEntity(message.maidId);
            if (entity instanceof EntityMaid maid) {
                PrincessCarryManager.request(player, maid);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
