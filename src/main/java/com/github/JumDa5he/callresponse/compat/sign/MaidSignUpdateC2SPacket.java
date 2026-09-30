package com.github.JumDa5he.callresponse.compat.sign;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record MaidSignUpdateC2SPacket(int maidId, List<String> lines, DyeColor color, boolean clear) {
    public static void encode(MaidSignUpdateC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.maidId);
        buf.writeBoolean(msg.clear);
        buf.writeEnum(msg.color);
        buf.writeVarInt(msg.lines.size());
        for (String line : msg.lines) buf.writeUtf(line, 384);
    }

    public static MaidSignUpdateC2SPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean clear = buf.readBoolean();
        DyeColor color = buf.readEnum(DyeColor.class);
        int count = buf.readVarInt();
        if (count < 0 || count > MaidSignManager.MAX_LINES) throw new DecoderException("Invalid sign line count: " + count);
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) lines.add(buf.readUtf(384));
        return new MaidSignUpdateC2SPacket(id, List.copyOf(lines), color, clear);
    }

    public static void handle(MaidSignUpdateC2SPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null && player.serverLevel().getEntity(msg.maidId) instanceof EntityMaid maid)
                MaidSignManager.applyClientUpdate(player, maid, msg);
        });
        context.setPacketHandled(true);
    }
}
