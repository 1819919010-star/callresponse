package com.github.JumDa5he.callresponse.compat.state;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** 服务器主线程上的实体生命周期兜底。 */
public final class MaidMovementLifecycle {
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide) {
            MaidMovementControl.abortAll(maid, MaidMovementControl.AbortCause.DEATH);
        }
    }

    @SubscribeEvent
    public void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide) {
            if (maid.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
                return;
            }
            MaidMovementControl.abortAll(maid, MaidMovementControl.AbortCause.REMOVED);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        MaidMovementControl.abortTrackedOnServerStop();
    }
}
