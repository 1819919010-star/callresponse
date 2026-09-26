package com.github.JumDa5he.callresponse.compat.outpost;

import net.minecraft.world.entity.raid.Raid;

/** Raid membership retained only while Raider.die runs; vanilla clears it before LivingDeathEvent. */
public interface OutpostRaiderDeathContext {
    Raid callresponse$raidBeforeDeath();
}
