package com.github.tartaricacid.callresponse.compat.emotion.mixin;

import com.github.tartaricacid.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionActiveDialogue;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
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

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(EntityMaid.class)
public abstract class MixinEntityMaid extends Mob {
    private MixinEntityMaid() { super(null, null); }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void example$onHurtHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.level().isClientSide) return;

        Entity directEntity = source.getDirectEntity();

        // 背叛女仆攻击通报（始终执行）
        if (directEntity instanceof EntityMaid attackerMaid && EmotionBetrayalManager.isBetraying(attackerMaid)) {
            EmotionBetrayalManager.onVictimAttackedByBetrayer(maid, attackerMaid);
        }

        boolean isOwnerAttack = isOwnerAttackingMaid(maid, source);
        boolean isMaidAttack = directEntity instanceof EntityMaid;


        if (isOwnerAttack && !EmotionBetrayalManager.isBetraying(maid)) {
            ServerPlayer player = resolvePlayer(source);
            if (player == null || !isAimingAtMaid(player, maid) || isSplashDamage(source)) {
                return;
            }

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

            // ★ 10%概率 + 30秒冷却触发AI对话，见 EmotionActiveDialogue.java:69-76
            if (LazyMaidHitHandler.isLazyMode(maid)) {
                LazyMaidHitHandler.triggerEscape(maid, player);
            } else {
                EmotionActiveDialogue.tryInteractDialogue(maid, player);
            }
        }

        // 伤害绕过：主人故意攻击 / 女仆间攻击
        if (isOwnerAttack || isMaidAttack) {
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

    @Unique
    private boolean isOwnerAttackingMaid(EntityMaid maid, DamageSource source) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) return false;

        Entity entity = source.getEntity();
        if (entity instanceof Player player && player.getUUID().equals(ownerId)) return true;

        Entity direct = source.getDirectEntity();
        if (direct instanceof Player player && player.getUUID().equals(ownerId)) return true;
        if (direct instanceof Projectile proj && proj.getOwner() instanceof Player player && player.getUUID().equals(ownerId)) return true;
        if (direct instanceof PrimedTnt tnt && tnt.getOwner() instanceof Player player && player.getUUID().equals(ownerId)) return true;

        return false;
    }

    @Nullable
    @Unique
    private ServerPlayer resolvePlayer(DamageSource source) {
        Entity direct = source.getDirectEntity();

        if (source.getEntity() instanceof ServerPlayer player) return player;

        if (direct instanceof ServerPlayer player) return player;

        if (direct instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer player) return player;

        if (direct instanceof PrimedTnt tnt && tnt.getOwner() instanceof ServerPlayer player) return player;

        return null;
    }

    @Unique
    private boolean isSplashDamage(DamageSource source) {
        if (source.isIndirect()) return true;
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
