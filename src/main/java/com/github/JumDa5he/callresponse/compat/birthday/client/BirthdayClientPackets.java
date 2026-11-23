package com.github.JumDa5he.callresponse.compat.birthday.client;

import com.github.JumDa5he.callresponse.compat.birthday.BirthdaySyncS2CPacket;
import com.github.JumDa5he.callresponse.compat.birthday.OpenBirthdayScreenS2CPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only bodies stay outside common packet registration and server handling. */
@OnlyIn(Dist.CLIENT)
public final class BirthdayClientPackets {
    private BirthdayClientPackets() {}

    public static void sync(BirthdaySyncS2CPacket message) {
        BirthdayClientData.update(message.birthdayMonth(), message.birthdayDay(),
                message.todayYear(), message.todayMonth(), message.todayDay());
    }

    public static void open(OpenBirthdayScreenS2CPacket message) {
        BirthdayScreen.open(message.month(), message.day());
    }
}
