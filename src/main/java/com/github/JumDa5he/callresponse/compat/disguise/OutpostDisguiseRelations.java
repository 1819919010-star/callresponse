package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostSavedData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;

import java.util.UUID;

/** Only the relationship between a disguised owner/nearby owned maids and one revenge camp. */
public final class OutpostDisguiseRelations {
    private static final String EXPOSURES = "CallResponseOutpostExposures";
    private static final int LEAVE_COOLDOWN = 20 * 60 * 5;

    public static boolean isPacifiedTarget(EntityMaid revengeMaid, LivingEntity target) {
        if (!BetrayalOutpostMaidData.isOutpostMaid(revengeMaid)
                || !(revengeMaid.level() instanceof ServerLevel level)) return false;
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level)
                .findByKey(level, BetrayalOutpostMaidData.group(revengeMaid));
        if (camp == null || !nearCamp(camp, target, 16)) return false;
        ServerPlayer owner = ownerOf(level, target);
        return owner != null && DisguiseManager.isActive(owner)
                && nearCamp(camp, owner, 24)
                && (DisguiseManager.isRecognized(owner) || !isExposed(owner, camp));
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

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid victim)
                || !BetrayalOutpostMaidData.isOutpostMaid(victim)
                || !(victim.level() instanceof ServerLevel level) || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (attacker == null && source.getDirectEntity() instanceof Projectile projectile) {
            attacker = projectile.getOwner();
        }
        ServerPlayer owner = attacker == null ? null : ownerOf(level, attacker);
        if (owner == null || !DisguiseManager.isActive(owner)) return;
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level)
                .findByKey(level, BetrayalOutpostMaidData.group(victim));
        if (camp != null) expose(owner, camp);
    }

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || event.getNewAboutToBeSetTarget() == null) return;
        if (BetrayalOutpostMaidData.isOutpostMaid(maid)
                && isPacifiedTarget(maid, event.getNewAboutToBeSetTarget())) {
            event.setNewAboutToBeSetTarget(null);
        } else if (event.getNewAboutToBeSetTarget() instanceof EntityMaid revenge
                && isPacifiedTarget(revenge, maid)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** A stale TLM Brain target or an already-fired projectile must not hurt a pacified player. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPacifiedAttack(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null && event.getSource().getDirectEntity() instanceof Projectile projectile) {
            attacker = projectile.getOwner();
        }
        if (attacker instanceof EntityMaid revenge && isPacifiedTarget(revenge, event.getEntity())) {
            event.setCanceled(true);
            BetrayalOutpostAlertManager.clearCombat(revenge);
        }
    }

    @SubscribeEvent
    public void onMaidTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide) return;
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid) && maid.tickCount % 10 != 0) return;
        LivingEntity target = maid.getTarget();
        if (target == null) target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) return;
        boolean pacified = BetrayalOutpostMaidData.isOutpostMaid(maid)
                ? isPacifiedTarget(maid, target)
                : target instanceof EntityMaid revenge && isPacifiedTarget(revenge, maid);
        if (!pacified) return;
        clearPacifiedTarget(maid);
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
            if (target == null) target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (target == null) continue;
            if (BetrayalOutpostMaidData.isOutpostMaid(maid)
                    ? isPacifiedTarget(maid, target)
                    : target instanceof EntityMaid revenge && isPacifiedTarget(revenge, maid)) {
                clearPacifiedTarget(maid);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
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
