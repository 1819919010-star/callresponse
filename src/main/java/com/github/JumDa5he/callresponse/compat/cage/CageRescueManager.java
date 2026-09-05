package com.github.JumDa5he.callresponse.compat.cage;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 三类自然结构共用的获救标记、一次性奖励和离开倒计时。 */
public final class CageRescueManager {
    private static final String SOURCE_TAG = "CallResponseCageRescueSource";
    private static final String CAGE_POS_TAG = "CallResponseCageRescueOriginalPos";
    private static final String DIMENSION_TAG = "CallResponseCageRescueDimension";
    private static final String RELEASED_TAG = "CallResponseCageRescueReleased";
    private static final String REWARDED_TAG = "CallResponseCageRescueRewarded";
    private static final String COMPLETED_TAG = "CallResponseCageRescueCompleted";
    private static final String DEADLINE_TAG = "CallResponseCageRescueDeadline";
    private static final long UNTAMED_LIFETIME = 20L * 60L * 3L;

    public CageRescueManager() {
    }

    /** 只能由自然结构生成入口调用，普通抓捕永远不会获得该标记。 */
    public static void markStructurePrisoner(EntityMaid maid, CageOrigin origin, BlockPos originalCage) {
        if (origin == CageOrigin.NORMAL || !(maid.level() instanceof ServerLevel level)) return;
        CompoundTag data = maid.getPersistentData();
        data.putString(SOURCE_TAG, origin.name());
        data.putLong(CAGE_POS_TAG, originalCage.asLong());
        data.putString(DIMENSION_TAG, level.dimension().location().toString());
        data.putBoolean(RELEASED_TAG, false);
        data.putBoolean(REWARDED_TAG, false);
        data.putBoolean(COMPLETED_TAG, false);
        data.remove(DEADLINE_TAG);
    }

    /** 只有从记录的原始结构笼中第一次释放，才会启动奖励与三分钟规则。 */
    public static void onReleasedFromCage(EntityMaid maid, BlockPos cagePos, CageOrigin cageOrigin) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        CompoundTag data = maid.getPersistentData();
        if (!isMarkedStructureMaid(data) || data.getBoolean(RELEASED_TAG)
                || cageOrigin == CageOrigin.NORMAL
                || !data.getString(SOURCE_TAG).equals(cageOrigin.name())
                || data.getLong(CAGE_POS_TAG) != cagePos.asLong()
                || !data.getString(DIMENSION_TAG).equals(level.dimension().location().toString())) {
            return;
        }

        data.putBoolean(RELEASED_TAG, true);
        if (!data.getBoolean(REWARDED_TAG)) {
            data.putBoolean(REWARDED_TAG, true);
            maid.getChatBubbleManager().addTextChatBubble("bubble.callresponse.cage.rescue.thanks");
            maid.spawnAtLocation(new ItemStack(ModItems.DISPOSABLE_FAVORABILITY_TOOL_ADD.get()));
        }
        if (maid.isTame()) {
            finishRescue(data);
        } else {
            data.putLong(DEADLINE_TAG, level.getGameTime() + UNTAMED_LIFETIME);
        }
    }

    @SubscribeEvent
    public void onMaidTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || !(maid.level() instanceof ServerLevel level)) return;
        CompoundTag data = maid.getPersistentData();
        if (!isMarkedStructureMaid(data) || !data.getBoolean(RELEASED_TAG)
                || data.getBoolean(COMPLETED_TAG)) return;
        if (maid.isTame()) {
            finishRescue(data);
            return;
        }
        long deadline = data.getLong(DEADLINE_TAG);
        if (deadline > 0L && level.getGameTime() >= deadline) {
            maid.discard();
        }
    }

    private static boolean isMarkedStructureMaid(CompoundTag data) {
        if (!data.contains(SOURCE_TAG) || !data.contains(CAGE_POS_TAG)
                || !data.contains(DIMENSION_TAG)) return false;
        return CageOrigin.byName(data.getString(SOURCE_TAG)) != CageOrigin.NORMAL;
    }

    private static void finishRescue(CompoundTag data) {
        data.putBoolean(COMPLETED_TAG, true);
        data.remove(DEADLINE_TAG);
    }
}
