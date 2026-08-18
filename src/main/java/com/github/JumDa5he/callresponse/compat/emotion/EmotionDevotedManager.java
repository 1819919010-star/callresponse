package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.api.event.emotion.MaidEmotionEvent;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.hunt.HuntRawHealth;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionDevotedManager {

    // ===== 配置参数 =====
    private static final int TRUST_THRESHOLD = 80;
    private static final int FEAR_THRESHOLD = 80;
    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private static final int DIALOGUE_COOLDOWN_TICKS = 400;
    private static final int HEAL_COOLDOWN_TICKS = 1200;
    private static final int HEAL_DELAY_TICKS = 20;
    private static final double OWNER_LOW_HP_THRESHOLD = 0.1;
    private static final double STEAL_CHANCE = 0.6;
    private static final float ATTACK_BONUS = 5.0f;
    private static final float SPEED_BONUS = 0.1f;
    // ===== 减伤系数 =====
    private static final float DAMAGE_REDUCTION = 0.7f; // 受到 70% 伤害，即减免 30%

    // ===== 状态存储 =====
    private static final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastDialogueTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastHealTime = new ConcurrentHashMap<>();
    private static final Set<UUID> devotedMaids = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, PendingHeal> pendingHeals = new ConcurrentHashMap<>();

    private static class PendingHeal {
        final int startTick;
        final ServerPlayer owner;
        PendingHeal(int startTick, ServerPlayer owner) {
            this.startTick = startTick;
            this.owner = owner;
        }
    }

    // ===== 检查死忠状态 =====
    public static boolean isDevoted(EntityMaid maid, ServerPlayer player) {
        if(NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidDevotedEvent(maid)).isCanceled())return false;
        if (!maid.isTame() || maid.getOwner() == null) return false;
        if (EmotionBetrayalManager.isBetraying(maid)) return false;
        EmotionData.EmotionValues values = EmotionData.get(maid, player);
        return values.trust() >= TRUST_THRESHOLD && values.fear() >= FEAR_THRESHOLD;
    }

    private static ServerPlayer getOwnerAsPlayer(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner instanceof ServerPlayer player) return player;
        return null;
    }

    // ===== 属性加成/移除 =====
    private static final ResourceLocation DEVOTED_ATTACK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "devoted_attack");
    private static final ResourceLocation DEVOTED_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "devoted_speed");

    private static void applyDevotedBuffs(EntityMaid maid) {
        AttributeInstance attackAttr = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attackAttr != null) {
            attackAttr.removeModifier(DEVOTED_ATTACK_MODIFIER_ID);
            attackAttr.addPermanentModifier(new AttributeModifier(
                    DEVOTED_ATTACK_MODIFIER_ID,
                    ATTACK_BONUS,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
        if (speedAttr != null) {
            speedAttr.removeModifier(DEVOTED_SPEED_MODIFIER_ID);
            speedAttr.addPermanentModifier(new AttributeModifier(
                    DEVOTED_SPEED_MODIFIER_ID,
                    SPEED_BONUS,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    private static void removeDevotedBuffs(EntityMaid maid) {
        AttributeInstance attackAttr = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attackAttr != null) {
            attackAttr.removeModifier(DEVOTED_ATTACK_MODIFIER_ID);
        }
        if (speedAttr != null) {
            speedAttr.removeModifier(DEVOTED_SPEED_MODIFIER_ID);
        }
    }

    // ===== 定时驱动 =====
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        if (!maid.isTame() || maid.getOwner() == null) return;
                        if (!maid.isAlive()) return;

                        boolean isDevoted = isDevoted(maid, player);
                        UUID maidId = maid.getUUID();
                        boolean wasDevoted = devotedMaids.contains(maidId);

                        if (isDevoted && !wasDevoted) {
                            devotedMaids.add(maidId);
                            applyDevotedBuffs(maid);
                        } else if (!isDevoted && wasDevoted) {
                            devotedMaids.remove(maidId);
                            removeDevotedBuffs(maid);
                        }

                        if (!isDevoted) return;

                        // 狩猎中的女仆专注狩猎：跳过攻击背叛女仆和给主人补血
                        // （否则打残主人时会触发补血回满主人并献祭女仆自己）
                        if (com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager.isHunting(maid)) {
                            return;
                        }

                        attackNearbyBetrayers(maid, player);
                        healOwnerIfNeeded(maid, player);
                    });
        }
    }

    // ===== 攻击背叛女仆 =====
    private static void attackNearbyBetrayers(EntityMaid maid, ServerPlayer owner) {
        UUID maidId = maid.getUUID();
        long now = maid.level().getGameTime();

        Long lastAttack = lastAttackTime.get(maidId);
        if (lastAttack != null && now - lastAttack < ATTACK_COOLDOWN_TICKS) {
            return;
        }

        AABB box = maid.getBoundingBox().inflate(16);
        List<EntityMaid> betrayers = maid.level().getEntitiesOfClass(EntityMaid.class, box,
                m -> m != maid && m.isAlive() && EmotionBetrayalManager.isBetraying(m));

        if (betrayers.isEmpty()) {
            maid.setTarget(null);
            return;
        }

        betrayers.sort(Comparator.comparingDouble(maid::distanceToSqr));
        EntityMaid target = betrayers.get(0);

        if (maid.isInSittingPose()) {
            maid.setInSittingPose(false);
        }

        double distance = maid.distanceTo(target);
        if (distance > 2.0) {
            maid.getBrain().setMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(
                            new net.minecraft.world.entity.ai.behavior.BlockPosTracker(target.blockPosition()),
                            0.8f, 1
                    )
            );
            return;
        }

        maid.swing(InteractionHand.MAIN_HAND);
        float healthBefore = target.getHealth();
        DamageSource source = target.damageSources().mobAttack(maid);
        target.invulnerableTime = 0;
        maid.doHurtTarget(target);

        // 同主人保护、饰品或其他事件可能让 doHurtTarget 表面成功但实际不扣血。
        // 仅在生命值完全没变时补一次基础攻击伤害，正常原生伤害不会被重复结算。
        if (target.isAlive() && target.getHealth() >= healthBefore) {
            float fallbackDamage = Math.max(1.0F,
                    (float) maid.getAttributeValue(Attributes.ATTACK_DAMAGE));
            float healthAfter = Math.max(0.0F, healthBefore - fallbackDamage);
            HuntRawHealth.write(target, healthAfter);
            target.hurtTime = target.hurtDuration = 10;
            target.hurtMarked = true;
            target.level().broadcastDamageEvent(target, source);
            target.knockback(0.4D, maid.getX() - target.getX(), maid.getZ() - target.getZ());
            target.hurtMarked = true;
            if (healthAfter <= 0.0F) {
                target.die(source);
            }
        }
        lastAttackTime.put(maidId, now);

        if (maid.getRandom().nextDouble() < STEAL_CHANCE) {
            stealFromBetrayer(maid, target);
        }

        Long lastDialogue = lastDialogueTime.get(maidId);
        if (lastDialogue == null || now - lastDialogue >= DIALOGUE_COOLDOWN_TICKS) {
            String prompt = "你看到背叛主人的女仆，作为一只对主人死心塌地的忠犬，你瞬间被怒火吞没。背叛主人是不可饶恕的罪——你愿意为主人去死，而那个叛徒居然敢伤害主人的心！请用充满杀气的话警告叛徒她的下场，并宣誓你对主人至死不渝的忠诚。你的语气里要有'你敢碰他一下我就要你命'的决绝。";
            MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
            lastDialogueTime.put(maidId, now);
        }
    }

    // ===== 从背叛女仆身上夺取盔甲/饰品 =====
    private static void stealFromBetrayer(EntityMaid attacker, EntityMaid target) {
        List<ItemStack> candidates = new ArrayList<>();

        // ===== 1. 收集盔甲 =====
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = target.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                candidates.add(stack);
            }
        }

        // ===== 2. 收集饰品 =====
        BaubleItemHandler baubles = target.getMaidBauble();
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (!stack.isEmpty()) {
                candidates.add(stack);
            }
        }

        if (candidates.isEmpty()) return;

        ItemStack selected = candidates.get(attacker.getRandom().nextInt(candidates.size()));
        if (selected.isEmpty()) return;

        boolean removed = false;

        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack == selected) {
                target.setItemSlot(slot, ItemStack.EMPTY);
                removed = true;
                break;
            }
        }

        if (!removed) {
            for (int i = 0; i < baubles.getSlots(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (stack == selected) {
                    baubles.setStackInSlot(i, ItemStack.EMPTY);
                    removed = true;
                    break;
                }
            }
        }

        if (!removed) return;

        ItemStack remaining = ItemHandlerHelper.insertItemStacked(attacker.getMaidInv(), selected, false);
        if (!remaining.isEmpty()) {
            attacker.spawnAtLocation(remaining);
        }
    }

    // ===== 主人补血逻辑 =====
    private static void healOwnerIfNeeded(EntityMaid maid, ServerPlayer owner) {
        UUID maidId = maid.getUUID();
        long now = maid.level().getGameTime();

        // 主人已死亡：绝不能给尸体回血（会把死亡状态改乱，导致无法正常复活）
        if (!owner.isAlive()) {
            return;
        }

        Long lastHeal = lastHealTime.get(maidId);
        if (lastHeal != null && now - lastHeal < HEAL_COOLDOWN_TICKS) {
            return;
        }

        float maxHealth = owner.getMaxHealth();
        float currentHealth = owner.getHealth();
        if (currentHealth / maxHealth > OWNER_LOW_HP_THRESHOLD) {
            return;
        }

        if (maid.isInSittingPose()) {
            maid.setInSittingPose(false);
        }

        double distance = maid.distanceTo(owner);
        if (distance > 16.0) {
            BlockPos ownerPos = owner.blockPosition();
            maid.teleportTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
        } else if (distance > 2.0) {
            maid.getBrain().setMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(
                            new net.minecraft.world.entity.ai.behavior.BlockPosTracker(owner.blockPosition()),
                            0.8f, 1
                    )
            );
            return;
        }

        float healAmount = maxHealth - currentHealth;
        if (healAmount <= 0) return;

        owner.setHealth(maxHealth);

        float maidNewHealth = maid.getHealth() - healAmount - 1.0f;

        if (maidNewHealth <= 0) {
            maid.getPersistentData().putBoolean("DevotedSacrifice", true);
            maid.getPersistentData().putInt("DevotedSacrificeTick", maid.tickCount);
            maid.hurt(maid.damageSources().generic(), maid.getHealth());
        } else {
            maid.setHealth(maidNewHealth);
            maid.hurt(maid.damageSources().generic(), 0.01f);
        }

        lastHealTime.put(maidId, now);

        String prompt = "主人生命值过低！你的心脏骤然收紧——那是比你自己受伤还要痛苦的感受。作为一只愿意为主人去死的女仆，你毫不犹豫地决定把自己的生命力渡给主人。请用一句简短而坚定的话表达你的决心，语气里要有'把我的命拿去'的坦然。你不需要犹豫，因为对主人忠诚就是你存在的全部意义。";
        MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
    }

    // ===== ★ 新增：忠诚女仆减伤被动 =====
    @SubscribeEvent
    public void onMaidHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        UUID maidId = maid.getUUID();

        // 检查是否为忠诚状态
        if (!devotedMaids.contains(maidId)) return;

        // 如果是牺牲伤害（DevotedSacrifice），不减免，保证女仆能正常死亡
        if (maid.getPersistentData().getBoolean("DevotedSacrifice")) {
            return;
        }

        // 减免 30% 伤害
        float originalDamage = event.getNewDamage();
        float reducedDamage = originalDamage * DAMAGE_REDUCTION;
        event.setNewDamage(reducedDamage);
    }

    // ===== 死亡消息覆盖 =====
    @SubscribeEvent
    public void onMaidDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (!maid.getPersistentData().getBoolean("DevotedSacrifice")) return;

        int sacrificeTick = maid.getPersistentData().getInt("DevotedSacrificeTick");
        if (!event.getSource().is(DamageTypes.GENERIC) || maid.tickCount - sacrificeTick > 1) {
            maid.getPersistentData().remove("DevotedSacrifice");
            maid.getPersistentData().remove("DevotedSacrificeTick");
            return;
        }

        event.setCanceled(true);
        maid.getPersistentData().remove("DevotedSacrifice");
        maid.getPersistentData().remove("DevotedSacrificeTick");

        String maidName = maid.getDisplayName().getString();
        Component deathMsg = Component.literal(maidName + "为主人而献身");
        maid.level().players().forEach(p -> p.sendSystemMessage(deathMsg));

        maid.setHealth(0);
        maid.die(maid.damageSources().generic());
    }
}
