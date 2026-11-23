package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import org.spongepowered.asm.mixin.*;
import java.util.UUID;

/** Discard only stale external combat bookkeeping, never restore its saved task/path. */
@Pseudo
@Mixin(targets="cn.autoforged.maid_cute_activity.LonelinessHandler$LonelinessState", remap=false)
public abstract class CuteCampCombatStateMixin implements WanderingBehaviorAuthority.WildCombatState {
    @Shadow UUID wildMaidTargetUUID;
    @Shadow long wildMaidLastAttackTick;
    @Shadow IMaidTask wildMaidSavedTask;
    @Shadow UUID fightArenaTargetUUID;
    @Shadow long fightArenaLastAttackTick;
    @Override public void callresponse$discardCampCombat() {
        wildMaidTargetUUID = null;
        wildMaidLastAttackTick = -1L;
        wildMaidSavedTask = null;
        fightArenaTargetUUID = null;
        fightArenaLastAttackTick = -1L;
    }
}
