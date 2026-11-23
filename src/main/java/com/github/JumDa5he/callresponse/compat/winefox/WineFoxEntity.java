package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Independent hostile mob. IMaid is solely a rendering view; asStrictMaid stays null. */
public final class WineFoxEntity extends Monster implements IMaid {
    public static final int ATTACK_COOLDOWN = 30, MELEE_HIT_TICK = 12, RANGED_HIT_TICK = 20,
            MELEE_ACTION_TICKS = 28, RANGED_ACTION_TICKS = 36, DEATH_TICKS = 72;
    private static final double BASE_HEALTH = 400, LEGACY_BASE_HEALTH = 300;
    public static final double MELEE_RANGE = 4, SHOT_RANGE = 12, SEARCH_RANGE = 32;
    private static final double LEGACY_MOVE_SPEED = 0.28, MOVE_SPEED = 0.336, CHASE_SPEED = 1.0, WANDER_SPEED = 0.7;
    private static final int SCAN_TICKS = 40, PROBE_TICKS = 10, PATH_TICKS = 20,
            STALL_TICKS = 120, SKIP_TICKS = 100, CANDIDATE_LIMIT = 24, REPOSITION_TICKS = 30;
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(WineFoxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(WineFoxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(WineFoxEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DEATH_START = SynchedEntityData.defineId(WineFoxEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DIALOGUE_LINE = SynchedEntityData.defineId(WineFoxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DIALOGUE_START = SynchedEntityData.defineId(WineFoxEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> INHERITANCE = SynchedEntityData.defineId(WineFoxEntity.class, EntityDataSerializers.COMPOUND_TAG);
    public static final int LAST_STAND_TICKS = 60;
    private static final float LAST_STAND_FRACTION = 0.10F;
    private final WineFoxDialogue dialogue = new WineFoxDialogue(this);
    private final WineFoxVehicleEscape vehicleEscape = new WineFoxVehicleEscape();
    private boolean healthGuardReady, readingHealth, lastStandUsed;
    private int protectionTicks, administrativeDamageDepth;
    private int cooldown;
    private boolean resolved;
    private boolean equipmentInitialized, equipmentRewarded, experienceRewarded;
    private boolean repositionPending;
    private Vec3 repositionDestination;
    private long repositionUntil;
    private final List<UUID> shotTargets = new ArrayList<>(2);

    public WineFoxEntity(EntityType<? extends WineFoxEntity> type, Level level) {
        super(type, level);
        if (!level.isClientSide) entityData.set(VARIANT, random.nextInt(2));
        setHealth(getMaxHealth());
        setPersistenceRequired();
        healthGuardReady = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 20).add(Attributes.FOLLOW_RANGE, SEARCH_RANGE)
                .add(Attributes.MOVEMENT_SPEED, MOVE_SPEED);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(VARIANT, 0); builder.define(ATTACK, 0); builder.define(START, 0L);
        builder.define(DEATH_START, -1L);
        builder.define(DIALOGUE_LINE, 0); builder.define(DIALOGUE_START, -1L);
        builder.define(INHERITANCE, new CompoundTag());
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CombatGoal());
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, WANDER_SPEED));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }
    public int getVariant() { return entityData.get(VARIANT); }
    @Override public boolean canBeAffected(MobEffectInstance effect) {
        return !WineFoxVanillaEffects.reject(effect.getEffect()) && super.canBeAffected(effect);
    }
    @Override public void removeVehicle() {
        super.removeVehicle();
        if (!isPassenger() && vehicleEscape != null) vehicleEscape.reset();
    }
    public int getAttackKind() { return entityData.get(ATTACK); }
    public long getAttackStart() { return entityData.get(START); }
    public long getDeathStart() { return entityData.get(DEATH_START); }
    public boolean isDeathConfirmed() { return dead && getDeathStart() >= 0; }
    public int getDialogueLine() { return entityData.get(DIALOGUE_LINE); }
    public long getDialogueStart() { return entityData.get(DIALOGUE_START); }
    public void cageEscapeDialogue() { dialogue.cageEscape(); }
    void syncDialogue(int line) {
        entityData.set(DIALOGUE_START, level().getGameTime());
        entityData.set(DIALOGUE_LINE, line);
    }
    public boolean hasUsedLastStand() { return lastStandUsed; }
    public int getProtectionTicks() { return protectionTicks; }
    public int getAttackDuration() { return getAttackKind() == 1 ? MELEE_ACTION_TICKS : RANGED_ACTION_TICKS; }
    public double getInheritedHealth() { return entityData.get(INHERITANCE).getDouble("Health"); }
    public double getInheritedAttack() { return entityData.get(INHERITANCE).getDouble("Attack"); }

    /** Add only this monster's permanent inheritance after normal attributes/equipment.
     * LivingEntity.getMaxHealth (also health HUDs) calls this virtual method. No global caps change. */
    @Override public double getAttributeValue(Holder<Attribute> attribute) {
        double normal = super.getAttributeValue(attribute);
        if (attribute == Attributes.MAX_HEALTH) return WineFoxGrowth.add(normal, getInheritedHealth());
        if (attribute == Attributes.ATTACK_DAMAGE) return WineFoxGrowth.add(normal, getInheritedAttack());
        return normal;
    }

    void applyInheritance(WineFoxLeaders.Totals totals) {
        double oldHealth = getInheritedHealth();
        double health = Math.max(oldHealth, WineFoxGrowth.nonnegative(totals.health()));
        double attack = Math.max(getInheritedAttack(), WineFoxGrowth.nonnegative(totals.attack()));
        if (health == oldHealth && attack == getInheritedAttack()) return;
        float before = getHealth();
        CompoundTag value = new CompoundTag();
        value.putDouble("Health", health);
        value.putDouble("Attack", attack);
        entityData.set(INHERITANCE, value);
        // Increasing the maximum and current health by the same amount preserves existing injury.
        super.setHealth(WineFoxGrowth.damage(WineFoxGrowth.add(before, health - oldHealth)));
    }
    @Override public String getModelId() { return "callresponse:corrupted_wine_fox"; }
    @Override public Mob asEntity() { return this; }

    public boolean validTarget(LivingEntity target) {
        return target != null && target != this && !(target instanceof WineFoxEntity)
                && !WineFoxFaction.blocksAttack(this, target)
                && !(target instanceof ArmorStand) && target.level() == level() && target.isAlive()
                && !target.isRemoved() && target.isAttackable() && !target.isInvulnerable()
                && (!(target instanceof Player p) || !p.isCreative() && !p.isSpectator())
                && level().hasChunkAt(target.blockPosition());
    }
    @Override public boolean canAttack(LivingEntity target) { return validTarget(target) && super.canAttack(target); }
    @Override public void setTarget(LivingEntity target) { super.setTarget(validTarget(target) ? target : null); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (WineFoxFaction.blocksAttack(WineFoxFaction.resolveAttacker(source), this)) return false;
        boolean administrative = source.is(DamageTypes.GENERIC_KILL);
        if (!administrative && protectionTicks > 0) return false;
        if (administrative) administrativeDamageDepth++;
        try { return super.hurt(source, amount); }
        finally { if (administrative) administrativeDamageDepth--; }
    }

    @Override protected void actuallyHurt(DamageSource source, float amount) {
        if (WineFoxFaction.blocksAttack(WineFoxFaction.resolveAttacker(source), this)) return;
        boolean administrative = source.is(DamageTypes.GENERIC_KILL);
        if (!administrative && protectionTicks > 0) return;
        if (administrative) administrativeDamageDepth++;
        try { super.actuallyHurt(source, amount); }
        finally { if (administrative) administrativeDamageDepth--; }
    }

    /** Native actuallyHurt reaches here only AFTER armor, effects, absorption and Forge damage events. */
    @Override public void setHealth(float health) {
        float before = healthGuardReady ? getHealth() : 0;
        boolean guard = healthGuardReady && !readingHealth && administrativeDamageDepth == 0
                && !level().isClientSide && !dead && !isRemoved() && before > 0;
        if (guard && health < before) {
            if (protectionTicks > 0) return; // Direct writes also cannot bypass the active protection.
            if (!lastStandUsed && health <= getMaxHealth() * LAST_STAND_FRACTION) {
                activateLastStand();
                return;
            }
        }
        super.setHealth(health);
        if (guard && getHealth() < before && getHealth() > 0) dialogue.hurt();
    }

    private void activateLastStand() {
        lastStandUsed = true;
        protectionTicks = LAST_STAND_TICKS;
        float threshold = getMaxHealth() * LAST_STAND_FRACTION;
        // A legacy saved individual already below the threshold gains protection, not free healing.
        super.setHealth(getHealth() > 0 ? Math.min(getHealth(), threshold) : threshold);
        dialogue.lastStand();
    }

    void releaseFriendlyTarget() {
        if (WineFoxFaction.blocksAttack(this, getTarget())) {
            setTarget(null);
            getNavigation().stop();
        }
        if (WineFoxFaction.blocksAttack(this, getLastHurtByMob())) setLastHurtByMob(null);
    }

    boolean visibleFrom(Vec3 eyes, LivingEntity target) {
        if (!level().hasChunksAt(BlockPos.containing(Math.min(eyes.x, target.getX()), eyes.y, Math.min(eyes.z, target.getZ())),
                BlockPos.containing(Math.max(eyes.x, target.getX()), eyes.y, Math.max(eyes.z, target.getZ())))) return false;
        return level().clip(new ClipContext(eyes, target.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    boolean canHit(LivingEntity target, double range) {
        return validTarget(target) && distanceToSqr(target) <= range * range && visibleFrom(getEyePosition(), target);
    }

    @Override public void onAddedToLevel() {
        super.onAddedToLevel();
        // The loader also invokes this hook after a failed worldgen/chunk insert. Check actual registration.
        if (level() instanceof ServerLevel level && level.getEntity(getUUID()) == this)
            WineFoxDialogue.announceArrival(this, null);
    }

    @Override public void tick() {
        // A freshly inserted fox can become tracked/ticking after onAddedToLevel.
        if (firstTick && isAddedToLevel() && !level().isClientSide) WineFoxDialogue.announceArrival(this, null);
        if (!level().isClientSide && protectionTicks > 0) protectionTicks--;
        super.tick();
        if (level().isClientSide) return;
        if (cooldown > 0) cooldown--;
        if (isAlive() && !isRemoved()) {
            vehicleEscape.tick(this);
            dialogue.tick();
            if (Math.floorMod(tickCount + getId(), 20) == 0) {
                WineFoxGrowth.apply(this);
                WineFoxFaction.recruitNearby(this);
            }
            releaseFriendlyTarget();
        }
        if (!isAlive() || isRemoved() || isNoAi()) { clearAction(); return; }
        if (getAttackKind() == 0) return;
        long age = level().getGameTime() - getAttackStart();
        if (!resolved && age >= (getAttackKind() == 1 ? MELEE_HIT_TICK : RANGED_HIT_TICK)) {
            resolved = true;
            if (getAttackKind() == 1) sweep();
            else for (UUID uuid : shotTargets) {
                Entity found = ((ServerLevel) level()).getEntity(uuid);
                if (found instanceof LivingEntity target && distanceToSqr(target) > MELEE_RANGE * MELEE_RANGE && canHit(target, SHOT_RANGE))
                    WineFoxSpike.spawn(this, target, WineFoxGrowth.damage(getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8));
            }
            shotTargets.clear();
            if (isAlive() && getAttackKind() != 0) repositionPending = true;
        }
        if (getAttackKind() != 0 && age >= getAttackDuration()) {
            clearAttackState();
        }
    }

    private boolean readyForAttack() {
        // A new action may take over the end of a cast only after the preceding hit was resolved.
        return cooldown == 0 && (getAttackKind() == 0 || resolved);
    }

    void beginAttack(int kind, List<LivingEntity> targets) {
        if (!readyForAttack() || !isAlive() || isNoAi()
                || repositionPending || repositionDestination != null) return;
        dialogue.combat();
        cooldown = ATTACK_COOLDOWN;
        resolved = false; shotTargets.clear();
        targets.stream().limit(2).map(Entity::getUUID).forEach(shotTargets::add);
        entityData.set(START, level().getGameTime()); entityData.set(ATTACK, kind);
        getNavigation().stop();
    }
    private void sweep() {
        float damage = WineFoxGrowth.damage(getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(MELEE_RANGE),
                candidate -> canHit(candidate, MELEE_RANGE))) {
            if (!isAlive() || dead) break; // Thorn retaliation can kill the attacker during this sweep.
            DamageSource source = damageSources().mobAttack(this);
            ServerLevel server = (ServerLevel) level();
            float enchantedDamage = WineFoxGrowth.damage(EnchantmentHelper.modifyDamage(server, getWeaponItem(), victim, source, damage));
            float knockback = getKnockback(victim, source);
            if (victim.hurt(source, enchantedDamage)) {
                if (knockback > 0) {
                    victim.knockback(knockback * 0.5, Math.sin(getYRot() * Math.PI / 180), -Math.cos(getYRot() * Math.PI / 180));
                    setDeltaMovement(getDeltaMovement().multiply(0.6, 1, 0.6));
                }
                // Native 1.21 effects include fire aspect and defensive enchantment retaliation.
                EnchantmentHelper.doPostAttackEffects(server, victim, source);
                setLastHurtMob(victim);
            }
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1, 1);
        ((ServerLevel) level()).sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 0.9, getZ(), 1, 0, 0, 0, 0);
    }
    private void clearReposition() {
        if (repositionDestination != null) getNavigation().stop();
        repositionPending = false; repositionDestination = null; repositionUntil = 0;
    }
    private void clearAttackState() { entityData.set(ATTACK, 0); shotTargets.clear(); resolved = true; }
    private void clearAction() { clearAttackState(); clearReposition(); }
    @Override public void die(DamageSource source) {
        if (!level().isClientSide && !dead && !isRemoved() && !readingHealth
                && administrativeDamageDepth == 0 && !source.is(DamageTypes.GENERIC_KILL)) {
            if (protectionTicks > 0) return;
            // Fallback for a caller that reached die without going through the native health setter.
            if (!lastStandUsed && getHealth() <= getMaxHealth() * LAST_STAND_FRACTION) {
                activateLastStand();
                return;
            }
        }
        boolean wasDead = dead;
        super.die(source); // NeoForge cancellation/totems retain their native authority.
        if (!level().isClientSide && !wasDead && dead) {
            entityData.set(DEATH_START, level().getGameTime());
            protectionTicks = 0;
            WineFoxFaction.onLeaderDeath(this);
            com.github.JumDa5he.callresponse.compat.emotion.EmotionPassiveManager.onConfirmedWineFoxDeath(this, source.getEntity());
            // A death/drop listener may already have removed a genuinely dead corpse.
            // Its followers still need the receipt; discard/unload outside die never reaches here.
            if (!isRemoved()) {
                clearAction(); setTarget(null); getNavigation().stop();
                dialogue.death();
            }
        }
    }
    @Override protected void tickDeath() {
        if (getDeathStart() < 0 || !level().isClientSide && !dead) return;
        deathTime++;
        if (deathTime >= DEATH_TICKS && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group);
        if (!equipmentInitialized) {
            equipmentInitialized = true;
            WineFoxEquipment.equip(this);
        }
        return result;
    }
    // Resolve registered bindings after TLM common setup, on the first eligible death.
    private static final Supplier<List<Item>> REWARD_BAUBLES = Suppliers.memoize(() ->
            BuiltInRegistries.ITEM.entrySet().stream()
                    .filter(entry -> {
                        String namespace = entry.getKey().location().getNamespace();
                        return namespace.equals(TouhouLittleMaid.MOD_ID) || namespace.equals(CallResponseMod.MOD_ID)
                                || entry.getValue() == Items.TOTEM_OF_UNDYING; // Bound by TLM itself.
                    })
                    .map(Map.Entry::getValue)
                    .filter(item -> BaubleManager.getBauble(item.getDefaultInstance()) != null)
                    .toList());

    /** Native death-loot hook: doMobLoot and NeoForge capture/cancellation remain outside this method. */
    @Override protected void dropCustomDeathLoot(ServerLevel server, DamageSource source, boolean recentlyHit) {
        if (equipmentRewarded || !dead || isRemoved()) return;
        equipmentRewarded = true;
        List<EquipmentSlot> available = new ArrayList<>(3);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.MAINHAND))
            if (!getItemBySlot(slot).isEmpty()) available.add(slot);
        if (!available.isEmpty()) {
            EquipmentSlot slot = available.get(random.nextInt(available.size()));
            ItemStack reward = getItemBySlot(slot).split(1);
            spawnAtLocation(reward); // Original NBT, including high levels, Unbreakable and vanishing curse.
        }
        spawnAtLocation(RemnantMemoryItem.create(random));
        // Native 1.21 enchanted-count loot resolves the damage-source attacker and gameplay enchantment level.
        int looting = source.getEntity() instanceof LivingEntity attacker
                ? EnchantmentHelper.getEnchantmentLevel(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.LOOTING), attacker) : 0;
        rollAdditionalDeathLoot(random, looting, LootTable.createStackSplitter(server, this::spawnAtLocation));
        // No super: Mob's six independent equipment rolls would duplicate the chosen reward.
    }

