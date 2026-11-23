package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.UUID;

/** Permanent, entity-owned surrender. Cage progress is independent of time of day and chunk absence. */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class WineFoxSurrender {
    public static final String ROOT = "CallResponseWineFoxSurrender";
    public static final int REHABILITATION_TICKS = 12000;
    private static final java.util.Map<EntityMaid, Long> LAST_CAGE_TICK = new java.util.WeakHashMap<>();

    public interface State {
        int callresponse$surrenderTicks();
        void callresponse$setSurrenderTicks(int value);
    }

    public static boolean isSurrendered(EntityMaid maid) {
        return ((State) maid).callresponse$surrenderTicks() >= 0
                || !maid.level().isClientSide && BetrayalOutpostMaidData.isOutpostMaid(maid)
                && maid.getPersistentData().getCompound(ROOT).getBoolean("Surrendered");
    }

    public static int getAnimationTicks(EntityMaid maid) {
        return Math.max(0, Math.min(20, ((State) maid).callresponse$surrenderTicks()));
    }

    public static void surrender(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !maid.isAlive()
                || !BetrayalOutpostMaidData.isOutpostMaid(maid)) return;
        if (isSurrendered(maid)) { enforce(maid); return; }
        CompoundTag state = new CompoundTag();
        state.putBoolean("Surrendered", true);
        state.putInt("AnimationTicks", 0);
        state.putInt("SpeechDelay", maid.getRandom().nextInt(21));
        maid.getPersistentData().put(ROOT, state);
        ((State) maid).callresponse$setSurrenderTicks(0);
        if (maid.isSleeping()) maid.stopSleeping();
        maid.stopRiding();
        maid.getBrain().stopAll(level, maid);
        EmotionBetrayalManager.cancelOutpostReturn(maid);
        enforce(maid);
    }

    public static void restore(EntityMaid maid) {
        CompoundTag state = maid.getPersistentData().getCompound(ROOT);
        if (state.getBoolean("Surrendered") && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            int ticks = Math.max(0, Math.min(20, state.getInt("AnimationTicks")));
            ((State) maid).callresponse$setSurrenderTicks(ticks);
            if (!maid.level().isClientSide && maid.isAlive()) enforce(maid);
        } else ((State) maid).callresponse$setSurrenderTicks(-1);
    }

    public static void enforce(EntityMaid maid) {
        if (maid.level().isClientSide || !isSurrendered(maid) || !maid.isAlive()) return;
        maid.setNoAi(true);
        maid.setTarget(null);
        maid.setLastHurtByMob(null);
        maid.setAggressive(false);
        maid.stopUsingItem();
        if (maid.isSleeping()) maid.stopSleeping();
        maid.setInSittingPose(false);
        maid.setJumping(false);
        maid.getNavigation().stop();
        maid.getMoveControl().setWantedPosition(maid.getX(), maid.getY(), maid.getZ(), 0);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
        maid.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (!MaidMovementControl.isActive(maid, MaidMovementControl.Reason.WINEFOX_SURRENDER))
            MaidMovementControl.begin(maid, MaidMovementControl.Reason.WINEFOX_SURRENDER,
                    EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.TASK, MaidMovementControl.Field.POSE));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide
                || !isSurrendered(maid) || !maid.isAlive()) return;
        enforce(maid);
        CompoundTag state = maid.getPersistentData().getCompound(ROOT);
        int ticks = getAnimationTicks(maid);
        if (ticks < 20) {
            ((State) maid).callresponse$setSurrenderTicks(ticks + 1);
            state.putInt("AnimationTicks", ticks + 1);
        }
        if (!state.getBoolean("Spoken")) {
            int delay = Math.max(0, Math.min(20, state.getInt("SpeechDelay")));
            if (delay > 0) state.putInt("SpeechDelay", delay - 1);
            else {
                state.putBoolean("Spoken", true);
                maid.getChatBubbleManager().addTextChatBubble("bubble.callresponse.winefox.surrender");
            }
        }
    }

    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof EntityMaid maid) restore(maid);
    }

    public static int cageTicks(EntityMaid maid) {
        return Math.max(0, Math.min(REHABILITATION_TICKS, maid.getPersistentData().getCompound(ROOT).getInt("CageTicks")));
    }
    public static UUID captor(EntityMaid maid) {
        CompoundTag state = maid.getPersistentData().getCompound(ROOT);
        UUID value = state.hasUUID("Captor") ? state.getUUID("Captor") : null;
        return value != null && (value.getMostSignificantBits() != 0 || value.getLeastSignificantBits() != 0) ? value : null;
    }
    public static void recordCaptor(EntityMaid maid, UUID captor) {
        if (captor != null && (captor.getMostSignificantBits() != 0 || captor.getLeastSignificantBits() != 0)
                && isSurrendered(maid) && captor(maid) == null)
            maid.getPersistentData().getCompound(ROOT).putUUID("Captor", captor);
    }
    /** Called only by the cage that currently contains this exact UUID. */
    public static boolean tickInCage(EntityMaid maid, UUID cageCaptor) {
        if (!isSurrendered(maid) || !maid.isAlive()) return false;
        recordCaptor(maid, cageCaptor);
        long tick = maid.level().getGameTime();
        Long previous = LAST_CAGE_TICK.put(maid, tick);
        if (previous != null && previous == tick)
            return cageTicks(maid) == REHABILITATION_TICKS && captor(maid) != null;
        int ticks = Math.min(REHABILITATION_TICKS, cageTicks(maid) + 1);
        maid.getPersistentData().getCompound(ROOT).putInt("CageTicks", ticks);
        return ticks == REHABILITATION_TICKS && captor(maid) != null;
    }
    private WineFoxSurrender() {}
}
