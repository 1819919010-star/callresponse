package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageContext;
import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionActiveDialogue;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntRawHealth;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.state.MaidPathRepair;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityMaid.class)
public abstract class MixinEntityMaid extends Mob {
    private MixinEntityMaid() { super(null, null); }

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
        if (!maid.level().isClientSide && HuntOrderManager.isHuntTarget(maid, target)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void example$onHurtHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.level().isClientSide) return;

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

        if (isOwnerAttack && !EmotionBetrayalManager.isBetraying(maid)) {
            ServerPlayer player = OwnerDamageSource.findServerPlayer(maid, source);
            // 保持旧语义：只有主人近距离明确瞄准的直接攻击改变情感；
            // 投射物、TNT 和枪械仍可造成伤害，但不会被误算成一次近战“教训”。
            if (player != null && isAimingAtMaid(player, maid) && !isSplashDamage(source)) {
                float damage = Math.max(amount, 0);
                int fearDelta = Math.min((int) (1 + damage * 1.5), 4);
                int trustDelta = Math.max(-(1 + (int) (damage * 0.5)), -2);

                EmotionData.EmotionValues old = EmotionData.get(maid, player.getUUID());
                EmotionData.addFear(maid, player.getUUID(), fearDelta);
                EmotionData.addTrust(maid, player.getUUID(), trustDelta);
                EmotionData.EmotionValues now = EmotionData.get(maid, player.getUUID());

                MaidResponder.debug(player,
                        "§e[情感] 教训女仆(mixin) → 信任 " + trustDelta +
                        " (" + old.trust() + "→" + now.trust() + "), 恐惧 " + fearDelta +
                        " (" + old.fear() + "→" + now.fear() + ")");

                if (LazyMaidHitHandler.isLazyMode(maid)) {
                    LazyMaidHitHandler.triggerEscape(maid, player);
                } else {
                    EmotionActiveDialogue.tryInteractDialogue(maid, player);
                }
            }
        }

        // 主人伤害：保留原始 DamageSource，跳过 EntityMaid 的友伤压缩，
        // 并在同一次原生 LivingEntity 结算中临时放行所有保护。
        if (isOwnerAttack && OwnerDamageSource.isEnabled()) {
            float rawDamage = OwnerDamageContext.normalizeDamage(amount);
            OwnerDamageContext.begin(maid, source, rawDamage);
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

    @Inject(method = "die", at = @At("HEAD"))
    private void callresponse$trackOwnerDamageDeath(DamageSource source, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (OwnerDamageContext.hasActiveDamage(maid, source)) {
            OwnerDamageContext.markDeathStarted(maid, source);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void callresponse$sanitizeMovementSave(CompoundTag tag, CallbackInfo ci) {
        MaidMovementControl.sanitizeSave((EntityMaid) (Object) this, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void callresponse$recoverMovementOnLoad(CompoundTag tag, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        MaidPathRepair.cleanupKnownSpeedPollution(maid,
                maid.getPersistentData().contains(MaidMovementControl.ROOT_KEY, Tag.TAG_COMPOUND), true);
        MaidMovementControl.recoverOnLoad(maid);
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
