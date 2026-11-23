package com.github.JumDa5he.callresponse.compat.birthday;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.time.LocalDate;
import java.util.function.Supplier;

/** Players may only edit their own birthday; dates are validated again on the server. */
public record BirthdaySetC2SPacket(int month, int day) {
    public static final int CLEAR = 0;

    public static BirthdaySetC2SPacket clear() {
        return new BirthdaySetC2SPacket(CLEAR, 0);
    }

    public static void encode(BirthdaySetC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.month);
        buffer.writeInt(message.day);
    }

    public static BirthdaySetC2SPacket decode(FriendlyByteBuf buffer) {
        return new BirthdaySetC2SPacket(buffer.readInt(), buffer.readInt());
    }

    public static void handle(BirthdaySetC2SPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.getServer() == null) return;
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
            player.sendSystemMessage(Component.translatable("message.callresponse.birthday.set", message.month, message.day));
        });
        context.setPacketHandled(true);
    }
}
