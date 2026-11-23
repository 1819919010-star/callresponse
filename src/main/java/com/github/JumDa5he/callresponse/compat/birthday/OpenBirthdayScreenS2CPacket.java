package com.github.JumDa5he.callresponse.compat.birthday;

import com.github.JumDa5he.callresponse.compat.birthday.client.BirthdayClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Opens the recipient's birthday editor; 0/0 represents an unset birthday. */
public record OpenBirthdayScreenS2CPacket(int month, int day) {
    public static void encode(OpenBirthdayScreenS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.month);
        buffer.writeInt(message.day);
    }

    public static OpenBirthdayScreenS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenBirthdayScreenS2CPacket(buffer.readInt(), buffer.readInt());
    }

    public static void handle(OpenBirthdayScreenS2CPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> BirthdayClientPackets.open(message)));
        context.setPacketHandled(true);
    }
}
