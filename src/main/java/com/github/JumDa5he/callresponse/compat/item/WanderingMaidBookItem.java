package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.gui.OpenWanderingSkinPoolS2CPacket;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

public final class WanderingMaidBookItem extends Item {
    public WanderingMaidBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
            WanderingMaidSavedData data = WanderingMaidSavedData.get(serverPlayer.getServer().overworld());
            CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new OpenWanderingSkinPoolS2CPacket(data.skinPool(serverPlayer.getUUID())));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
