package com.github.JumDa5he.callresponse.compat.sign;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 编辑界面的提交结果：四行文字 + 颜色，或 {@code clear} 表示取下告示牌。 */
public record MaidSignUpdateC2SPacket(int maidId, List<String> lines, DyeColor color, boolean clear)
        implements CustomPacketPayload {
    private static final int MAX_LINE_BYTES = 384;

    public static final CustomPacketPayload.Type<MaidSignUpdateC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "maid_sign_update"));

    public static final StreamCodec<FriendlyByteBuf, MaidSignUpdateC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buffer, MaidSignUpdateC2SPacket pkt) {
            buffer.writeVarInt(pkt.maidId);
            buffer.writeBoolean(pkt.clear);
            DyeColor.STREAM_CODEC.encode(buffer, pkt.color);
            buffer.writeVarInt(pkt.lines.size());
            for (String line : pkt.lines) {
                buffer.writeUtf(line == null ? "" : line, MAX_LINE_BYTES);
            }
        }

        @Override
        public MaidSignUpdateC2SPacket decode(FriendlyByteBuf buffer) {
            int maidId = buffer.readVarInt();
            boolean clear = buffer.readBoolean();
            DyeColor color = DyeColor.STREAM_CODEC.decode(buffer);
            int size = buffer.readVarInt();
            if (size < 0 || size > MaidSignManager.MAX_LINES) {
                throw new io.netty.handler.codec.DecoderException("Invalid maid sign line count: " + size);
            }
            List<String> lines = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                lines.add(buffer.readUtf(MAX_LINE_BYTES));
            }
            return new MaidSignUpdateC2SPacket(maidId, lines, color, clear);
        }
    };

    public static void handle(MaidSignUpdateC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.serverLevel().getEntity(message.maidId) instanceof EntityMaid maid)) {
                return;
            }
            MaidSignManager.applyClientUpdate(player, maid, message);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
