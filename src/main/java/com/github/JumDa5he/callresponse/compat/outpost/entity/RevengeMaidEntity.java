package com.github.JumDa5he.callresponse.compat.outpost.entity;

import com.github.JumDa5he.callresponse.compat.outpost.*;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.*;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import javax.annotation.Nullable;
import java.util.UUID;

/** Real TLM subtype. Only behavior ownership changes; physics, inventory and death stay inherited. */
// Vanilla golems and TLM's default attack classification recognize Enemy, not MobCategory alone.
public class RevengeMaidEntity extends EntityMaid implements net.minecraft.world.entity.monster.Enemy {
    public static final String ALLIED_EGG = "CallResponseAlliedRevengeEgg";
    private static final String INITIALIZED = "CallResponseRevengeInitialized";
    private static EntityDataAccessor<String> roleKey;
    private boolean suspendedBrain;
    final RevengeMaidSafety safety = new RevengeMaidSafety();
    private BetrayalOutpostSavedData.Outpost cachedCamp;
    private String cachedCampGroup;
    private long nextCampLookup;

    @Override public void aiStep() {
        restoreInterruptedHandUse();
        super.aiStep();
        restoreInterruptedHandUse();
    }

    private void restoreInterruptedHandUse() {
        // TLM restores this inventory on completeUsingItem, but not on cancellation.
        // Never mint a shield: restore only the real temporarily stored item.
        if (!level().isClientSide && isAlive() && !isUsingItem() && !getHideInv().getStackInSlot(0).isEmpty()) {
            ((com.github.JumDa5he.callresponse.mixin.accessor.EntityMaidHandItemInvoker) this)
                    .callresponse$restoreTemporaryHandItem();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RevengeMaidEntity(EntityType<? extends RevengeMaidEntity> type, Level level) {
        super((EntityType<EntityMaid>) (EntityType) type, level);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, -1);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA, -1);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_OTHER, -1);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE, 16);
    }

    // Vanilla otherwise increases this with health while pursuing a target.
    // Keep stairs/ladder navigation, but never authorize a balcony drop because of a large health pool.
    @Override public int getMaxFallDistance() { return 2; }

    @Override public float getPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes type) {
        float cost = super.getPathfindingMalus(type);
        return safety != null ? safety.escapePathCost(type, cost) : cost;
    }

    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && safety != null) safety.onHurt(this, source);
        return hurt;
    }

    /** Routine/return anchor only; the saved combat boundary and raid center remain unchanged. */
    public BlockPos getCampActivityAnchor() {
        BlockPos home = BetrayalOutpostMaidData.home(this);
        var camp = campRecord();
        if (camp != null) return RevengeMaidSafety.entrance(camp.box(), home);
        return home; // Standalone eggs and older/unrecognized templates retain their own anchor.
    }

    public boolean isSafeCampStandingPosition(BlockPos pos) { return RevengeMaidSafety.safeStanding(this, pos); }

    @Nullable private BetrayalOutpostSavedData.Outpost campRecord() {
        if (!(level() instanceof ServerLevel level)) return null;
        String group = BetrayalOutpostMaidData.group(this);
        if (!java.util.Objects.equals(group, cachedCampGroup)) {
            cachedCampGroup = group;
            cachedCamp = null;
            nextCampLookup = 0;
        }
        // Restriction is queried for every path node. Do not query SavedData for every node.
        if (cachedCamp == null && level.getGameTime() >= nextCampLookup) {
            cachedCamp = BetrayalOutpostSavedData.get(level).findByKey(level, group);
            nextCampLookup = level.getGameTime() + 100;
        }
        return cachedCamp;
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        // Parent mixins allocate some EntityMaid IDs lazily inside super.defineSynchedData.
        // Allocate the child ID only AFTER all parent injections have completed, on either side.
        entityData.define(roleKey(), BetrayalOutpostMaidData.Role.SWORDSMAN.name());
    }
    private static synchronized EntityDataAccessor<String> roleKey() {
        if (roleKey == null) roleKey = SynchedEntityData.defineId(RevengeMaidEntity.class, EntityDataSerializers.STRING);
        return roleKey;
    }
    public void syncRole(BetrayalOutpostMaidData.Role role) { entityData.set(roleKey(), role.name()); }
    public BetrayalOutpostMaidData.Role clientRole() {
        try { return BetrayalOutpostMaidData.Role.valueOf(entityData.get(roleKey())); }
        catch (IllegalArgumentException ignored) { return BetrayalOutpostMaidData.Role.SWORDSMAN; }
    }

    @Override protected Brain.Provider<EntityMaid> brainProvider() { return RevengeMaidBrain.provider(); }
    @Override protected Brain<?> makeBrain(Dynamic<?> data) {
        // Invoked by LivingEntity's constructor, BEFORE this entity's data/fields exist.
        Brain<EntityMaid> brain = brainProvider().makeBrain(data);
        RevengeMaidBrain.register(brain);
        return brain;
    }
    @Override public void refreshBrain(ServerLevel level) {
        getBrain().stopAll(level, this);
        this.brain = getBrain().copyWithoutBehaviors();
        RevengeMaidBrain.register(getBrain());
    }
    @Override protected void customServerAiStep() {
        if (!isInitialized()) initializeStandalone();
        if (RevengeMaidBrain.externallyControlled(this)) {
            safety.release(this);
            EmotionBetrayalManager.pauseOutpostReturnClock(this);
            if (!suspendedBrain) getBrain().stopAll((ServerLevel) level(), this);
            suspendedBrain = true;
            return;
        }
        suspendedBrain = false;
        RevengeMaidBrain.tick(this);
        // EntityMaid's remaining maintenance is in inherited aiStep, not skipped here.
    }

    public boolean isInitialized() { return getPersistentData().getBoolean(INITIALIZED); }
    public void markInitialized() { getPersistentData().putBoolean(INITIALIZED, true); }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(world, difficulty, reason, group, tag);
        if (reason != MobSpawnType.STRUCTURE) initializeStandalone();
        return result;
    }
    private void initializeStandalone() {
        if (isInitialized() || !(level() instanceof ServerLevel level)) return;
        BetrayalOutpostMaidData.Role[] pool = {BetrayalOutpostMaidData.Role.HEAVY,
                BetrayalOutpostMaidData.Role.SWORDSMAN, BetrayalOutpostMaidData.Role.SWORDSMAN,
                BetrayalOutpostMaidData.Role.FARMER, BetrayalOutpostMaidData.Role.FEEDER};
        var role = random.nextInt(100) == 0 ? BetrayalOutpostMaidData.Role.GLY : pool[random.nextInt(pool.length)];
        // A personal anchor, NOT a structure registration or a raid/loot entitlement.
        BetrayalOutpostManager.initializeMaid(this, "standalone:" + getUUID(), role, blockPosition());
        BetrayalOutpostManager.personalizeStandalone(this);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        super.setOwnerUUID(null);
        super.setTame(false);
        // Loading a configured subtype must never reroll role/equipment, even if an older
        // experimental save omitted the explicit once flag.
        if (BetrayalOutpostMaidData.recordedHome(this) != null) markInitialized();
        syncRole(BetrayalOutpostMaidData.role(this));
    }
    @Override public void setTame(boolean value) { super.setTame(false); }
    @Override public void setOwnerUUID(@Nullable UUID owner) { super.setOwnerUUID(null); }
    @Override public void tame(Player player) { /* Wild identity: refuse before item use. */ }
    @Override public boolean openMaidGui(Player player) { return false; }
    @Override public boolean openMaidGui(Player player, int tab) { return false; }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (getTamedItem().test(player.getItemInHand(hand)) || getNtrItem().test(player.getItemInHand(hand)))
            return InteractionResult.PASS;
        return super.mobInteract(player, hand);
    }

    // The real role is persisted in existing outpost NBT. Task is only a compatibility
    // descriptor for item/animation/role components, never a player-switchable work mode.
    @Override public void setTask(IMaidTask ignored) {}
    @Override public IMaidTask getTask() {
        if (getMainHandItem().getItem() instanceof BowItem) return TaskManager.findTask(TaskBowAttack.UID).orElse(TaskManager.getIdleTask());
        if (getMainHandItem().getItem() instanceof CrossbowItem) return TaskManager.findTask(TaskCrossBowAttack.UID).orElse(TaskManager.getIdleTask());
        String id = switch (BetrayalOutpostMaidData.role(this)) {
            case FARMER -> "farm";
            case FEEDER -> "feed";
            case GLY -> "idle";
            default -> "attack";
        };
        return TaskManager.findTask(new net.minecraft.resources.ResourceLocation("touhou_little_maid", id)).orElse(TaskManager.getIdleTask());
    }
    @Override public boolean canAttack(LivingEntity target) {
        return BetrayalOutpostAlertManager.canKeepOrDetectTarget(this, target);
    }
    @Override public net.minecraft.world.phys.AABB searchDimension() {
        return BetrayalOutpostMaidData.pursuitSearchArea(this);
    }
    @Override public net.minecraft.world.entity.schedule.Activity getScheduleDetail() {
        return com.github.tartaricacid.touhoulittlemaid.init.InitEntities.MAID_DAY_SHIFT_SCHEDULES.get()
                .getActivityAt((int) (level().getDayTime() % 24000));
    }

    // TLM components still ask these methods, but no longer read its domestic ranges.
    @Override public boolean isHomeModeEnable() { return true; }
    @Override public boolean hasRestriction() { return true; }
    @Override public BlockPos getRestrictCenter() { return BetrayalOutpostMaidData.home(this); }
    @Override public float getRestrictRadius() { return BetrayalOutpostMaidData.CAMP_ACTIVITY_RADIUS; }
    @Override public BlockPos getBrainSearchPos() { return getRestrictCenter(); }
    @Override public boolean isWithinRestriction(BlockPos pos) {
        if (isInsideCampBuilding(pos)) return true;
        BlockPos home = getRestrictCenter();
        double dx = pos.getX() - home.getX(), dz = pos.getZ() - home.getZ();
        // An egg's home is its spawn floor, not the camp's ground floor. Upstairs eggs
        // must be able to use the same stairs downward that downstairs eggs use upward.
        int below = BetrayalOutpostMaidData.group(this).startsWith("standalone:") ? 16 : 4;
        return dx * dx + dz * dz <= getRestrictRadius() * getRestrictRadius()
                && pos.getY() >= home.getY() - below && pos.getY() <= home.getY() + 16;
    }

    public boolean isInsideCampBuilding(BlockPos pos) {
        var camp = campRecord();
        return camp != null && camp.box().isInside(pos) && pos.getY() >= camp.center().getY();
    }
}
