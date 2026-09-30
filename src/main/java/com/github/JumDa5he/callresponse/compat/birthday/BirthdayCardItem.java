package com.github.JumDa5he.callresponse.compat.birthday;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.MonthDay;

/**
 * 生日贺卡：使用后打开设置界面，让玩家登记/修改自己的生日。
 * 数据保存在全局存档里，玩家看到的就是自己的隐藏配置。
 */
public final class BirthdayCardItem extends Item {
    public BirthdayCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && serverPlayer.getServer() != null) {
            BirthdayData data = BirthdayData.get(serverPlayer.getServer().overworld());
            MonthDay birthday = data.birthday(serverPlayer.getUUID());
            PacketDistributor.sendToPlayer(serverPlayer, new OpenBirthdayScreenS2CPacket(
                    birthday == null ? 0 : birthday.getMonthValue(),
                    birthday == null ? 0 : birthday.getDayOfMonth()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
