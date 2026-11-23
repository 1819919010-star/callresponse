package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Death receipts are committed only by the maid's confirmed native death return. */
public final class WineFoxGrowth {
    public static void onMaidDeath(EntityMaid maid, UUID leader, double maxHealth, double attack) {
        if (leader == null || !(maid.level() instanceof ServerLevel level)
                || !WineFoxFaction.isRevenge(maid) || WineFoxSurrender.isSurrendered(maid)) return;
        WineFoxLeaders ledger = WineFoxLeaders.get(level);
        if (!ledger.contribute(leader, maid.getUUID(), maxHealth, attack)) return;
        for (ServerLevel loaded : level.getServer().getAllLevels()) {
            Entity entity = loaded.getEntity(leader);
            if (entity instanceof WineFoxEntity fox) {
                apply(fox);
                break;
            }
        }
    }

    public static void apply(WineFoxEntity fox) {
        if (!(fox.level() instanceof ServerLevel level) || !fox.isAlive() || fox.isRemoved()
                || fox.isDeathConfirmed()) return;
        WineFoxLeaders ledger = WineFoxLeaders.get(level);
        if (!ledger.isDead(fox.getUUID())) fox.applyInheritance(ledger.totals(fox.getUUID()));
    }

    /** No gameplay growth cap: the only ceiling is Minecraft's finite float health/damage format. */
    public static double nonnegative(double value) {
        return Double.isFinite(value) && value > 0 ? Math.min(Float.MAX_VALUE, value) : 0;
    }
    public static double add(double first, double second) {
        return Math.min(Float.MAX_VALUE, nonnegative(first) + nonnegative(second));
    }
    public static float damage(double value) { return (float) nonnegative(value); }
    private WineFoxGrowth() {}
}
