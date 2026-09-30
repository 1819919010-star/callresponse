package com.github.JumDa5he.callresponse.compat.birthday;

import com.github.JumDa5he.callresponse.CallResponseMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

/**
 * 客户端提交生日设置。{@code month <= 0} 视为清除设置。
 * 服务端会再次校验日期合法性，避免被篡改的客户端写入非法数据。
 */
public record BirthdaySetC2SPacket(int month, int day) implements CustomPacketPayload {
    public static final int CLEAR = 0;

    public static final CustomPacketPayload.Type<BirthdaySetC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    CallResponseMod.MOD_ID, "birthday_set"));

    public static final StreamCodec<ByteBuf, BirthdaySetC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, BirthdaySetC2SPacket pkt) {
            buf.writeInt(pkt.month);
            buf.writeInt(pkt.day);
        }

        @Override
        public BirthdaySetC2SPacket decode(ByteBuf buf) {
            return new BirthdaySetC2SPacket(buf.readInt(), buf.readInt());
        }
    };

    public static BirthdaySetC2SPacket clear() {
        return new BirthdaySetC2SPacket(CLEAR, 0);
    }

    public static void handle(BirthdaySetC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.getServer() == null) {
                return;
            }
            BirthdayData data = BirthdayData.get(player.getServer().overworld());

            if (message.month <= CLEAR) {
                data.clear(player.getUUID());
                BirthdaySyncS2CPacket.sync(player, data, LocalDate.now());
                player.sendSystemMessage(Component.translatable("message.callresponse.birthday.cleared"));
                return;
            }

            if (!BirthdayData.isValid(message.month, message.day)) {
                player.sendSystemMessage(Component.translatable("message.callresponse.birthday.invalid"));
                return;
            }

            data.setBirthday(player.getUUID(), message.month, message.day);
            BirthdaySyncS2CPacket.sync(player, data, LocalDate.now());
            player.sendSystemMessage(Component.translatable("message.callresponse.birthday.set",
                    message.month, message.day));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
