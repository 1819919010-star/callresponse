package com.github.tartaricacid.callresponse.compat.hunt;

import com.github.tartaricacid.callresponse.compat.gui.CopyEntityUuidS2CPacket;
import com.github.tartaricacid.callresponse.CallResponseMod;
import com.github.tartaricacid.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * 狩猎令物品交互：
 * - 右键狩猎令本身：打开狩猎终端（见 HuntOrderItem）
 * - shift+右键任意生物/玩家：复制其 UUID 到剪贴板（玩家在名单输入框粘贴添加）
 * 名单数据存在女仆 NBT。
 */
public class HuntOrderInteractListener {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.HUNT_ORDER.get())) return;

        Entity target = event.getTarget();

        // shift + 右键：复制目标 UUID 到剪贴板
        if (player.isShiftKeyDown()) {
            event.setCanceled(true);
            CallResponseMod.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new CopyEntityUuidS2CPacket(target.getUUID().toString(), target.getName().getString()));
        }
    }

    public static List<HuntOrderEntry> buildEntryPayload(EntityMaid maid) {
        List<HuntOrderEntry> entries = HuntOrderData.getEntries(maid);
        if (maid.getServer() != null) {
            for (HuntOrderEntry entry : entries) {
                Entity entity = HuntOrderManager.resolveEntity(maid.getServer(), entry.uuid);
                if (entity != null) {
                    entry.name = entity.getName().getString();
                    entry.isPlayer = entity instanceof Player;
                }
            }
        }
        return entries;
    }
}