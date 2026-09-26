package com.github.JumDa5he.callresponse.compat.brain;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/** 溺爱女仆嫉妒关笼的正式长期 Brain Behavior。 */
public final class JealousyCageBehavior extends Behavior<EntityMaid> {
    public JealousyCageBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 20 * 60 * 3);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return JealousyCageManager.isRunning(maid) || JealousyCageManager.tryStart(level, maid);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return JealousyCageManager.isRunning(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        JealousyCageManager.tick(level, maid, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        JealousyCageManager.tick(level, maid, gameTime);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        JealousyCageManager.onBehaviorStopped(maid);
    }
}
