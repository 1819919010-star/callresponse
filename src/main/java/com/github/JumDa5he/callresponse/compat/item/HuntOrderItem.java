package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionDevotedManager;
import com.github.JumDa5he.callresponse.network.MaidListS2CPacket;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 狩猎令：右键打开狩猎终端，列出所有在线的死忠女仆，点选后管理该女仆的狩猎名单。
 */
public class HuntOrderItem extends Item {
    public HuntOrderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            List<MaidListS2CPacket.MaidInfo> maids = collectDevotedMaids(serverPlayer);
            PacketDistributor.sendToPlayer(serverPlayer, new MaidListS2CPacket(maids));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    private static List<MaidListS2CPacket.MaidInfo> collectDevotedMaids(ServerPlayer player) {
        List<MaidListS2CPacket.MaidInfo> list = new ArrayList<>();
        MinecraftServer server = player.getServer();
        if (server == null) return list;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof EntityMaid maid
                        && maid.getOwnerUUID() != null
                        && maid.getOwnerUUID().equals(player.getUUID())
                        && EmotionDevotedManager.isDevoted(maid, player)) {
                    // EntityMaid.getName() 会优先返回自定义名，否则返回当前模型名（如灵梦、酒狐）。
                    String displayName = maid.getName().getString();
                    list.add(new MaidListS2CPacket.MaidInfo(maid.getUUID(), maid.getType().getDescriptionId(), displayName));
                }
            }
        }
        return list;
    }
}
