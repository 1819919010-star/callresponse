package com.github.JumDa5he.callresponse.compat.birthday;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.birthday.client.BirthdayClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.function.Supplier;

/** Synchronizes only the recipient's birthday and the server's current real date. */
public record BirthdaySyncS2CPacket(int birthdayMonth, int birthdayDay,
                                  int todayYear, int todayMonth, int todayDay) {
    public static void encode(BirthdaySyncS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.birthdayMonth);
        buffer.writeInt(message.birthdayDay);
        buffer.writeInt(message.todayYear);
        buffer.writeInt(message.todayMonth);
        buffer.writeInt(message.todayDay);
    }

    public static BirthdaySyncS2CPacket decode(FriendlyByteBuf buffer) {
        return new BirthdaySyncS2CPacket(buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt());
    }

    public static void handle(BirthdaySyncS2CPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> BirthdayClientPackets.sync(message)));
        context.setPacketHandled(true);
    }

    public static void sync(ServerPlayer player, BirthdayData data, LocalDate today) {
        MonthDay birthday = data.birthday(player.getUUID());
        CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BirthdaySyncS2CPacket(
                birthday == null ? 0 : birthday.getMonthValue(), birthday == null ? 0 : birthday.getDayOfMonth(),
                today.getYear(), today.getMonthValue(), today.getDayOfMonth()));
    }
}
