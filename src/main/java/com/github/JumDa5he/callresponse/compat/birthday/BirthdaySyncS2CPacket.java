package com.github.JumDa5he.callresponse.compat.birthday;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.birthday.client.BirthdayClientData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * 把玩家自己的生日以及服务端当前日期同步给客户端，
 * 让客户端也能调用 {@code BirthdayClientData.isBirthdayToday()} 判断今天是不是自己的生日。
 *
 * <p>生日是隐藏配置，这里只会发给玩家本人。</p>
 */
public record BirthdaySyncS2CPacket(int birthdayMonth, int birthdayDay,
                                    int todayYear, int todayMonth, int todayDay) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BirthdaySyncS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    CallResponseMod.MOD_ID, "birthday_sync"));

    public static final StreamCodec<ByteBuf, BirthdaySyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, BirthdaySyncS2CPacket pkt) {
            buf.writeInt(pkt.birthdayMonth);
            buf.writeInt(pkt.birthdayDay);
            buf.writeInt(pkt.todayYear);
            buf.writeInt(pkt.todayMonth);
            buf.writeInt(pkt.todayDay);
        }

        @Override
        public BirthdaySyncS2CPacket decode(ByteBuf buf) {
            return new BirthdaySyncS2CPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    };

    public static void handle(BirthdaySyncS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> BirthdayClientData.update(
                message.birthdayMonth, message.birthdayDay,
                message.todayYear, message.todayMonth, message.todayDay));
    }

    /** 只把玩家本人的生日同步给他自己；未设置生日时月/日发 0。 */
    public static void sync(ServerPlayer player, BirthdayData data, LocalDate today) {
        MonthDay birthday = data.birthday(player.getUUID());
        PacketDistributor.sendToPlayer(player, new BirthdaySyncS2CPacket(
                birthday == null ? 0 : birthday.getMonthValue(),
                birthday == null ? 0 : birthday.getDayOfMonth(),
                today.getYear(), today.getMonthValue(), today.getDayOfMonth()));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