    static void rollAdditionalDeathLoot(RandomSource random, int looting, Consumer<ItemStack> drop) {
        List<Item> baubles = REWARD_BAUBLES.get();
        if (!baubles.isEmpty()) {
            int rolls = random.nextIntBetweenInclusive(1, 2);
            for (int i = 0; i < rolls; i++) {
                drop.accept(baubles.get(random.nextInt(baubles.size())).getDefaultInstance());
            }
        }
        drop.accept(new ItemStack(Items.ANCIENT_DEBRIS, materialCount(random, 2, 4, looting)));
        drop.accept(new ItemStack(Items.CRYING_OBSIDIAN, materialCount(random, 1, 3, looting)));
    }

    private static int materialCount(RandomSource random, int minimum, int maximum, int looting) {
        int base = random.nextIntBetweenInclusive(minimum, maximum);
        return looting > 0 ? base + Math.round(looting * random.nextFloat()) : base;
    }
    @Override protected int getBaseExperienceReward() { return 200; }
    @Override protected void dropExperience(Entity killer) {
        if (experienceRewarded || !dead || !(level() instanceof ServerLevel server)) return;
        experienceRewarded = true;
        if (!wasExperienceConsumed() && level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            int reward = net.neoforged.neoforge.event.EventHooks.getExperienceDrop(this, lastHurtByPlayer, getBaseExperienceReward());
            ExperienceOrb.award(server, position(), Math.max(0, reward));
        }
    }
    @Override public void remove(RemovalReason reason) { clearAction(); super.remove(reason); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("WineFoxVariant", getVariant()); tag.putInt("WineFoxCooldown", cooldown);
        tag.putBoolean("WineFoxEquipmentInitialized", equipmentInitialized);
        tag.putBoolean("WineFoxEquipmentRewarded", equipmentRewarded);
        tag.putBoolean("WineFoxExperienceRewarded", experienceRewarded);
        tag.put("WineFoxInheritance", entityData.get(INHERITANCE).copy());
        tag.putBoolean("WineFoxConfirmedDead", dead && getDeathStart() >= 0);
        tag.putBoolean("WineFoxSpeedUpgraded", true);
        tag.putBoolean("WineFoxBaseHealthUpgraded", true);
        tag.putBoolean("WineFoxLastStandUsed", lastStandUsed);
        tag.putInt("WineFoxProtectionTicks", protectionTicks);
        dialogue.save(tag);
        vehicleEscape.save(tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        // Restore the true maximum before the native health loader clamps current health.
        CompoundTag storedGrowth = tag.getCompound("WineFoxInheritance"), growth = new CompoundTag();
        growth.putDouble("Health", WineFoxGrowth.nonnegative(storedGrowth.getDouble("Health")));
        growth.putDouble("Attack", WineFoxGrowth.nonnegative(storedGrowth.getDouble("Attack")));
        entityData.set(INHERITANCE, growth);
        WineFoxSpawnStats.beforeLoad(this);
        readingHealth = true;
        try { super.readAdditionalSaveData(tag); }
        finally { readingHealth = false; }
        lastStandUsed = tag.getBoolean("WineFoxLastStandUsed");
        protectionTicks = lastStandUsed ? Math.max(0, Math.min(LAST_STAND_TICKS, tag.getInt("WineFoxProtectionTicks"))) : 0;
        dialogue.load(tag);
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (tag.hasUUID("UUID") && tag.contains("WineFoxVariant") && !tag.getBoolean("WineFoxBaseHealthUpgraded")
                && maxHealth != null && maxHealth.getBaseValue() == LEGACY_BASE_HEALTH) {
            maxHealth.setBaseValue(BASE_HEALTH);
        }
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (!tag.getBoolean("WineFoxSpeedUpgraded") && speed != null
                && Double.compare(speed.getBaseValue(), LEGACY_MOVE_SPEED) == 0) {
            speed.setBaseValue(MOVE_SPEED);
        }
        if (tag.contains("WineFoxVariant")) entityData.set(VARIANT, Math.floorMod(tag.getInt("WineFoxVariant"), 2));
        cooldown = Math.min(ATTACK_COOLDOWN, Math.max(0, tag.getInt("WineFoxCooldown")));
        // /summon first loads an id-only tag, THEN calls finalizeSpawn. A real saved entity has
        // UUID/equipment fields (including old empty slots); do not mistake a new command for a reload.
        equipmentInitialized = tag.contains("WineFoxEquipmentInitialized") || tag.hasUUID("UUID")
                || tag.contains("ArmorItems") || tag.contains("HandItems");
        equipmentRewarded = tag.getBoolean("WineFoxEquipmentRewarded");
        experienceRewarded = tag.getBoolean("WineFoxExperienceRewarded");
        boolean legacyCorpse = !tag.contains("WineFoxConfirmedDead") && getHealth() <= 0 && deathTime > 0;
        if (tag.getBoolean("WineFoxConfirmedDead") || legacyCorpse) {
            dead = true;
            protectionTicks = 0;
            if (legacyCorpse) equipmentRewarded = true;
            experienceRewarded = true;
            entityData.set(DEATH_START, level().getGameTime() - Math.max(0, deathTime));
        }
        clearAction(); setTarget(null);
        vehicleEscape.load(tag);
        for (MobEffectInstance effect : List.copyOf(getActiveEffects())) {
            if (!WineFoxVanillaEffects.reject(effect.getEffect())) continue;
            MobEffectInstance removed = removeEffectNoUpdate(effect.getEffect());
            if (removed != null) onEffectRemoved(removed);
        }
        WineFoxSpawnStats.afterLoad(this, tag);
    }

    /** Sole target/pursuit owner: one candidate per 10 ticks, at most three endpoint paths per probe. */
    private final class CombatGoal extends Goal {
        private final Map<UUID, Long> skipped = new HashMap<>();
        private List<LivingEntity> candidates = List.of();
        private int cursor, nextScan, nextProbe, nextMove, stalled;
        private double bestDistance = Double.MAX_VALUE;
        private Path chosenPath;
        CombatGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() {
            if (!isAlive() || isNoAi()) return false;
            if (repositionPending || repositionDestination != null) return true;
            if (getAttackKind() != 0) return true;
            if (validTarget(getTarget()) && distanceToSqr(getTarget()) <= SEARCH_RANGE * SEARCH_RANGE) return true;
            setTarget(null);
            if (tickCount < nextProbe) return false;
            nextProbe = tickCount + PROBE_TICKS;
            if (cursor >= candidates.size()) {
                if (tickCount < nextScan) return false;
                nextScan = tickCount + SCAN_TICKS; cursor = 0;
                skipped.entrySet().removeIf(e -> e.getValue() <= level().getGameTime());
                candidates = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SEARCH_RANGE),
                        t -> validTarget(t) && distanceToSqr(t) <= SEARCH_RANGE * SEARCH_RANGE && !skipped.containsKey(t.getUUID()))
                        .stream().sorted(Comparator.<LivingEntity>comparingInt(t -> WineFoxPursuit.isPreferredTarget(WineFoxEntity.this, t)
                                        ? 0 : t == getLastHurtByMob() ? 1 : 2)
                                .thenComparingDouble(WineFoxEntity.this::distanceToSqr)).limit(CANDIDATE_LIMIT).toList();
            }
            if (cursor >= candidates.size()) return false;
            LivingEntity target = candidates.get(cursor++);
            if (!validTarget(target) || !visibleFrom(getEyePosition(), target)) return false;
            chosenPath = attackPath(target);
            if (!canHit(target, SHOT_RANGE) && chosenPath == null) { skip(target); return false; }
            setTarget(target); bestDistance = distanceToSqr(target); stalled = 0;
            return true;
        }
        @Override public boolean canContinueToUse() {
            return isAlive() && !isNoAi() && (repositionPending || repositionDestination != null
                    || getAttackKind() != 0 || validTarget(getTarget()) && distanceToSqr(getTarget()) <= SEARCH_RANGE * SEARCH_RANGE);
        }
        @Override public void start() {
            nextMove = tickCount + PATH_TICKS;
            if (!repositionPending && repositionDestination == null && chosenPath != null)
                getNavigation().moveTo(chosenPath, CHASE_SPEED);
        }
        @Override public void stop() { clearReposition(); getNavigation().stop(); setTarget(null); chosenPath = null; }
        private void skip(LivingEntity target) { if (target != null) skipped.put(target.getUUID(), level().getGameTime() + SKIP_TICKS); }
        @Override public void tick() {
            if (!isAlive() || isNoAi()) { clearReposition(); return; }
            LivingEntity target = getTarget();
            if (validTarget(target)) getLookControl().setLookAt(target, 30, 30);
            if (getAttackKind() != 0 && !resolved) { getNavigation().stop(); return; }
            if (repositionPending) {
                repositionPending = false; // One bounded attempt after the hit frame, including a missed attack.
                beginReposition();
            }
            if (repositionDestination != null) {
                boolean nextAttackReady = readyForAttack() && canHit(target, SHOT_RANGE);
                if (!nextAttackReady && level().getGameTime() < repositionUntil && !getNavigation().isDone()
                        && position().distanceToSqr(repositionDestination) > 0.09) return;
                clearReposition();
                stalled = 0; bestDistance = validTarget(target) ? distanceToSqr(target) : Double.MAX_VALUE;
                nextMove = tickCount;
            }
            if (getAttackKind() != 0 && !readyForAttack()) return;
            if (!validTarget(target)) return;
            if (canHit(target, SHOT_RANGE)) {
                getNavigation().stop(); stalled = 0; bestDistance = distanceToSqr(target);
                if (cooldown == 0) {
                    List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SHOT_RANGE),
                            t -> canHit(t, SHOT_RANGE)).stream().sorted(Comparator.comparingDouble(WineFoxEntity.this::distanceToSqr)).toList();
                    if (!nearby.isEmpty()) {
                        if (distanceToSqr(nearby.get(0)) <= MELEE_RANGE * MELEE_RANGE) beginAttack(1, List.of());
                        else beginAttack(2, nearby.stream().limit(2).toList());
                    }
                }
                return;
            }
            if (getAttackKind() != 0 || tickCount < nextMove) return;
            nextMove = tickCount + PATH_TICKS;
            double distance = distanceToSqr(target);
            if (distance < bestDistance - 1) { bestDistance = distance; stalled = 0; }
            else stalled += PATH_TICKS;
            if (stalled >= STALL_TICKS) { skip(target); setTarget(null); getNavigation().stop(); return; }
            chosenPath = attackPath(target);
            if (chosenPath != null) getNavigation().moveTo(chosenPath, CHASE_SPEED);
            else { getNavigation().stop(); stalled += PATH_TICKS; }
        }

        /** One short walk through the existing navigation; never teleports or owns a second Goal. */
        private void beginReposition() {
            if (!onGround() || isInWater() || isInLava() || isPassenger()) return;
            Vec3 origin = position();
            double angle = random.nextDouble() * Math.PI * 2;
            int probes = 0;
            for (int attempt = 0; attempt < 4; attempt++) {
                double direction = angle + attempt * Math.PI / 2;
                BlockPos column = BlockPos.containing(origin.x + Math.cos(direction) * 2.5,
                        origin.y, origin.z + Math.sin(direction) * 2.5);
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos requested = column.offset(0, dy, 0);
                    Vec3 end = Vec3.atBottomCenterOf(requested);
                    double distance = end.subtract(origin).horizontalDistanceSqr();
                    if (distance < 4 || distance > 9 || !safeRepositionStep(requested)) continue;
                    if (++probes > 4) return;
                    Path path = getNavigation().createPath(requested, 0);
                    if (path == null || !path.canReach() || path.getEndNode() == null
                            || !path.getEndNode().asBlockPos().equals(requested)) continue;
                    boolean safe = true;
                    double length = 0;
                    Vec3 previous = origin;
                    for (int node = 0; node < path.getNodeCount(); node++) {
                        BlockPos step = path.getNode(node).asBlockPos();
                        Vec3 point = Vec3.atBottomCenterOf(step);
                        length += point.distanceTo(previous); previous = point;
                        if (length > 5 || !safeRepositionStep(step)) { safe = false; break; }
                    }
                    if (!safe || !getNavigation().moveTo(path, CHASE_SPEED)) continue;
                    repositionDestination = end;
                    repositionUntil = level().getGameTime() + REPOSITION_TICKS;
                    return;
                }
            }
        }

        private boolean safeRepositionStep(BlockPos feet) {
            Vec3 destination = Vec3.atBottomCenterOf(feet);
            AABB body = getBoundingBox().move(destination.subtract(position()));
            BlockPos min = BlockPos.containing(body.minX - 0.01, body.minY - 1, body.minZ - 0.01);
            BlockPos max = BlockPos.containing(body.maxX + 0.01, body.maxY + 0.01, body.maxZ + 0.01);
            if (!level().hasChunksAt(min, max) || !level().getWorldBorder().isWithinBounds(body)
                    || !level().loadedAndEntityCanStandOn(feet.below(), WineFoxEntity.this)
                    || !level().noCollision(WineFoxEntity.this, body)) return false;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockState state = level().getBlockState(pos);
                if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                        || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                        || state.is(Blocks.POWDER_SNOW)
                        || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) return false;
            }
            return true;
        }

        /** Validate the actual endpoint as a visible firing position, not just a non-null path. */
        private Path attackPath(LivingEntity target) {
            if (canHit(target, SHOT_RANGE)) return null;
            Vec3 towards = position().subtract(target.position()).normalize();
            for (int probe = 0; probe < 3; probe++) {
                double angle = probe == 0 ? 0 : probe == 1 ? Math.PI / 3 : -Math.PI / 3;
                Vec3 offset = towards.yRot((float) angle).scale(probe == 0 ? 8 : 4);
                BlockPos requested = BlockPos.containing(target.getX() + offset.x, target.getY(), target.getZ() + offset.z);
                if (!level().hasChunkAt(requested)) continue;
                Path path = getNavigation().createPath(requested, 0);
                if (path == null || path.getEndNode() == null) continue;
                if (!level().hasChunkAt(path.getEndNode().asBlockPos())) continue;
                Vec3 end = Vec3.atBottomCenterOf(path.getEndNode().asBlockPos());
                if (end.distanceToSqr(target.position()) <= SHOT_RANGE * SHOT_RANGE
                        && level().noCollision(WineFoxEntity.this, getBoundingBox().move(end.subtract(position())))
                        && visibleFrom(end.add(0, getEyeHeight(), 0), target)) return path;
            }
            return null;
        }
    }
}
