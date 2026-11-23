package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import java.util.List;
import java.util.Map;

/** Driven exclusively by the native Brain tick, including its GUI pause. */
public final class CampRoutineBehavior extends Behavior<EntityMaid> {
    public static final IExtraMaidBrain EXTENSION = new IExtraMaidBrain() {
        @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
            return List.of(Pair.of(1, new CampRoutineBehavior()));
        }
    };
    public CampRoutineBehavior() { super(Map.of()); }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) { return CampPolicy.runsRoutine(maid); }
    @Override protected boolean canStillUse(ServerLevel level, EntityMaid maid, long time) { return CampPolicy.runsRoutine(maid); }
    @Override protected boolean timedOut(long time) { return false; }
    @Override protected void tick(ServerLevel level, EntityMaid maid, long time) { WanderingCampBrain.tick(maid); }
    @Override protected void stop(ServerLevel level, EntityMaid maid, long time) { WanderingCampBrain.release(maid, true); }
}
