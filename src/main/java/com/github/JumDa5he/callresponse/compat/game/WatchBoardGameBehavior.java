package com.github.JumDa5he.callresponse.compat.game;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/**
 * 低优先级正式 Brain Behavior：只在符合条件时申请围观位，移动由 WALK_TARGET 正常执行。
 */
public final class WatchBoardGameBehavior extends Behavior<EntityMaid> {
    public WatchBoardGameBehavior() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), Integer.MAX_VALUE);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (BoardGameManager.isSpectating(maid)) return true;
        // 未围观女仆每秒才查询一次，并按实体 id 错开，避免大量女仆在同一 tick 集中检查。
        if ((maid.tickCount + maid.getId()) % 20 != 0) return false;
        return BoardGameManager.tryStartSpectating(level, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        BoardGameManager.tickSpectator(maid);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        BoardGameManager.tickSpectator(maid);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return BoardGameManager.isSpectating(maid);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        BoardGameManager.releaseSpectator(maid);
    }
}
