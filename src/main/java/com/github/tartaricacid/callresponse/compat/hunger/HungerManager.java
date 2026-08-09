package com.github.tartaricacid.callresponse.compat.hunger;

import com.github.tartaricacid.callresponse.compat.bauble.BaubleDetector;
import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HungerManager {

    // ===== 饱食度衰减 =====
    private static final int HUNGER_DECAY_INTERVAL = 600; // 30秒减1点
    private static final float HUNGER_DECAY_AMOUNT = 1.0f;

    // ===== 伤害 =====
    private static final int DAMAGE_INTERVAL = 20;  // 1秒
    private static final float DAMAGE_AMOUNT = 2.0f;

    // ===== 移速修改 =====
    private static final float BASE_SPEED = 0.65f;
    private static final float SPEED_PENALTY_HIGH = -0.15f;
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

    // ===== 暴食饰品：每20秒无条件尝试吃一次 =====
    private static final long MORE_EAT_EAT_COOLDOWN_TICKS = 20 * 20; // 20秒

    // ===== 禁食饰品：饥饿值锁定区间 =====
    private static final float NO_EAT_MIN_HUNGER = 10f;
    private static final float NO_EAT_MAX_HUNGER = 90f;

    // ===== 对话冷却 =====
    private static final Map<UUID, Long> lastHungerTalkTime = new HashMap<>();
    private static final String OVERFED_DEATH_TAG = "OverfedDeath";

    // 女仆上次自动进食时间
    private static final Map<UUID, Long> lastAutoEatTime = new HashMap<>();

    // ===== 功能1：低饱食度向附近同主人的女仆要食物 =====
    private static final float STEAL_HUNGER_THRESHOLD = 20f;
    private static final int STEAL_SEARCH_RADIUS = 8;
    private static final int STEAL_MAX_SEARCH = 3;
    private static final long STEAL_FAIL_COOLDOWN_TICKS = 20 * 60 * 10; // 10分钟
    // 记录"找女仆要食物失败"的时间（成功吃到不记）
    private static final Map<UUID, Long> lastStealFailTime = new HashMap<>();

    // ===== 监听女仆吃东西（玩家喂食或其他方式触发） =====
    @SubscribeEvent
    public void onMaidEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;
        if (!maid.isTame() || maid.getOwner() == null) return;

        ItemStack stack = event.getItem();
        FoodProperties food = stack.getFoodProperties(maid);
        if (food == null) return;

        int nutrition = food.getNutrition();

        float oldHunger = HungerData.get(maid);
        HungerData.add(maid, (float) nutrition);
        if (BaubleDetector.hasNoEat(maid) && HungerData.get(maid) > NO_EAT_MAX_HUNGER) {
            HungerData.add(maid, NO_EAT_MAX_HUNGER - HungerData.get(maid));
        }
        float newHunger = HungerData.get(maid);

        LivingEntity ownerEntity = maid.getOwner();
        if (ownerEntity instanceof ServerPlayer serverPlayer) {
            UUID playerId = serverPlayer.getUUID();
            EmotionData.addTrust(maid, playerId, 1);
            EmotionData.addFear(maid, playerId, -1);
            MaidResponder.debug(serverPlayer,
                    Component.literal("§e[压力] ")
                            .append(maid.getName())
                            .append(Component.literal(" 吃了 "))
                            .append(stack.getDisplayName()) // 直接使用 Component
                            .append(Component.literal("饥饿度 " + oldHunger + " → " + newHunger + "信任度 +1恐惧度 -1"))
            );
        }

        applySpeedEffect(maid);
    }

    // ===== 定时处理 =====
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        if (!maid.isTame() || maid.getOwner() == null) return;
                        if (!maid.isAlive()) return;

                        long tick = maid.level().getGameTime();

                        // 1. 饱食度衰减
                        if (tick % HUNGER_DECAY_INTERVAL == 0) {
                            float current = HungerData.get(maid);
                            if (current > 0) {
                                HungerData.add(maid, -HUNGER_DECAY_AMOUNT);
                            }
                        }

                        // 2. 伤害处理
                        if (tick % DAMAGE_INTERVAL == 0) {
                            float hunger = HungerData.get(maid);
                            DamageSource damageSource = maid.damageSources().starve();

                            if (hunger <= 9) {
                                maid.hurt(damageSource, DAMAGE_AMOUNT);
                            } else if (hunger >= 91 && !BaubleDetector.hasMoreEat(maid)) {
                                // 暴食饰品免疫吃撑伤害
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
                            }
                        }

                        // 5. 移速更新
                        if (tick % 20 == 0) {
                            applySpeedEffect(maid);
                        }

                        // 6. 自动进食（禁食饰品不主动吃；暴食饰品每20秒无条件吃一次；否则饥饿低于阈值且冷却已过才吃）
                        float hunger = HungerData.get(maid);
                        boolean noEat = BaubleDetector.hasNoEat(maid);
                        boolean moreEat = BaubleDetector.hasMoreEat(maid);
                        boolean wantEat = moreEat || hunger < HUNGER_THRESHOLD;
                        long eatCooldown = moreEat ? MORE_EAT_EAT_COOLDOWN_TICKS : EAT_COOLDOWN_TICKS;
                        if (!noEat && wantEat) {
                            UUID maidId = maid.getUUID();
                            Long lastEatTime = lastAutoEatTime.get(maidId);
                            if (lastEatTime == null || tick - lastEatTime >= eatCooldown) {
                                if (tryEatFoodFromBackpack(maid)) {
                                    lastAutoEatTime.put(maidId, tick);
                                    // 进食后同步（但饱食度会在事件中增加，延迟一下）
                                }
                            }
                        }

                        // 功能1：低饱食度(<20)且自己背包/手上确实没有食物时，去找附近同主人的女仆借食物（禁食饰品不触发）
                        if (!noEat && hunger < STEAL_HUNGER_THRESHOLD && tick % 20 == 0 && !hasAnyFoodOfOwn(maid)) {
                            tryStealFoodFromNearbyMaid(maid, tick);
                        }

                        // 7. 饱食度对话
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

    // ===== 功能1：低饱食度向附近同主人的女仆要食物 =====
    // 每次重新搜索离自己最近、且没搜索过的女仆，走过去检查（物品栏+双手）；
    // 对方有食物就随机拿一个并吃下，直接结束讨食恢复正常（10 分钟 CD）；
    // 没食物就排除再找下一个，最多 3 个；都没找到则信任-1 + AI 对话，同样 10 分钟 CD 后再次尝试。
    // 讨食期间会临时关闭 TLM 的跟随任务（防止中途被拉回主人身边），讨食完成后恢复跟随
    private static final Map<UUID, StealState> stealStates = new HashMap<>();
    private static final double STEAL_ARRIVE_DISTANCE = 2.5;
    private static final long STEAL_WALK_TIMEOUT = 20 * 15; // 走向某个女仆超过15秒视为找不到

    private static class StealState {
        final Set<UUID> searched = new HashSet<>(); // 已搜索/已排除的女仆
        UUID currentTarget = null;                  // 正在走去的女仆
        long walkStartTick = 0;                     // 开始走向当前目标的时间
        boolean wasFollowing = false;               // 讨食前是否处于跟随模式（结束后要恢复）
    }

    private void tryStealFoodFromNearbyMaid(EntityMaid maid, long tick) {
        UUID maidId = maid.getUUID();
        // 讨食 CD：无论讨到还是没讨到，结束后 10 分钟内不再触发
        Long lastFail = lastStealFailTime.get(maidId);
        if (lastFail != null && tick - lastFail < STEAL_FAIL_COOLDOWN_TICKS) {
            return;
        }

        StealState state = stealStates.get(maidId);
        if (state == null) {
            state = new StealState();
            // 讨食期间禁止 TLM 跟随任务把女仆拉回玩家身边：临时开启 home mode
            state.wasFollowing = !maid.isHomeModeEnable();
            if (state.wasFollowing) {
                maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
                maid.setHomeModeEnable(true);
            }
            stealStates.put(maidId, state);
        }

        // 逐个排除搜索，最多 3 个
        while (state.searched.size() < STEAL_MAX_SEARCH) {
            // 重新搜索离自己最近且未搜索过的女仆（必须排除搜索过的，否则会原地搜同一个）
            if (state.currentTarget == null) {
                EntityMaid nearest = searchNearestMaid(maid, state.searched);
                if (nearest == null) {
                    // 附近已经没有未搜索过的女仆
                    failStealFood(maid, tick);
                    return;
                }
                state.currentTarget = nearest.getUUID();
                state.walkStartTick = 0;
            }

            EntityMaid target = findMaidByUuid(maid, state.currentTarget);
            if (target == null || !target.isAlive() || !target.isOwnedBy(maid.getOwner())) {
                // 目标消失/死亡/不再同主人，排除并重搜
                state.searched.add(state.currentTarget);
                state.currentTarget = null;
                continue;
            }

            // 还没走到目标旁边：走过去（超过15秒视为这个女仆找不到）
            if (!maid.closerThan(target, STEAL_ARRIVE_DISTANCE)) {
                if (state.walkStartTick == 0) {
                    state.walkStartTick = tick;
                } else if (tick - state.walkStartTick > STEAL_WALK_TIMEOUT) {
                    state.searched.add(state.currentTarget);
                    state.currentTarget = null;
                    continue;
                }
                BlockPos targetPos = target.blockPosition();
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(targetPos), 0.7f, 1));
                return; // 正在走路，等下一轮
            }

            // 已走到：检查对方双手+物品栏有没有食物，有就随机拿一个并吃下
            if (stealRandomFoodFrom(target, maid)) {
                tryEatFoodFromBackpack(maid);
                lastAutoEatTime.put(maidId, tick);
                // 讨到了：直接结束整个讨食流程，记 10 分钟 CD（吃完前不会再来讨食）
                lastStealFailTime.put(maidId, tick);
                finishStealFood(maid);
                return;
            }

            // 这个女仆没有食物，排除，重新搜索下一个最近的
            state.searched.add(state.currentTarget);
            state.currentTarget = null;
        }

        // 3 个都找过且都没有食物：失败处理
        failStealFood(maid, tick);
    }

    // 搜索离自己最近、同主人、且未搜索过的女仆
    private static EntityMaid searchNearestMaid(EntityMaid maid, Set<UUID> searched) {
        return maid.level().getEntitiesOfClass(EntityMaid.class,
                        maid.getBoundingBox().inflate(STEAL_SEARCH_RADIUS)).stream()
                .filter(EntityMaid::isAlive)
                .filter(EntityMaid::isTame)
                .filter(other -> !other.getUUID().equals(maid.getUUID()))
                .filter(other -> !searched.contains(other.getUUID()))
                .filter(other -> maid.getOwner() != null && other.isOwnedBy(maid.getOwner()))
                .min(Comparator.comparingDouble(other -> other.distanceToSqr(maid)))
                .orElse(null);
    }

    private static EntityMaid findMaidByUuid(EntityMaid maid, UUID uuid) {
        for (EntityMaid maidEntity : maid.level().getEntitiesOfClass(EntityMaid.class,
                maid.getBoundingBox().inflate(STEAL_SEARCH_RADIUS * 2))) {
            if (maidEntity.getUUID().equals(uuid)) {
                return maidEntity;
            }
        }
        return null;
    }

    // 讨食结束（成功或失败）：恢复 TLM 跟随主人的状态
    private static void finishStealFood(EntityMaid maid) {
        StealState state = stealStates.remove(maid.getUUID());
        if (state != null && state.wasFollowing) {
            maid.restrictTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
            maid.setHomeModeEnable(false);
        }
    }

    // 讨食失败：信任-1 + 触发一次对话（强调找了好几个都没有吃的），10 分钟后再尝试
    private void failStealFood(EntityMaid maid, long tick) {
        UUID maidId = maid.getUUID();
        finishStealFood(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        LivingEntity ownerEntity = maid.getOwner();
        if (ownerEntity instanceof ServerPlayer serverPlayer) {
            UUID ownerId = serverPlayer.getUUID();
            EmotionData.addTrust(maid, ownerId, -1);
            String suffix = EmotionData.getTendencyPromptSuffix(maid, ownerId);
            String prompt = "你饿得头昏眼花，跑去找身边的女仆借食物，可是接连找了好几个女仆，她们都没有多余的食物，你空手而归，又饿又委屈。" + suffix
                    + " 请用你自己的话诉说你这趟借食白跑一趟的失落和委屈，40字左右。";
            MaidResponder.processBroadcast(serverPlayer, Collections.singletonList(maid), prompt, false);
        }
        lastStealFailTime.put(maidId, tick);
    }

    // 从指定女仆双手+物品栏里随机拿一个食物放到当前女仆背包
    private boolean stealRandomFoodFrom(EntityMaid target, EntityMaid maid) {
        CombinedInvWrapper inv = target.getAvailableInv(true);
        List<Integer> foodSlots = new ArrayList<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getFoodProperties(target) != null) {
                foodSlots.add(i);
            }
        }
        if (foodSlots.isEmpty()) {
            return false;
        }
        int slot = foodSlots.get(maid.getRandom().nextInt(foodSlots.size()));
        ItemStack extracted = inv.extractItem(slot, 1, false);
        if (extracted.isEmpty()) {
            return false;
        }
        ItemStack rest = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), extracted, false);
        if (!rest.isEmpty()) {
            // 自己背包放不下则归还对方
            target.getAvailableBackpackInv().insertItem(slot, rest, false);
            return false;
        }
        return true;
    }

    // 检查自己手上/背包里是否还有食物可用
    private static boolean hasAnyFoodOfOwn(EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.getFoodProperties(maid) != null) {
            return true;
        }
        ItemStack offHand = maid.getOffhandItem();
        if (!offHand.isEmpty() && offHand.getFoodProperties(maid) != null) {
            return true;
        }
        CombinedInvWrapper inv = maid.getAvailableBackpackInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getFoodProperties(maid) != null) {
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
