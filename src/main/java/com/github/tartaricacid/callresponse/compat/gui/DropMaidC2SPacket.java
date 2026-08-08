package com.github.tartaricacid.callresponse.compat.gui;

import com.github.tartaricacid.callresponse.compat.emotion.SaddleLaunchHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DropMaidC2SPacket {
    private final float percent;

    public DropMaidC2SPacket(float percent) {
        this.percent = percent;
    }

    public DropMaidC2SPacket(FriendlyByteBuf buf) {
        this.percent = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(percent);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (percent > 1 || percent < 0) return;
            var player = ctx.get().getSender();
            if (player == null) return;
            if (!player.isShiftKeyDown()) return;
            if (!(player.getFirstPassenger() instanceof EntityMaid maid) || !player.getMainHandItem().is(Items.SADDLE) && !player.getOffhandItem().is(Items.SADDLE)) return;
            SaddleLaunchHandler.dropMaid(maid, player, percent);
        });
        ctx.get().setPacketHandled(true);
    }
}
