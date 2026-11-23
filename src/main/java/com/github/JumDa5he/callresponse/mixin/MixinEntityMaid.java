package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageContext;
import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.JumDa5he.callresponse.compat.damage.ProtectionBreakLevel;
import com.github.JumDa5he.callresponse.compat.disguise.OutpostDisguiseRelations;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionActiveDialogue;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntRawHealth;
import com.github.JumDa5he.callresponse.compat.npc.MaidReviveEventData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.outpost.OutpostMaidMarker;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxFaction;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxGrowth;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityMaid.class)
public abstract class MixinEntityMaid extends Mob implements OutpostMaidMarker, WineFoxSurrender.State {
    @Unique private boolean callresponse$deathEmotionsSettled;
    @Unique private DamageSource callresponse$confirmedDeathSource;
    @Unique private java.util.UUID callresponse$contributionLeader;
    @Unique private double callresponse$contributionHealth, callresponse$contributionAttack;
    @Unique private boolean callresponse$contributionSettled;
    @Unique private boolean callresponse$surrenderPhysics;
    @Unique private net.minecraft.world.entity.ai.goal.GoalSelector callresponse$surrenderLookGoals;
    private MixinEntityMaid() { super(null, null); }

    @Unique
    private static final EntityDataAccessor<Boolean> callresponse$OUTPOST_MAID =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BOOLEAN);
    @Unique private static final EntityDataAccessor<Integer> callresponse$SURRENDER_TICKS =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.INT);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void callresponse$defineOutpostMaid(CallbackInfo ci) {
        getEntityData().define(callresponse$OUTPOST_MAID, false);
        getEntityData().define(callresponse$SURRENDER_TICKS, -1);
    }

    @Override
    public boolean callresponse$isOutpostMaid() {
        return getEntityData().get(callresponse$OUTPOST_MAID);
    }

    @Override
    public void callresponse$setOutpostMaid(boolean outpostMaid) {
        getEntityData().set(callresponse$OUTPOST_MAID, outpostMaid);
    }

    @Override public int callresponse$surrenderTicks() { return getEntityData().get(callresponse$SURRENDER_TICKS); }
    @Override public void callresponse$setSurrenderTicks(int value) { getEntityData().set(callresponse$SURRENDER_TICKS, value); }

    /** Local EntityMaid override: optional controls cannot re-enable a surrendered maid's AI. */
    @Override public boolean isNoAi() {
        return WineFoxSurrender.isSurrendered((EntityMaid) (Object) this) || super.isNoAi();
    }
    /** NoAI still blocks all autonomous AI. Only native travel may integrate external velocity. */
    @Override public boolean isEffectiveAi() {
        return callresponse$surrenderPhysics && !level().isClientSide || super.isEffectiveAi();
    }
    @Override public void setTarget(LivingEntity target) {
        super.setTarget(WineFoxSurrender.isSurrendered((EntityMaid) (Object) this) ? null : target);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void callresponse$restoreSurrender(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        WineFoxSurrender.restore((EntityMaid) (Object) this);
    }
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void callresponse$stopSurrenderedBrain(CallbackInfo ci) {
        if (WineFoxSurrender.isSurrendered((EntityMaid) (Object) this)) ci.cancel();
    }
    @Inject(method = "canBrainMoving", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$stopSurrenderedWork(CallbackInfoReturnable<Boolean> cir) {
        if (WineFoxSurrender.isSurrendered((EntityMaid) (Object) this)) cir.setReturnValue(false);
    }
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void callresponse$stopSurrenderedTravel(Vec3 requested, CallbackInfo ci) {
        if (WineFoxSurrender.isSurrendered((EntityMaid) (Object) this)) {
            boolean previous = callresponse$surrenderPhysics;
            callresponse$surrenderPhysics = true;
            try {
                // Mob.NoAI normally also disables LivingEntity.travel's physical integration.
                // No serverAiStep/Brain/Goal/navigation is run inside this narrow travel scope.
                super.travel(Vec3.ZERO);
            } finally { callresponse$surrenderPhysics = previous; }
            ci.cancel();
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void callresponse$surrenderLook(CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (level().isClientSide || !isAlive() || !WineFoxSurrender.isSurrendered(maid)) return;
        if (callresponse$surrenderLookGoals == null) {
            callresponse$surrenderLookGoals = new net.minecraft.world.entity.ai.goal.GoalSelector(() -> level().getProfiler());
            callresponse$surrenderLookGoals.addGoal(0,
                    new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(maid, Player.class, 8.0F));
            callresponse$surrenderLookGoals.addGoal(1, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(maid));
        }
        // NoAI deliberately skips Mob's normal controller tick. This separate selector has no
        // walking/attacking behavior and never ticks MoveControl, navigation or the maid Brain.
        getSensing().tick();
        if ((tickCount + getId()) % 2 == 0) callresponse$surrenderLookGoals.tick();
        else callresponse$surrenderLookGoals.tickRunningGoals(false);
        getLookControl().tick();
        yHeadRot = yBodyRot + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(yHeadRot - yBodyRot),
                -getMaxHeadYRot(), getMaxHeadYRot());
    }

    /** 只在据点复仇女仆真实睡眠/娱乐时将 TLM 传感器范围缩半。 */
    @Inject(method = "searchDimension", at = @At("RETURN"), cancellable = true, remap = false)
    private void callresponse$reduceRelaxedOutpostAwareness(CallbackInfoReturnable<AABB> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        cir.setReturnValue(BetrayalOutpostAlertManager.adjustSensorBox(maid, cir.getReturnValue()));
    }

    /** 困在铁笼中时从源头拒绝 TLM 跟随传送，避免在主人和笼子之间反复闪现。 */
    @Inject(method = "teleportToOwner", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$blockOwnerTeleportWhileCaged(LivingEntity owner,
                                                            CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE)) {
            cir.setReturnValue(false);
        }
    }

    // ===== 狩猎令：放行名单内的目标（玩家默认被 TLM 拒绝） =====
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void callresponse$allowHuntTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (BetrayalOutpostMaidData.areSisters(maid, target) || WineFoxFaction.blocksAttack(maid, target)) {
            cir.setReturnValue(false);
            return;
        }
        if (!maid.level().isClientSide && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            // One gate for TLM StartAttacking and callresponse's camp target selection.
            cir.setReturnValue(BetrayalOutpostAlertManager.canKeepOrDetectTarget(maid, target));
            return;
        }
        if (!maid.level().isClientSide && HuntOrderManager.isHuntTarget(maid, target)) {
            cir.setReturnValue(true);
        }
    }

    /** Recheck before TLM invokes melee baubles, sweep or role-specific extra attacks. */
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void callresponse$rejectInvalidRevengeMelee(Entity target, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!maid.level().isClientSide && BetrayalOutpostMaidData.isOutpostMaid(maid)
                && target instanceof LivingEntity living && !BetrayalOutpostAlertManager.isBaseValidTarget(maid, living)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void example$onHurtHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.level().isClientSide) return;

        // Check the attributable source before the existing maid-to-maid generic-source bridge.
        Entity attributedAttacker = WineFoxFaction.resolveAttacker(source);
        if (WineFoxFaction.blocksAttack(attributedAttacker, maid)
                || attributedAttacker instanceof EntityMaid revenge
                && OutpostDisguiseRelations.isPacifiedTarget(revenge, maid)) {
            cir.setReturnValue(false);
            return;
        }

        // Preserve source qualification BEFORE the existing maid-to-maid generic-source bridge.
        if (com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingBehaviorAuthority.blocksDamage(source)) {
            cir.setReturnValue(false);
            return;
        }

        Entity directEntity = source.getDirectEntity();

        // 背叛女仆攻击通报（始终执行）
        if (directEntity instanceof EntityMaid attackerMaid && EmotionBetrayalManager.isBetraying(attackerMaid)) {
            EmotionBetrayalManager.onVictimAttackedByBetrayer(maid, attackerMaid);
        }

        boolean isOwnerAttack = OwnerDamageSource.isCurrentOwnerSource(maid, source);
        boolean isMaidAttack = directEntity instanceof EntityMaid;

        // 只有来自当前狩猎者的伤害才绕过目标女仆保护，其他伤害仍走原逻辑。
        if (HuntOrderManager.isHuntDamage(maid, source)) {
            this.invulnerableTime = 0;
            boolean huntResult = super.hurt(source, Math.max(amount, 0));
            cir.setReturnValue(huntResult);
            return;
        }


        // BASIC 只建立前置放行凭证，随后继续执行 EntityMaid 原本的 hurt：
        // TLM 限伤、护甲、无敌帧、饰品与死亡保护都照常结算。
        ProtectionBreakLevel protectionLevel = OwnerDamageSource.level();
        if (isOwnerAttack && protectionLevel == ProtectionBreakLevel.BASIC) {
            OwnerDamageContext.begin(maid, source,
                    OwnerDamageContext.normalizeDamage(amount), protectionLevel);
            return;
        }

        // ULTIMATE 完整保留旧版全破路径。
        if (isOwnerAttack && protectionLevel == ProtectionBreakLevel.ULTIMATE) {
            float rawDamage = OwnerDamageContext.normalizeDamage(amount);
            OwnerDamageContext.begin(maid, source, rawDamage, protectionLevel);
            this.invulnerableTime = 0;
            this.hurtTime = 0;
            this.lastHurt = 0.0F;
            try {
                boolean result = super.hurt(source, rawDamage);
                float desiredHealth = OwnerDamageContext.desiredHealth(maid, maid.getHealth());
                if (maid.getHealth() > desiredHealth) {
                    HuntRawHealth.write(maid, desiredHealth);
                }
                if (desiredHealth <= 0.0F && !OwnerDamageContext.wasDeathStarted(maid)
                        && !maid.isRemoved()) {
                    OwnerDamageContext.markDeathStarted(maid, source);
                    super.die(source);
                }
                cir.setReturnValue(result || rawDamage > 0.0F);
            } finally {
                OwnerDamageContext.end(maid, source);
            }
            return;
        }

        // 保留原有女仆攻击女仆范围；这部分暂不扩大，也不冒充主人伤害。
        if (isMaidAttack) {
            DamageSource neutral = maid.damageSources().generic();
            boolean result = super.hurt(neutral, amount);
            if (result) {
                Entity entity = source.getDirectEntity();
                if (entity == null) entity = source.getEntity();
                if (entity != null) {
                    double dx = entity.getX() - maid.getX();
                    double dz = entity.getZ() - maid.getZ();
                    while (dx * dx + dz * dz < 1.0E-4) {
                        dx = (Math.random() - Math.random()) * 0.01;
                        dz = (Math.random() - Math.random()) * 0.01;
                    }
                    maid.knockback(0.4, dx, dz);
                }
            }
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void callresponse$finishBasicOwnerDamage(DamageSource source, float amount,
                                                      CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (OwnerDamageContext.isBasic(maid, source)) {
            OwnerDamageContext.end(maid, source);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void callresponse$trackOwnerDamageDeath(DamageSource source, CallbackInfo ci) {
        callresponse$confirmedDeathSource = source;
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!this.dead && !maid.level().isClientSide && WineFoxFaction.isRevenge(maid)) {
            callresponse$contributionLeader = WineFoxFaction.leaderOf(maid);
            callresponse$contributionHealth = maid.getMaxHealth();
            callresponse$contributionAttack = maid.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        }
        if (OwnerDamageContext.isUltimate(maid, source)) {
            OwnerDamageContext.markDeathStarted(maid, source);
        }
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void callresponse$commitRevengeContribution(DamageSource source, CallbackInfo ci) {
        if (this.dead && !callresponse$contributionSettled && !level().isClientSide) {
            callresponse$contributionSettled = true;
            WineFoxGrowth.onMaidDeath((EntityMaid) (Object) this, callresponse$contributionLeader,
                    callresponse$contributionHealth, callresponse$contributionAttack);
            com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager.onConfirmedGuardDeath(
                    (EntityMaid) (Object) this, source);
        }
    }

    /** 这里只会在死亡已经成立、TLM 即将把女仆 NBT 写入复活胶片时执行。 */
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void callresponse$recordReviveEventDeathType(CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.level().isClientSide) return;
        // Existing ultimate-owner damage can enter Mob.die directly. Its confirmed native
        // equipment path is still a valid receipt; capture before TLM empties any real slots.
        if (this.dead && !callresponse$contributionSettled && WineFoxFaction.isRevenge(maid)) {
            if (callresponse$contributionLeader == null) {
                callresponse$contributionLeader = WineFoxFaction.leaderOf(maid);
                callresponse$contributionHealth = maid.getMaxHealth();
                callresponse$contributionAttack = maid.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            }
            callresponse$contributionSettled = true;
            WineFoxGrowth.onMaidDeath(maid, callresponse$contributionLeader,
                    callresponse$contributionHealth, callresponse$contributionAttack);
            com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager.onConfirmedGuardDeath(
                    maid, callresponse$confirmedDeathSource);
        }
        if (this.dead && !callresponse$deathEmotionsSettled) {
            callresponse$deathEmotionsSettled = true;
            DamageSource deathSource = callresponse$confirmedDeathSource;
            com.github.JumDa5he.callresponse.compat.emotion.EmotionPassiveManager.onConfirmedMaidDeath(
                    maid, deathSource == null ? null : deathSource.getEntity());
            com.github.JumDa5he.callresponse.compat.npc.NpcEventData.remove(maid);
        }
        if (this.dead && EmotionBetrayalManager.isBetraying(maid)) {
            EmotionBetrayalManager.onConfirmedDeath(maid);
        }
        if (!this.dead || EmotionBetrayalManager.isActualBetrayal(maid)) return;
        DamageSource source = callresponse$confirmedDeathSource;
        boolean ownerKilled = source != null && (OwnerDamageSource.isCurrentOwnerSource(maid, source)
                || OwnerDamageContext.hasActiveDamage(maid, source));
        MaidReviveEventData.recordDeath(maid, ownerKilled, maid.level().getGameTime());
    }

    @Inject(method = "tameMaid", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$rejectBetrayerTaming(net.minecraft.world.item.ItemStack stack, Player player,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        if (EmotionBetrayalManager.isActualBetrayal((EntityMaid) (Object) this)) {
            cir.setReturnValue(net.minecraft.world.InteractionResult.PASS);
        }
    }

    @Unique
    private boolean isSplashDamage(DamageSource source) {
        if (source.getDirectEntity() != source.getEntity()) return true;
        return false;
    }

    @Unique
    private boolean isAimingAtMaid(Player player, EntityMaid maid) {
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getLookAngle();
        double reach = 4.5;
        Vec3 endPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        // 搜索射线路径上的所有实体
        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach).inflate(1.0);
        // 获取第一个被击中的实体
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eyePos, endPos, searchBox, e -> e == maid || e.isPickable());
        if (hit == null || hit.getEntity() != maid) return false;
        // 确认射线到女仆之间没有方块阻挡
        Vec3 maidPos = maid.getEyePosition();
        HitResult blockHit = player.level().clip(new ClipContext(eyePos, maidPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return blockHit.getType() == HitResult.Type.MISS || blockHit.getLocation().distanceToSqr(eyePos) >= eyePos.distanceToSqr(maidPos);
    }
}
