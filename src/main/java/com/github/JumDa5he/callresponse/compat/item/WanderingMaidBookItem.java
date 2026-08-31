package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.network.OpenWanderingSkinPoolS2CPacket;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WanderingMaidBookItem extends Item {
    public WanderingMaidBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && serverPlayer.level().getServer() != null) {
            WanderingMaidSavedData data = WanderingMaidSavedData.get(serverPlayer.level().getServer().overworld());
            PacketDistributor.sendToPlayer(serverPlayer,
                    new OpenWanderingSkinPoolS2CPacket(data.skinPool(serverPlayer.getUUID())));
        }
        return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
    }
}
