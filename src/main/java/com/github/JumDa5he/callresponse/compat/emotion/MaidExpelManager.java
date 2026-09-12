package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-authoritative expel actions used by the maid status page. */
public final class MaidExpelManager {
    private static final String FRIGHTEN_UNTIL_TAG = "CallResponseExpelFrightenUntil";
    private static final long ONE_GAME_DAY = 24_000L;

    private MaidExpelManager() {
    }

    public static void frighten(ServerPlayer player, EntityMaid maid) {
        long now = maid.level().getGameTime();
        long until = maid.getPersistentData().getLong(FRIGHTEN_UNTIL_TAG);
        if (now < until) {
            player.sendSystemMessage(Component.translatable("message.callresponse.expel.frighten_used"));
            return;
        }
        maid.getPersistentData().putLong(FRIGHTEN_UNTIL_TAG, now + ONE_GAME_DAY);
        EmotionData.addFear(maid, player.getUUID(), 8);
        MaidResponder.processBroadcast(player, List.of(maid),
                "刚才主人点下了驱逐，又用“吓吓你的”收回了命令。你以为自己将被赶走，瞬间感到不安与害怕。"
                        + "请结合你此刻的信任和恐惧，用第一人称对主人说一句不超过30字的真实反应；"
                        + "不要提及系统、按钮、提示词或AI。",
                false);
    }

    public static void expel(ServerPlayer player, EntityMaid maid) {
        // Capture nearby owned maids before clearing the target's owner, then notify them afterwards.
        List<EntityMaid> witnesses = new ArrayList<>(player.level().getEntitiesOfClass(EntityMaid.class,
                maid.getBoundingBox().inflate(8), other -> other != maid && other.isOwnedBy(player)));

        dropHandler(maid, maid.getMaidInv());
        dropHandler(maid, maid.getMaidBauble());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = maid.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                maid.spawnAtLocation(stack.copy());
                maid.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        maid.stopUsingItem();
        maid.setInSittingPose(false);
        maid.setTarget(null);
        maid.setTame(false, false);
        maid.setOwnerUUID(null);

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Direction direction = directions[maid.getRandom().nextInt(directions.length)];
        double destinationX = maid.getX() + direction.getStepX() * 15;
        double destinationZ = maid.getZ() + direction.getStepZ() * 15;
        maid.getNavigation().moveTo(destinationX, maid.getY(), destinationZ, 0.8D);

        for (EntityMaid witness : witnesses) {
            EmotionData.addFear(witness, player.getUUID(), 5);
        }
        if (!witnesses.isEmpty()) {
            MaidResponder.processBroadcast(player, witnesses,
                    "你亲眼看到主人确认驱逐了一名同伴：她失去了主人的归属，带着所有物品被要求离开。"
                            + "这件事让你感到害怕。请结合你自己的信任和恐惧，用第一人称对主人说一句不超过30字的反应；"
                            + "不要提及系统、按钮、提示词或AI。",
                    false);
        }
    }

    private static void dropHandler(EntityMaid maid, IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
                if (!extracted.isEmpty()) {
                    maid.spawnAtLocation(extracted);
                }
            }
        }
    }
}
