package com.github.tartaricacid.callresponse.compat.hunger;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class HungerManager {

    // ===== 饱食度衰减 =====
    private static final int HUNGER_DECAY_INTERVAL = 600; // 30秒减1点
    private static final float HUNGER_DECAY_AMOUNT = 1.0f;

    // ===== 伤害 =====
    private static final int DAMAGE_INTERVAL = 20;  // 1秒
    private static final float DAMAGE_AMOUNT = 2.0f;

    // ===== 移速修改 =====
    private static final float BASE_SPEED = 0.65f;
    private static final float SPEED_PENALTY_HIGH = -0.3f;
    private static final float SPEED_PENALTY_MEDIUM = -0.1f;
    private static final float SPEED_BONUS = 0.2f;

    // ===== 回血（原有区间回血） =====
    private static final int HEAL_INTERVAL = 20;   // 1秒
    private static final float HEAL_AMOUNT = 1f;

    // ===== 新增：消耗饥饿值回血 =====
    private static final int CONSUMPTION_HEAL_INTERVAL = 40;   // 2秒
    private static final float HUNGER_COST = 2.0f;             // 消耗饥饿值
    private static final float CONSUMPTION_HEAL_AMOUNT = 1.0f; // 恢复生命值

    // ===== 自动进食 =====
    private static final float HUNGER_THRESHOLD = 35f;
    private static final long EAT_COOLDOWN_TICKS = 20 * 2; // 1分钟

    // ===== 对话冷却 =====
    private static final Map<UUID, Long> lastHungerTalkTime = new HashMap<>();
    private static final String OVERFED_DEATH_TAG = "OverfedDeath";

    // 女仆上次自动进食时间
    private static final Map<UUID, Long> lastAutoEatTime = new HashMap<>();

    // ===== 同步饱食度到客户端 =====
    public static void syncHungerToClient(EntityMaid maid) {
        if (maid.level().isClientSide) return;
        int hunger = (int) Math.round(HungerData.get(maid));
        SyncHungerPacket packet = new SyncHungerPacket(maid.getUUID(), hunger);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(maid, packet);
    }

    // ===== 监听女仆吃东西（玩家喂食或其他方式触发） =====
    @SubscribeEvent
    public void onMaidEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;
        if (!maid.isTame() || maid.getOwner() == null) return;

        ItemStack stack = event.getItem();
        FoodProperties food = stack.getFoodProperties(maid);
        if (food == null) return;

        int nutrition = food.nutrition();
        float hungerGain = nutrition;

        float oldHunger = HungerData.get(maid);
        HungerData.add(maid, hungerGain);
        float newHunger = HungerData.get(maid);

        LivingEntity ownerEntity = maid.getOwner();
        if (ownerEntity instanceof ServerPlayer serverPlayer) {
            UUID playerId = serverPlayer.getUUID();
            EmotionData.addTrustFloat(maid, playerId, 0.5f);
            EmotionData.addFearFloat(maid, playerId, -0.5f);

            MaidResponder.debug(serverPlayer, "\u001b[e[压力] " + maid.getCustomName() + " 吃了 " + stack.getDisplayName().getString() +
                    "饥饿度 " + oldHunger + " → " + newHunger + "信任度 +0.5恐惧度 -0.5");
        }

        applySpeedEffect(maid);
        syncHungerToClient(maid);
    }

    // ===== 定时处理 =====
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        if (!maid.isTame() || maid.getOwner() == null) return;
                        if (!maid.isAlive()) return;

                        long tick = maid.level().getGameTime();
                        boolean hungerChanged = false;

                        // 1. 饱食度衰减
                        if (tick % HUNGER_DECAY_INTERVAL == 0) {
                            float current = HungerData.get(maid);
                            if (current > 0) {
                                HungerData.add(maid, -HUNGER_DECAY_AMOUNT);
                                hungerChanged = true;
                            }
                        }

                        // 2. 伤害处理
                        if (tick % DAMAGE_INTERVAL == 0) {
                            float hunger = HungerData.get(maid);
                            DamageSource damageSource = maid.damageSources().starve();

                            if (hunger <= 9) {
                                maid.hurt(damageSource, DAMAGE_AMOUNT);
                            } else if (hunger >= 91) {
                                maid.getPersistentData().putBoolean(OVERFED_DEATH_TAG, true);
                                maid.hurt(damageSource, DAMAGE_AMOUNT * 0.5f);
                            }
                        }

                        // 3. 回血（原有区间回血：饱食度 41~74，每秒回1）
                        if (tick % HEAL_INTERVAL == 0) {
                            float hunger = HungerData.get(maid);
                            if (hunger >= 41 && hunger <= 74) {
                                if (maid.getHealth() < maid.getMaxHealth()) {
                                    maid.heal(HEAL_AMOUNT);
                                }
                            }
                        }

                        // ★ 新增：消耗饥饿值回血（未满血时，每2秒消耗2饥饿值恢复1生命）
                        if (tick % CONSUMPTION_HEAL_INTERVAL == 0) {
                            float hunger = HungerData.get(maid);
                            if (maid.getHealth() < maid.getMaxHealth() && hunger >= HUNGER_COST) {
                                // 消耗饥饿值
                                HungerData.add(maid, -HUNGER_COST);
                                // 恢复生命
                                maid.heal(CONSUMPTION_HEAL_AMOUNT);
                                hungerChanged = true;
                            }
                        }

                        // 4. 移速更新
                        if (tick % 20 == 0) {
                            applySpeedEffect(maid);
                        }

                        // 5. 自动进食（低于阈值且冷却已过）
                        float hunger = HungerData.get(maid);
                        if (hunger < HUNGER_THRESHOLD) {
                            UUID maidId = maid.getUUID();
                            Long lastEatTime = lastAutoEatTime.get(maidId);
                            if (lastEatTime == null || tick - lastEatTime >= EAT_COOLDOWN_TICKS) {
                                if (tryEatFoodFromBackpack(maid)) {
                                    lastAutoEatTime.put(maidId, tick);
                                    hungerChanged = true;
                                    // 进食后同步（但饱食度会在事件中增加，延迟一下）
                                }
                            }
                        }

                        if (hungerChanged) {
                            syncHungerToClient(maid);
                        }

                        // 6. 饱食度对话
                        if (tick % 200 == 0) {
                            float hungerLevel = HungerData.get(maid);
                            UUID maidId = maid.getUUID();
                            Long lastTime = lastHungerTalkTime.get(maidId);
                            long now = maid.level().getGameTime();

                            if (lastTime == null || now - lastTime > 1200) {
                                String prompt = null;
                                String suffix = EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
                                if (hungerLevel <= 9) {
                                    prompt = "你快要饿死了！胃痛得像刀割一样，视线都开始模糊了。" + suffix + " 请用你自己的话喊出你的绝望和痛苦，直接表达你的难受，30字左右。";
                                } else if (hungerLevel <= 25) {
                                    prompt = "你肚子咕咕叫，饿得有点发慌。" + suffix + " 请用你自己的话表达你的饥饿感，25字左右。";
                                } else if (hungerLevel >= 91) {
                                    prompt = "你吃得太撑了，肚子胀得难受，感觉食物都顶到嗓子眼了！" + suffix + " 请用你自己的话表达你的难受和后悔，25字左右。";
                                }

                                if (prompt != null) {
                                    MaidResponder.processBroadcast(player, Collections.singletonList(maid), prompt, false);
                                    lastHungerTalkTime.put(maidId, now);
                                }
                            }
                        }
                    });
        }
    }

    // ===== 尝试从背包中吃一个食物（模拟正常吃） =====
    // 逻辑完全对齐 TLM MaidWorkMealTask：
    // 1) 先查双手是否有食物，有就直接吃
    // 2) 都没食物就从背包取，默认放副手（双手都有东西时不干扰主手）
    private boolean tryEatFoodFromBackpack(EntityMaid maid) {
        // 检查主手是否有可食用的物品
        ItemStack mainHand = maid.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.getFoodProperties(maid) != null) {
            maid.startUsingItem(InteractionHand.MAIN_HAND);
            return true;
        }

        // 检查副手是否有可食用的物品
        ItemStack offHand = maid.getOffhandItem();
        if (!offHand.isEmpty() && offHand.getFoodProperties(maid) != null) {
            maid.startUsingItem(InteractionHand.OFF_HAND);
            return true;
        }

        // 手里没有食物，从背包搜索
        CombinedInvWrapper inv = maid.getAvailableBackpackInv();
        if (inv == null) return false;

        // 选择进食手：TLM 原版逻辑
        // - 默认副手（双手都有物品时不干扰主手）
        // - 有空手则用空手
        InteractionHand eatHand = InteractionHand.OFF_HAND;
        if (mainHand.isEmpty()) {
            eatHand = InteractionHand.MAIN_HAND;
        } else if (offHand.isEmpty()) {
            eatHand = InteractionHand.OFF_HAND;
        }
        ItemStack handItem = maid.getItemInHand(eatHand);

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            FoodProperties food = stack.getFoodProperties(maid);
            if (food == null) continue;

            // 取出一个食物
            ItemStack extracted = inv.extractItem(i, 1, false);
            if (!extracted.isEmpty()) {
                // 用 TLM 隐藏物品栏暂存进食手的原物品
                if (!handItem.isEmpty()) {
                    maid.memoryHandItemStack(handItem.copy());
                }
                // 设置食物到进食手并开始使用
                maid.setItemInHand(eatHand, extracted);
                maid.startUsingItem(eatHand);
                return true;
            }
        }
        return false;
    }

    // ===== 应用移速效果 =====
    private static void applySpeedEffect(EntityMaid maid) {
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        float hunger = HungerData.get(maid);
        float targetSpeed;

        if (hunger <= 9 || hunger >= 91) {
            targetSpeed = BASE_SPEED + SPEED_PENALTY_HIGH;
        } else if (hunger <= 25 || hunger >= 75) {
            targetSpeed = BASE_SPEED + SPEED_PENALTY_MEDIUM;
        } else if (hunger >= 41 && hunger <= 74) {
            targetSpeed = BASE_SPEED + SPEED_BONUS;
        } else {
            targetSpeed = BASE_SPEED;
        }

        targetSpeed = Math.max(0.05f, targetSpeed);
        speedAttr.setBaseValue(targetSpeed);
    }

    // ===== 获取饱食度描述 =====
    public static String getHungerDescription(EntityMaid maid) {
        float hunger = HungerData.get(maid);
        if (hunger <= 9) {
            return "你快要饿死了，急需食物！";
        } else if (hunger <= 25) {
            return "你非常饥饿，需要吃东西。";
        } else if (hunger <= 40) {
            return "你有点饿了，但还可以忍受。";
        } else if (hunger <= 74) {
            return "你感觉很饱，精力充沛。";
        } else if (hunger <= 90) {
            return "你吃得很饱，有点撑。";
        } else {
            return "你吃得太饱了，感觉要撑死了！";
        }
    }

    // ===== 自定义死亡消息（过饱死亡） =====
    @SubscribeEvent
    public void onMaidDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (!maid.getPersistentData().getBoolean(OVERFED_DEATH_TAG)) return;
        if (maid.getPersistentData().getBoolean("DevotedSacrifice")) return;

        if (!event.getSource().is(DamageTypes.STARVE) || HungerData.get(maid) < 91) {
            maid.getPersistentData().remove(OVERFED_DEATH_TAG);
            return;
        }

        event.setCanceled(true);
        maid.getPersistentData().remove(OVERFED_DEATH_TAG);

        String maidName = maid.getDisplayName().getString();
        Component deathMsg = Component.literal(maidName + "被撑死了");
        maid.level().players().forEach(p -> p.sendSystemMessage(deathMsg));
    }
}
