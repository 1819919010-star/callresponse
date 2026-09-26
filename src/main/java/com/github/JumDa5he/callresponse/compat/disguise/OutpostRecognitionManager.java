package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostSavedData;
import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaidMarker;
import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaiderDeathContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Credits actual kills in a marked camp raid, then waits for vanilla's victory outcome. */
public final class OutpostRecognitionManager {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRaiderDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Raider raider)
                || !(raider.level() instanceof ServerLevel level)) return;
        // Raider.die removes itself from the raid before super.die posts LivingDeathEvent.
        Raid raid = ((OutpostRaiderDeathContext) raider).callresponse$raidBeforeDeath();
        if (raid == null || !((OutpostRaidMarker) raid).callresponse$isOutpostRaid()) return;
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level).findAt(level, raid.getCenter());
        if (camp == null) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null && event.getSource().getDirectEntity() instanceof Projectile projectile) {
            attacker = projectile.getOwner();
        }
        ServerPlayer owner = attacker == null ? null : OutpostDisguiseRelations.ownerOf(level, attacker);
        if (owner == null) return;
        OutpostRaidParticipationSavedData.get(level).participate(level, raid.getId(), camp.key(), owner.getUUID());
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % 20 != 0) return;
        OutpostRaidParticipationSavedData data = OutpostRaidParticipationSavedData.get(level);
        for (OutpostRaidParticipationSavedData.RaidEntry contribution : data.pending(level)) {
            Raid raid = level.getRaids().get(contribution.raidId());
            if (raid == null) {
                data.close(contribution);
                continue;
            }
            if (!raid.isVictory()) {
                if (raid.isStopped() || raid.isLoss()) data.close(contribution);
                continue;
            }
            BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level)
                    .findByKey(level, contribution.campKey());
            if (camp != null && ((OutpostRaidMarker) raid).callresponse$isOutpostRaid()
                    && raid.getCenter().equals(camp.center())) {
                for (java.util.UUID playerId : contribution.participants()) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) DisguiseManager.recognize(player);
                }
            }
            data.close(contribution);
        }
    }
}
