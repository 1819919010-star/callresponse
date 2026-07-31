package com.github.tartaricacid.callresponse.compat.gui;

import com.github.tartaricacid.callresponse.CallResponseMod;
import com.github.tartaricacid.callresponse.compat.emotion.SaddleLaunchHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DropMaidC2SPacket(float percent) implements CustomPacketPayload {
    public static final Type<DropMaidC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "drop_maid"));
    public static final StreamCodec<ByteBuf, DropMaidC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            DropMaidC2SPacket::percent,
            DropMaidC2SPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DropMaidC2SPacket packet, IPayloadContext context){
        if(packet.percent > 1 || packet.percent < 0)return;
        var player = context.player();
        if(!player.isShiftKeyDown())return;
        if(!(player.getFirstPassenger() instanceof EntityMaid maid) || !player.getMainHandItem().is(Items.SADDLE) && !player.getOffhandItem().is(Items.SADDLE))return;
        SaddleLaunchHandler.dropMaid(maid, player, packet.percent());
    }
}
