package com.github.JumDa5he.callresponse.compat.birthday;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.birthday.client.BirthdayScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 服务端下发当前生日并请求客户端打开设置界面；0/0 表示尚未设置。 */
public record OpenBirthdayScreenS2CPacket(int month, int day) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenBirthdayScreenS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    CallResponseMod.MOD_ID, "open_birthday_screen"));

    public static final StreamCodec<ByteBuf, OpenBirthdayScreenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, OpenBirthdayScreenS2CPacket pkt) {
            buf.writeInt(pkt.month);
            buf.writeInt(pkt.day);
        }

        @Override
        public OpenBirthdayScreenS2CPacket decode(ByteBuf buf) {
            return new OpenBirthdayScreenS2CPacket(buf.readInt(), buf.readInt());
        }
    };

    public static void handle(OpenBirthdayScreenS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> BirthdayScreen.open(message.month, message.day));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
