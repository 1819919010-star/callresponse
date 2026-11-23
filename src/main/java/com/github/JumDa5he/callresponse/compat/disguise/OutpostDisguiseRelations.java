package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostSavedData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxFaction;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.UUID;

/** Revenge identity owns disguise recognition; a structure only adds its existing camp alarm. */
public final class OutpostDisguiseRelations {
    private static final String EXPOSURES = "CallResponseOutpostExposures";
    private static final String PROVOCATIONS = "CallResponseRevengeProvocations";
    private static final int LEAVE_COOLDOWN = 20 * 60 * 5;

    public static boolean isPacifiedTarget(EntityMaid revengeMaid, LivingEntity target) {
        if (!BetrayalOutpostMaidData.isOutpostMaid(revengeMaid)
                || target == null || target.level() != revengeMaid.level()
                || !(revengeMaid.level() instanceof ServerLevel level)) return false;
        ServerPlayer owner = ownerOf(level, target);
        if (owner == null || owner.level() != level || !DisguiseManager.isActive(owner)
                || isProvokedBy(revengeMaid, owner.getUUID())) return false;
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level)
                .findByKey(level, BetrayalOutpostMaidData.group(revengeMaid));
        if (camp != null && !DisguiseManager.isRecognized(owner) && isExposed(owner, camp)) return false;
        // Players are recognized by their actual disguise even without a structure or anchor.
        if (target == owner) return true;
        // Keep the camp's existing protection area for owned maids; standalone revenge maids
        // protect an actually nearby disguised owner and her maid, not a remote/offline owner.
        return camp != null ? nearCamp(camp, target, 16) && nearCamp(camp, owner, 24)
                : revengeMaid.distanceToSqr(owner) <= 24 * 24 && target.distanceToSqr(owner) <= 24 * 24;
    }

    public static boolean isProvokedBy(EntityMaid maid, UUID player) {
        ListTag entries = maid.getPersistentData().getList(PROVOCATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Player") && player.equals(entry.getUUID("Player"))
                    && entry.getInt("Remaining") > 0) return true;
        }
        return false;
    }

    private static void provoke(EntityMaid maid, ServerPlayer player) {
        ListTag entries = maid.getPersistentData().getList(PROVOCATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Player") && player.getUUID().equals(entry.getUUID("Player"))) {
                entry.putInt("Remaining", LEAVE_COOLDOWN);
                maid.getPersistentData().put(PROVOCATIONS, entries);
                return;
            }
        }
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Player", player.getUUID());
        entry.putInt("Remaining", LEAVE_COOLDOWN);
        entries.add(entry);
        maid.getPersistentData().put(PROVOCATIONS, entries);
    }

    private static void tickProvocations(EntityMaid maid) {
        ListTag entries = maid.getPersistentData().getList(PROVOCATIONS, Tag.TAG_COMPOUND);
        if (entries.isEmpty()) return;
        ListTag remaining = new ListTag();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int ticks = Math.min(LEAVE_COOLDOWN, entry.getInt("Remaining")) - 1;
            if (ticks <= 0 || !entry.hasUUID("Player")) continue;
            entry.putInt("Remaining", ticks);
            remaining.add(entry);
        }
        if (remaining.isEmpty()) maid.getPersistentData().remove(PROVOCATIONS);
        else maid.getPersistentData().put(PROVOCATIONS, remaining);
    }

    public static ServerPlayer ownerOf(ServerLevel level, Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof EntityMaid maid && maid.isTame() && maid.getOwnerUUID() != null) {
            return level.getServer().getPlayerList().getPlayer(maid.getOwnerUUID());
        }
        return null;
    }

    private static boolean nearCamp(BetrayalOutpostSavedData.Outpost camp, Entity entity, int margin) {
        BoundingBox box = camp.box();
        return entity.getX() >= box.minX() - margin && entity.getX() <= box.maxX() + 1 + margin
                && entity.getZ() >= box.minZ() - margin && entity.getZ() <= box.maxZ() + 1 + margin
                && entity.getY() >= box.minY() - 16 && entity.getY() <= box.maxY() + 16;
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean isExposed(ServerPlayer player, BetrayalOutpostSavedData.Outpost camp) {
        ListTag list = persisted(player).getList(EXPOSURES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (camp.key().equals(entry.getString("Camp"))
                    && camp.dimension().equals(entry.getString("Dimension"))) return true;
        }
        return false;
    }

    public static void expose(ServerPlayer player, BetrayalOutpostSavedData.Outpost camp) {
        if (!isExposed(player, camp)) {
            CompoundTag persisted = persisted(player);
            ListTag list = persisted.getList(EXPOSURES, Tag.TAG_COMPOUND);
            CompoundTag entry = new CompoundTag();
            entry.putString("Camp", camp.key());
            entry.putString("Dimension", camp.dimension());
            entry.putInt("AwayTicks", 0);
            list.add(entry);
            persisted.put(EXPOSURES, list);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        }
        DisguiseManager.revokeRecognition(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid victim)
                || !BetrayalOutpostMaidData.isOutpostMaid(victim)
                || !(victim.level() instanceof ServerLevel level) || event.isCanceled() || event.getAmount() <= 0) return;
        Entity attacker = WineFoxFaction.resolveAttacker(event.getSource());
        if (WineFoxFaction.blocksAttack(attacker, victim)) return;
        ServerPlayer owner = attacker == null ? null : ownerOf(level, attacker);
        // Record the actual related player even if she disguises only AFTER attacking.
        // Final positive damage is after armor, resistance and absorption; canceled hits do not arrive here.
        if (owner == null) return;
        provoke(victim, owner);
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level)
                .findByKey(level, BetrayalOutpostMaidData.group(victim));
        if (camp != null) expose(owner, camp);
        else DisguiseManager.revokeRecognition(owner);
        BetrayalOutpostAlertManager.onHurt(victim, attacker instanceof LivingEntity living ? living : null);
    }

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || event.getNewTarget() == null) return;
        if (WineFoxFaction.blocksAttack(maid, event.getNewTarget())) {
            event.setNewTarget(null);
        } else if (BetrayalOutpostMaidData.isOutpostMaid(maid)
                && isPacifiedTarget(maid, event.getNewTarget())) {
            event.setNewTarget(null);
        } else if (event.getNewTarget() instanceof EntityMaid revenge
                && isPacifiedTarget(revenge, maid)) {
            event.setNewTarget(null);
        }
    }

    /** A stale TLM Brain target or an already-fired projectile must not hurt a pacified player. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPacifiedAttack(LivingAttackEvent event) {
        Entity attacker = WineFoxFaction.resolveAttacker(event.getSource());
        if (attacker instanceof EntityMaid revenge && isPacifiedTarget(revenge, event.getEntity())) {
            event.setCanceled(true);
            BetrayalOutpostAlertManager.clearCombat(revenge);
        }
    }

    @SubscribeEvent
    public void onMaidTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide) return;
        if (BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            tickProvocations(maid);
            BetrayalOutpostAlertManager.clearFriendlyTargets(maid);
        }
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid) && maid.tickCount % 10 != 0) return;
        LivingEntity target = maid.getTarget();
        LivingEntity memory = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (isPacifiedRelationship(maid, target) || isPacifiedRelationship(maid, memory)) clearPacifiedTarget(maid);
    }

    private static boolean isPacifiedRelationship(EntityMaid maid, LivingEntity target) {
        return target != null && (BetrayalOutpostMaidData.isOutpostMaid(maid)
                ? isPacifiedTarget(maid, target)
                : target instanceof EntityMaid revenge && isPacifiedTarget(revenge, maid));
    }

    private static void clearPacifiedTarget(EntityMaid maid) {
        if (BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            BetrayalOutpostAlertManager.clearCombat(maid);
            return;
        }
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getNavigation().stop();
    }

    public static void clearOldTargetsNear(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        AABB area = owner.getBoundingBox().inflate(48.0D, 24.0D, 48.0D);
        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class, area)) {
            LivingEntity target = maid.getTarget();
            LivingEntity memory = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (isPacifiedRelationship(maid, target) || isPacifiedRelationship(maid, memory)) {
                clearPacifiedTarget(maid);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        CompoundTag persisted = persisted(player);
        ListTag list = persisted.getList(EXPOSURES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return;
        ListTag updated = new ListTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i).copy();
            BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(player.serverLevel())
                    .findByKey(player.serverLevel(), entry.getString("Camp"));
            // A different dimension counts as leaving without loading that dimension's chunks.
            boolean nearby = camp != null && nearCamp(camp, player, 48);
            int away = nearby ? 0 : entry.getInt("AwayTicks") + 20;
            if (away >= LEAVE_COOLDOWN) continue;
            entry.putInt("AwayTicks", away);
            updated.add(entry);
        }
        persisted.put(EXPOSURES, updated);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag old = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!old.contains(EXPOSURES, Tag.TAG_LIST)) return;
        CompoundTag target = event.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        target.put(EXPOSURES, old.getList(EXPOSURES, Tag.TAG_COMPOUND).copy());
        event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, target);
    }
}
