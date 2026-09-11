package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.JumDa5he.callresponse.compat.bauble.BaubleDetector;
import com.github.JumDa5he.callresponse.compat.brain.SeekFoodBehavior;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.npc.NpcEventManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
    private static final String HUNGER_DECAY_PROGRESS_TAG = "CallResponseHungerDecayProgress";
    private static final String HUNGER_DECAY_LAST_TICK_TAG = "CallResponseHungerDecayLastTick";

    // ===== 伤害 =====
    private static final int DAMAGE_INTERVAL = 20;  // 1秒
    private static final float STARVATION_DAMAGE_AMOUNT = 1.0f;
    private static final float OVERFED_DAMAGE_AMOUNT = 1.0f;

    // ===== 移速修改 =====
    private static final float BASE_SPEED = 0.65f;
    private static final float SPEED_PENALTY_HIGH = -0.15f;
    private static final float SPEED_PENALTY_MEDIUM = -0.1f;
    private static final float SPEED_BONUS = 0.2f;
    public static final UUID SPEED_EFFECT_UUID = UUID.fromString("c6b6f7ee-df89-4d2f-aec8-1f1471691142");

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

    // ===== 禁食饰品：饥饿值锁定上限 =====
    private static final float NO_EAT_MAX_HUNGER = 90f;

    // ===== 对话冷却 =====
    private static final Map<UUID, Long> lastHungerTalkTime = new HashMap<>();
    private static final String OVERFED_DEATH_TAG = "OverfedDeath";

    // 女仆上次自动进食时间
    private static final Map<UUID, Long> lastAutoEatTime = new HashMap<>();

    // 女仆上次暴食饰品额外进食时间（与正常自动进食冷却互相独立）
    private static final Map<UUID, Long> lastMoreEatTime = new HashMap<>();

    // 每只女仆吃东西改变信任/恐惧的冷却：1分钟
    private static final long EAT_EMOTION_COOLDOWN_TICKS = 20 * 60;
    private static final Map<UUID, Long> lastEatEmotionChangeTime = new HashMap<>();

    // ===== 功能1：低饱食度向附近同主人的女仆要食物 =====
    private static final float STEAL_HUNGER_THRESHOLD = 20f;
    private static final int STEAL_SEARCH_RADIUS = 8;
    private static final int STEAL_MAX_SEARCH = 3;
    private static final long STEAL_FAIL_COOLDOWN_TICKS = 20 * 60 * 10; // 10分钟
    private static final long STEAL_EMPTY_RETRY_TICKS = 20 * 5; // 周围没人时5秒后重新搜索
    // 记录"找女仆要食物失败"的时间（成功吃到不记）
    private static final Map<UUID, Long> lastStealFailTime = new HashMap<>();
    private static final Map<UUID, Long> lastStealEmptySearchTime = new HashMap<>();

    // ===== 监听女仆吃东西（玩家喂食或其他方式触发） =====
    @SubscribeEvent
    public void onMaidEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;
        if (!maid.isTame() || maid.getOwner() == null) return;

        ItemStack stack = event.getItem();
        FoodProperties food = stack.getFoodProperties(maid);
        if (food == null) return;

        // NPC 日常事件只旁路记录已吃下的物品种类，不改变原进食流程。
        NpcEventManager.recordFood(maid, stack);

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
            UUID maidId = maid.getUUID();
            long gameTime = maid.level().getGameTime();
            Long lastChangeTime = lastEatEmotionChangeTime.get(maidId);
            boolean emotionChanged = lastChangeTime == null
                    || gameTime < lastChangeTime
                    || gameTime - lastChangeTime >= EAT_EMOTION_COOLDOWN_TICKS;
            if (emotionChanged) {
                EmotionData.addTrust(maid, playerId, 1);
                EmotionData.addFear(maid, playerId, -1);
                lastEatEmotionChangeTime.put(maidId, gameTime);
            }
            MaidResponder.debug(serverPlayer,
                    Component.translatable("message.callresponse.debug.stress_prefix")
                            .append(maid.getName())
                            .append(Component.translatable("message.callresponse.debug.ate_separator"))
                            .append(stack.getDisplayName()) // 直接使用 Component
                            .append(Component.translatable(emotionChanged
                                    ? "message.callresponse.debug.ate_status_changed"
                                    : "message.callresponse.debug.ate_status", oldHunger, newHunger))
            );
        }

        applySpeedEffect(maid);
    }

    // ===== 定时处理 =====
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMaidStoredAsItem(MaidAndItemTransformEvent.ToItem event) {
        EntityMaid maid = event.getMaid();
        if (!isBeggingForFood(maid)) {
            return;
        }

        // 魂符/相机保存实体数据之前，必须撤销讨食期间的临时居家状态与日程位置。
        // TLM 在发出该事件前已经保存过一次，因此恢复后重新写入同一个 NBT。
        finishStealFood(maid);
        maid.saveWithoutId(event.getData());
    }

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

                        // 饱食度超过 90 连续 3 秒后锁住所有主动进食；低于 85 才解除。
                        HungerEatingGuard.tick(maid, tick);

                        // 1. 饱食度衰减
                        if (advanceHungerDecayClock(maid, tick)) {
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
                                maid.hurt(damageSource, STARVATION_DAMAGE_AMOUNT);
                            } else if (hunger >= 91 && !BaubleDetector.hasMoreEat(maid)) {
                                // 暴食饰品免疫吃撑伤害
                                maid.getPersistentData().putBoolean(OVERFED_DEATH_TAG, true);
                                maid.hurt(damageSource, OVERFED_DAMAGE_AMOUNT);
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
                            if (maid.getHealth() < maid.getMaxHealth() && hunger >= 10.0f) {
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

                        // 6. 自动进食（禁食饰品不主动吃；饥饿低于阈值按正常冷却主动吃；暴食饰品每20秒无条件额外吃一次，与正常进食互不干扰）
                        float hunger = HungerData.get(maid);
                        boolean noEat = HungerEatingGuard.isBlocked(maid);
                        boolean moreEat = BaubleDetector.hasMoreEat(maid);
                        if (!noEat) {
                            UUID maidId = maid.getUUID();
                            // 饥饿低于阈值：按正常冷却主动吃
                            if (hunger < HUNGER_THRESHOLD) {
                                Long lastEatTime = lastAutoEatTime.get(maidId);
                                if (lastEatTime == null || tick - lastEatTime >= EAT_COOLDOWN_TICKS) {
                                    if (tryEatFoodFromBackpack(maid)) {
                                        lastAutoEatTime.put(maidId, tick);
                                        // 进食后同步（但饱食度会在事件中增加，延迟一下）
                                    }
                                }
                            }
                            // 暴食饰品：每20秒额外无条件吃一次，不影响上面的正常进食
                            if (moreEat) {
                                Long lastMoreEatTimeTick = lastMoreEatTime.get(maidId);
                                if (lastMoreEatTimeTick == null || tick - lastMoreEatTimeTick >= MORE_EAT_EAT_COOLDOWN_TICKS) {
                                    if (tryEatFoodFromBackpack(maid)) {
                                        lastMoreEatTime.put(maidId, tick);
                                    }
                                }
                            }
                        }

                        // 功能1：低饱食度(<20)且自己背包/手上确实没有食物时，去找附近同主人的女仆借食物（禁食饰品不触发）
                        // 讨食期间每tick调用：需要每tick重设 WALK_TARGET 与活动范围，对抗 TLM 的 MaidAwaitTask/SchedulePos 干扰
                        if (!noEat && hunger < STEAL_HUNGER_THRESHOLD && !hasAnyFoodOfOwn(maid)
                                && !SeekFoodBehavior.isSeeking(maid)) {
                            tryStealFoodFromNearbyMaid(maid, tick);
                        } else if (stealStates.containsKey(maid.getUUID())) {
                            // 中途被喂食、装上禁食饰品或饥饿值恢复时，立即结束讨食并恢复原状态。
                            finishStealFood(maid);
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
                                boolean suppressLowHungerTalk = BaubleDetector.hasNoEat(maid);
                                boolean suppressOverfedTalk = BaubleDetector.hasMoreEat(maid);
                                if (hungerLevel <= 9 && !suppressLowHungerTalk) {
                                    prompt = "你快要饿死了！胃痛得像刀割一样，视线都开始模糊了。" + suffix + " 请用你自己的话喊出你的绝望和痛苦，直接表达你的难受，30字左右。";
                                } else if (hungerLevel <= 25 && !suppressLowHungerTalk) {
                                    prompt = "你肚子咕咕叫，饿得有点发慌。" + suffix + " 请用你自己的话表达你的饥饿感，25字左右。";
                                } else if (hungerLevel >= 91 && !suppressOverfedTalk) {
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
        final EntityMaid maidInstance;
        final Set<UUID> excluded = new HashSet<>();
        UUID currentTarget = null;                  // 正在走去的女仆
        int inspectedCount = 0;
        double closestDistanceSqr = Double.MAX_VALUE;
        long lastProgressTick = 0;
        StealState(EntityMaid maid) {
            this.maidInstance = maid;
        }
    }

    /**
     * 用女仆自己的衰减进度替代全局整点判定。真实睡眠时不推进进度，醒来后接着睡前剩余时间。
     * LastTick 同时避免多人站在同一只女仆附近时，同一服务器 tick 被重复累计。
     */
    private static boolean advanceHungerDecayClock(EntityMaid maid, long tick) {
        var data = maid.getPersistentData();
        if (data.getLong(HUNGER_DECAY_LAST_TICK_TAG) == tick) return false;
        data.putLong(HUNGER_DECAY_LAST_TICK_TAG, tick);

        if (!data.contains(HUNGER_DECAY_PROGRESS_TAG)) {
            int inheritedProgress = (int) Math.floorMod(tick, HUNGER_DECAY_INTERVAL);
            data.putInt(HUNGER_DECAY_PROGRESS_TAG, inheritedProgress);
        }
        // 只认 TLM/原版真实 sleeping 状态；坐下和好吃懒做的地面睡眠不会进入这里。
        if (maid.isSleeping()) return false;

        int progress = data.getInt(HUNGER_DECAY_PROGRESS_TAG) + 1;
        if (progress >= HUNGER_DECAY_INTERVAL) {
            data.putInt(HUNGER_DECAY_PROGRESS_TAG, 0);
            return true;
        }
        data.putInt(HUNGER_DECAY_PROGRESS_TAG, progress);
        return false;
    }

    public static boolean isBeggingForFood(EntityMaid maid) {
        StealState state = stealStates.get(maid.getUUID());
        return state != null && state.maidInstance == maid;
    }

    private void tryStealFoodFromNearbyMaid(EntityMaid maid, long tick) {
        UUID maidId = maid.getUUID();
        // 讨食 CD：无论讨到还是没讨到，结束后 10 分钟内不再触发
        Long lastFail = lastStealFailTime.get(maidId);
        if (lastFail != null && tick - lastFail < STEAL_FAIL_COOLDOWN_TICKS) {
            return;
        }
        Long lastEmptySearch = lastStealEmptySearchTime.get(maidId);
        if (lastEmptySearch != null && tick >= lastEmptySearch && tick - lastEmptySearch < STEAL_EMPTY_RETRY_TICKS) {
            return;
        }

        StealState state = stealStates.get(maidId);
        if (state != null && state.maidInstance != maid) {
            stealStates.remove(maidId);
            state = null;
        }
        if (state == null) {
            // 先退出谈话并恢复真正的原日程，再让讨食系统保存自己的恢复点。
            TalkEventManager.leaveForFood(maid);
            state = new StealState(maid);
            MaidMovementControl.begin(maid, MaidMovementControl.Reason.BEGGING,
                    java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.SCHEDULE));
            stealStates.put(maidId, state);
        }

        // 逐个排除搜索，最多 3 个
        while (state.inspectedCount < STEAL_MAX_SEARCH) {
            // 重新搜索离自己最近且未搜索过的女仆（必须排除搜索过的，否则会原地搜同一个）
            if (state.currentTarget == null) {
                EntityMaid nearest = searchNearestMaid(maid, state.excluded);
                if (nearest == null) {
                    if (state.inspectedCount > 0) {
                        failStealFood(maid, tick);
                    } else {
                        lastStealEmptySearchTime.put(maidId, tick);
                        finishStealFood(maid);
                    }
                    return;
                }
                state.currentTarget = nearest.getUUID();
                state.closestDistanceSqr = maid.distanceToSqr(nearest);
                state.lastProgressTick = tick;
                clearStealNavigation(maid);
            }

            EntityMaid target = findMaidByUuid(maid, state.currentTarget);
            if (target == null || !target.isAlive() || !target.isOwnedBy(maid.getOwner())) {
                // 目标消失/死亡/不再同主人，排除并重搜
                excludeCurrentTarget(state);
                continue;
            }

            // 还没走到目标旁边：走过去（超过15秒视为这个女仆找不到）
            if (!maid.closerThan(target, STEAL_ARRIVE_DISTANCE)) {
                double distanceSqr = maid.distanceToSqr(target);
                if (distanceSqr + 0.25 < state.closestDistanceSqr) {
                    state.closestDistanceSqr = distanceSqr;
                    state.lastProgressTick = tick;
                } else if (tick < state.lastProgressTick || tick - state.lastProgressTick > STEAL_WALK_TIMEOUT) {
                    excludeCurrentTarget(state);
                    continue;
                }
                BlockPos targetPos = target.blockPosition();
                // 不改 TLM 的 home/schedule；统一控制器暂停 Await/Follow/SchedulePos 的竞争。
                // restriction 不是持久字段，结束时按原 home/schedule 语义重建。
                maid.restrictTo(targetPos, STEAL_SEARCH_RADIUS + 2);
                maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetPos));
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosTracker(targetPos), 0.7f, 1));
                maid.getNavigation().moveTo(target, 0.7d);
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
            state.inspectedCount++;
            excludeCurrentTarget(state);
        }

        // 3 个都找过且都没有食物：失败处理
        failStealFood(maid, tick);
    }

    private static void excludeCurrentTarget(StealState state) {
        if (state.currentTarget != null) {
            state.excluded.add(state.currentTarget);
        }
        state.currentTarget = null;
        state.closestDistanceSqr = Double.MAX_VALUE;
        state.lastProgressTick = 0;
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

    // 讨食结束（成功或失败）：统一恢复接管前的 restriction，并清理路径。
    private static void finishStealFood(EntityMaid maid) {
        StealState state = stealStates.remove(maid.getUUID());
        if (state != null && state.maidInstance == maid) {
            clearStealNavigation(maid);
            MaidMovementControl.end(maid, MaidMovementControl.Reason.BEGGING);
        }
    }

    private static void clearStealNavigation(EntityMaid maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
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
            if (!BaubleDetector.hasNoEat(maid)) {
                String suffix = EmotionData.getTendencyPromptSuffix(maid, ownerId);
                String prompt = "你饿得头昏眼花，跑去找身边的女仆借食物，可是接连找了好几个女仆，她们都没有多余的食物，你空手而归，又饿又委屈。" + suffix
                        + " 请用你自己的话诉说你这趟借食白跑一趟的失落和委屈，40字左右。";
                MaidResponder.processBroadcast(serverPlayer, Collections.singletonList(maid), prompt, false);
            }
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

        speedAttr.removeModifier(SPEED_EFFECT_UUID);
        double additive = calculateAdditionForTarget(speedAttr, targetSpeed);
        if (Math.abs(additive) > 1.0E-6D) {
            speedAttr.addTransientModifier(new AttributeModifier(SPEED_EFFECT_UUID,
                    "CallResponse hunger speed", additive, AttributeModifier.Operation.ADDITION));
        }
    }

    private static double calculateAdditionForTarget(AttributeInstance attribute, double target) {
        double addition = attribute.getModifiers(AttributeModifier.Operation.ADDITION).stream()
                .filter(modifier -> !modifier.getId().equals(SPEED_EFFECT_UUID))
                .mapToDouble(AttributeModifier::getAmount).sum();
        double multiplyBase = 1.0D + attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE).stream()
                .mapToDouble(AttributeModifier::getAmount).sum();
        double multiplyTotal = attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL).stream()
                .mapToDouble(modifier -> 1.0D + modifier.getAmount())
                .reduce(1.0D, (left, right) -> left * right);
        double factor = multiplyBase * multiplyTotal;
        if (Math.abs(factor) < 1.0E-6D) {
            return 0.0D;
        }
        return target / factor - attribute.getBaseValue() - addition;
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
        finishStealFood(maid);
        AttributeInstance speed = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_EFFECT_UUID);
        HungerEatingGuard.clear(maid);
        if (!maid.getPersistentData().getBoolean(OVERFED_DEATH_TAG)) return;
        if (maid.getPersistentData().getBoolean("DevotedSacrifice")) return;

        if (!event.getSource().is(DamageTypes.STARVE) || HungerData.get(maid) < 91) {
            maid.getPersistentData().remove(OVERFED_DEATH_TAG);
            return;
        }

        event.setCanceled(true);
        maid.getPersistentData().remove(OVERFED_DEATH_TAG);

        String maidName = maid.getDisplayName().getString();
        Component deathMsg = Component.translatable("death.attack.callresponse.overfed", maidName);
        maid.level().players().forEach(p -> p.sendSystemMessage(deathMsg));
    }

    @SubscribeEvent
    public void onMaidLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            StealState state = stealStates.remove(maid.getUUID());
            if (state != null && state.maidInstance == maid) {
                MaidMovementControl.clearNavigation(maid);
                MaidMovementControl.end(maid, MaidMovementControl.Reason.BEGGING);
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stealStates.clear();
    }
}
