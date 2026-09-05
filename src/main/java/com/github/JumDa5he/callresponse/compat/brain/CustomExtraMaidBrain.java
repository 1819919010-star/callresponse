package com.github.JumDa5he.callresponse.compat.brain;

import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.game.WatchBoardGameBehavior;

import com.github.JumDa5he.callresponse.compat.bauble.BaubleDetector;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.SpawnParticleMessage;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

public class CustomExtraMaidBrain implements IExtraMaidBrain {

    private static final Random RANDOM = new Random();
    /** 只存在于运行内存，不写入女仆 NBT；卸载附属后 TLM 会按原逻辑自动唤醒。 */
    private static final Set<EntityMaid> GROUND_NAPPING_MAIDS =
            Collections.newSetFromMap(new WeakHashMap<>());
    /** 调试指令提交的一次性需求，只在对应女仆的好吃懒做 Behavior 下一 tick 消费。 */
    private static final Map<EntityMaid, String> FORCED_LAZY_NEEDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final String KEY_LAZY_STATE = "LazyState";
    private static final String KEY_STATE_TIMER = "LazyStateTimer";
    private static final String KEY_TARGET_REST_X = "LazyTargetRestX";
    private static final String KEY_TARGET_REST_Y = "LazyTargetRestY";
    private static final String KEY_TARGET_REST_Z = "LazyTargetRestZ";
    private static final String KEY_TARGET_FOOD_X = "LazyTargetFoodX";
    private static final String KEY_TARGET_FOOD_Y = "LazyTargetFoodY";
    private static final String KEY_TARGET_FOOD_Z = "LazyTargetFoodZ";
    private static final String KEY_TARGET_CAKE_X = "LazyTargetCakeX";
    private static final String KEY_TARGET_CAKE_Y = "LazyTargetCakeY";
    private static final String KEY_TARGET_CAKE_Z = "LazyTargetCakeZ";

    private static final int OWNER_SEARCH_TIMEOUT = 300;
    private static final int OWNER_WAIT_TIMEOUT = 120;
    private static final int SEARCH_TIMEOUT = 300;
    private static final int SIT_REST_DURATION = 100;
    private static final int CAKE_SIT_DURATION = 400;
    /** 对齐 TLM 女仆床中实体相对床面的高度，避免 sleep 模型陷进完整方块。 */
    private static final double GROUND_NAP_SURFACE_OFFSET = 0.24D;

    public static boolean isGroundNapping(EntityMaid maid) {
        return GROUND_NAPPING_MAIDS.contains(maid);
    }

    public static boolean requestLazyNeed(EntityMaid maid, String need) {
        if (maid == null || !maid.isAlive()
                || !"callresponse:lazy".equals(maid.getTask().getUid().toString())) {
            return false;
        }
        FORCED_LAZY_NEEDS.put(maid, need);
        return true;
    }

    @Override
    public List<MemoryModuleType<?>> getExtraMemoryTypes() {
        return List.of();
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
        return List.of(
                Pair.of(0, new SeekFoodBehavior()),
                Pair.of(1, new LazyLoopBehavior()),
                Pair.of(10, new WatchBoardGameBehavior())
        );
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getWorkBehaviors() {
        return List.of(Pair.of(4, new FeedHungryMaidBehavior()));
    }

    private static class LazyLoopBehavior extends Behavior<EntityMaid> {
        private enum State {
            IDLE,
            GOING_TO_REST,
            RESTING,
            SEARCHING_REST,
            GOING_TO_OWNER,
            EATING,
            GOING_TO_FOOD,
            SEARCHING_FOOD,
            RESTING_TIRED,
            GOING_TO_CAKE,
            RESTING_CAKE,
            GROUND_NAPPING
        }

        private enum NeedType {
            NONE, REST, OWNER_FOOD, FOOD_SOURCE, CAKE, GROUND_NAP
        }

        private State currentState = State.IDLE;
        private NeedType currentNeed = NeedType.NONE;
        private BlockPos targetRestPos = null;
        private BlockPos targetFoodPos = null;
        private BlockPos targetCakePos = null;
        private LivingEntity targetOwner = null;
        private BlockPos groundNapGroundPos = null;
        private double groundNapX;
        private double groundNapY;
        private double groundNapZ;
        private double restHoldX;
        private double restHoldY;
        private double restHoldZ;
        private int restStartTick;
        private int stateTimer = 0;
        private int searchCooldown = 0;
        private boolean hasShownMessage = false;
        private int voiceCooldown = 0;
        private ItemStack foodToEat = ItemStack.EMPTY;
        private int lastHurtTick = 0;

        private int ownerSearchTimer = 0;
        private int ownerWaitTimer = 0;
        private boolean hasShownNoFoodMessage = false;
        private boolean hasShownIgnoreMessage = false;
        private boolean hasAskedForFood = false;
        private boolean hasRepliedHit = false;
        private boolean isSpeedBoosted = false;
        private int speedBoostTimer = 0;
        private static final float NORMAL_SPEED = 0.3f;
        private static final float BOOST_SPEED = 0.8f;
        private static final int GOING_TO_TIMEOUT = 600;
        private static final int TRUST_INTERVAL = 3600;
        // Behavior 的无时长构造默认只运行 60 tick，会把所有休息在约 3 秒时强制 stop。
        // 给循环行为一个足够长的运行窗口；正常退出仍由工作模式切换、受伤等既有条件负责。
        private static final int BEHAVIOR_RUN_WINDOW = 1_000_000;
        private int trustTimer = 0;

        private int lazyModeCheckTimer = 0;
        private int workCheckTimer = 0;
        private int saveTimer = 0;
        private boolean cachedLazyMode = false;
        private boolean cachedWorkTime = true;

        public LazyLoopBehavior() {
            super(ImmutableMap.of(
                    MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                    MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED
            ), BEHAVIOR_RUN_WINDOW);
        }

        private void saveState(EntityMaid maid) {
            CompoundTag nbt = maid.getPersistentData();
            // 所有休息姿态都不落盘，避免附属被移除后留下需要本模组才能解释的状态。
            if (isExclusiveRestState(currentState)) {
                clearSavedLazyState(nbt);
                return;
            }
            nbt.putInt(KEY_LAZY_STATE, currentState.ordinal());
            nbt.putInt(KEY_STATE_TIMER, stateTimer);
            if (targetRestPos != null) {
                nbt.putInt(KEY_TARGET_REST_X, targetRestPos.getX());
                nbt.putInt(KEY_TARGET_REST_Y, targetRestPos.getY());
                nbt.putInt(KEY_TARGET_REST_Z, targetRestPos.getZ());
            }
            if (targetFoodPos != null) {
                nbt.putInt(KEY_TARGET_FOOD_X, targetFoodPos.getX());
                nbt.putInt(KEY_TARGET_FOOD_Y, targetFoodPos.getY());
                nbt.putInt(KEY_TARGET_FOOD_Z, targetFoodPos.getZ());
            }
            if (targetCakePos != null) {
                nbt.putInt(KEY_TARGET_CAKE_X, targetCakePos.getX());
                nbt.putInt(KEY_TARGET_CAKE_Y, targetCakePos.getY());
                nbt.putInt(KEY_TARGET_CAKE_Z, targetCakePos.getZ());
            }
        }

        private void clearSavedLazyState(CompoundTag nbt) {
            nbt.remove(KEY_LAZY_STATE);
            nbt.remove(KEY_STATE_TIMER);
            nbt.remove(KEY_TARGET_REST_X);
            nbt.remove(KEY_TARGET_REST_Y);
            nbt.remove(KEY_TARGET_REST_Z);
            nbt.remove(KEY_TARGET_FOOD_X);
            nbt.remove(KEY_TARGET_FOOD_Y);
            nbt.remove(KEY_TARGET_FOOD_Z);
            nbt.remove(KEY_TARGET_CAKE_X);
            nbt.remove(KEY_TARGET_CAKE_Y);
            nbt.remove(KEY_TARGET_CAKE_Z);
        }

        private void loadState(EntityMaid maid) {
            CompoundTag nbt = maid.getPersistentData();
            if (nbt.contains(KEY_LAZY_STATE)) {
                int stateOrd = nbt.getInt(KEY_LAZY_STATE);
                if (stateOrd >= 0 && stateOrd < State.values().length) {
                    currentState = State.values()[stateOrd];
                    // 旧版本可能把休息状态写进 NBT，但没有可安全恢复的原地坐标；加载后重新开始循环。
                    if (isExclusiveRestState(currentState)) {
                        currentState = State.IDLE;
                    }
                }
                stateTimer = nbt.getInt(KEY_STATE_TIMER);
                if (nbt.contains(KEY_TARGET_REST_X)) {
                    targetRestPos = new BlockPos(
                            nbt.getInt(KEY_TARGET_REST_X),
                            nbt.getInt(KEY_TARGET_REST_Y),
                            nbt.getInt(KEY_TARGET_REST_Z)
                    );
                }
                if (nbt.contains(KEY_TARGET_FOOD_X)) {
                    targetFoodPos = new BlockPos(
                            nbt.getInt(KEY_TARGET_FOOD_X),
                            nbt.getInt(KEY_TARGET_FOOD_Y),
                            nbt.getInt(KEY_TARGET_FOOD_Z)
                    );
                }
                if (nbt.contains(KEY_TARGET_CAKE_X)) {
                    targetCakePos = new BlockPos(
                            nbt.getInt(KEY_TARGET_CAKE_X),
                            nbt.getInt(KEY_TARGET_CAKE_Y),
                            nbt.getInt(KEY_TARGET_CAKE_Z)
                    );
                }
                nbt.remove(KEY_LAZY_STATE);
                nbt.remove(KEY_STATE_TIMER);
                nbt.remove(KEY_TARGET_REST_X);
                nbt.remove(KEY_TARGET_REST_Y);
                nbt.remove(KEY_TARGET_REST_Z);
                nbt.remove(KEY_TARGET_FOOD_X);
                nbt.remove(KEY_TARGET_FOOD_Y);
                nbt.remove(KEY_TARGET_FOOD_Z);
                nbt.remove(KEY_TARGET_CAKE_X);
                nbt.remove(KEY_TARGET_CAKE_Y);
                nbt.remove(KEY_TARGET_CAKE_Z);
            }
        }

        private boolean isLazyMode(EntityMaid maid) {
            if (--lazyModeCheckTimer > 0) return cachedLazyMode;
            lazyModeCheckTimer = 20;
            cachedLazyMode = "callresponse:lazy".equals(maid.getTask().getUid().toString());
            return cachedLazyMode;
        }

        private void clearAllMemories(EntityMaid maid) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            maid.setBegging(false);
            targetRestPos = null;
            targetFoodPos = null;
            targetCakePos = null;
            targetOwner = null;
            foodToEat = ItemStack.EMPTY;
            hasShownMessage = false;
            hasShownNoFoodMessage = false;
            hasShownIgnoreMessage = false;
            currentNeed = NeedType.NONE;
            ownerWaitTimer = 0;
            hasAskedForFood = false;
        }

        private void standUp(EntityMaid maid) {
            endGroundNap(maid);
            if (MaidMovementControl.isActive(maid, MaidMovementControl.Reason.LAZY_POSE)) {
                MaidMovementControl.end(maid, MaidMovementControl.Reason.LAZY_POSE);
            }
            maid.setXRot(0);
        }

        private void sitDown(EntityMaid maid) {
            MaidMovementControl.begin(maid, MaidMovementControl.Reason.LAZY_POSE,
                    java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
            restHoldX = maid.getX();
            restHoldY = maid.getY();
            restHoldZ = maid.getZ();
            restStartTick = maid.tickCount;
            MaidMovementControl.clearNavigation(maid);
            maid.setInSittingPose(true);
        }

        private boolean isExclusiveRestState(State state) {
            return state == State.RESTING || state == State.RESTING_TIRED
                    || state == State.RESTING_CAKE || state == State.GROUND_NAPPING;
        }

        private boolean canNapHere(ServerLevel level, EntityMaid maid) {
            BlockPos groundPos = maid.blockPosition().below();
            return level.getBlockState(groundPos).isFaceSturdy(level, groundPos, net.minecraft.core.Direction.UP)
                    && level.getBlockState(groundPos.above()).getCollisionShape(level, groundPos.above()).isEmpty()
                    && level.getBlockState(groundPos.above(2)).getCollisionShape(level, groundPos.above(2)).isEmpty();
        }

        private void beginGroundNap(ServerLevel level, EntityMaid maid) {
            BlockPos groundPos = maid.blockPosition().below();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            maid.getNavigation().stop();
            if (maid.isMaidInSittingPose()) {
                maid.setInSittingPose(false);
            }
            float yaw = RANDOM.nextInt(4) * 90.0F;
            maid.setYRot(yaw);
            maid.setYHeadRot(yaw);
            maid.setYBodyRot(yaw);
            GROUND_NAPPING_MAIDS.add(maid);
            // 只使用 TLM sleep 动画读取的姿态，不进入真实睡眠，避免 Brain 自动切换 REST 活动。
            double surfaceY = groundPos.getY() + level.getBlockState(groundPos)
                    .getCollisionShape(level, groundPos).max(net.minecraft.core.Direction.Axis.Y);
            groundNapGroundPos = groundPos.immutable();
            groundNapX = maid.getX();
            groundNapY = surfaceY + GROUND_NAP_SURFACE_OFFSET;
            groundNapZ = maid.getZ();
            maid.setPos(groundNapX, groundNapY, groundNapZ);
            maid.setDeltaMovement(0, 0, 0);
            maid.setPose(Pose.SLEEPING);
            MaidMovementControl.begin(maid, MaidMovementControl.Reason.LAZY_POSE,
                    java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
            restStartTick = maid.tickCount;
            currentState = State.GROUND_NAPPING;
            stateTimer = RANDOM.nextInt(800) + 400;
            playVoice(maid);
            maid.getChatBubbleManager().addTextChatBubble("躺一会儿吧");
        }

        private void endGroundNap(EntityMaid maid) {
            if (!GROUND_NAPPING_MAIDS.remove(maid)) return;
            if (maid.isSleeping()) {
                maid.stopSleeping();
            } else if (maid.getPose() == Pose.SLEEPING) {
                maid.setPose(Pose.STANDING);
            }
            groundNapGroundPos = null;
            maid.setXRot(0);
        }

        private NeedType consumeForcedNeed(EntityMaid maid) {
            String need = FORCED_LAZY_NEEDS.remove(maid);
            if (need == null) return null;
            return switch (need) {
                case "rest" -> NeedType.REST;
                case "owner_food" -> NeedType.OWNER_FOOD;
                case "food_source" -> NeedType.FOOD_SOURCE;
                case "cake" -> NeedType.CAKE;
                case "ground_nap" -> NeedType.GROUND_NAP;
                default -> null;
            };
        }

        private boolean wasRestInterruptedByDamage(EntityMaid maid) {
            if (LazyMaidHitHandler.checkEscape(maid)) return true;
            if (maid.tickCount <= restStartTick || maid.hurtTime <= 0) return false;
            var source = maid.getLastDamageSource();
            // 饥饿伤害不能打断休息；其他真实受伤视为被打醒。
            return source == null || !source.is(DamageTypes.STARVE);
        }

        /** 休息期间的唯一状态循环：受伤退出，否则原地维持到计时结束。 */
        private void tickExclusiveRest(ServerLevel level, EntityMaid maid) {
            if (wasRestInterruptedByDamage(maid)) {
                hasRepliedHit = LazyMaidHitHandler.checkEscape(maid);
                standUp(maid);
                clearAllMemories(maid);
                currentState = State.IDLE;
                isSpeedBoosted = true;
                speedBoostTimer = 60;
                stateTimer = RANDOM.nextInt(200) + 100;
                setRandomWalkTarget(maid, BOOST_SPEED);
                return;
            }

            MaidMovementControl.clearNavigation(maid);
            maid.setDeltaMovement(0, 0, 0);
            if (currentState == State.GROUND_NAPPING) {
                boolean groundStillValid = groundNapGroundPos != null
                        && level.getBlockState(groundNapGroundPos).isFaceSturdy(
                        level, groundNapGroundPos, net.minecraft.core.Direction.UP);
                if (!groundStillValid || !isGroundNapping(maid)) {
                    finishExclusiveRest(maid);
                    return;
                }
                maid.setPos(groundNapX, groundNapY, groundNapZ);
                maid.setPose(Pose.SLEEPING);
            } else {
                maid.setPos(restHoldX, restHoldY, restHoldZ);
                maid.setInSittingPose(true);
                if (currentState == State.RESTING_CAKE && targetCakePos != null) {
                    maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetCakePos));
                }
            }

            stateTimer--;
            if (stateTimer <= 0) {
                State finished = currentState;
                finishExclusiveRest(maid);
                if (finished == State.RESTING) {
                    playVoice(maid);
                    maid.getChatBubbleManager().addTextChatBubble("休息够了，溜达溜达");
                } else if (finished == State.RESTING_TIRED || finished == State.RESTING_CAKE) {
                    playVoice(maid);
                    maid.getChatBubbleManager().addTextChatBubble("继续溜达~");
                }
            }
        }

        private void finishExclusiveRest(EntityMaid maid) {
            standUp(maid);
            clearAllMemories(maid);
            currentState = State.IDLE;
            stateTimer = RANDOM.nextInt(400) + 200;
            setRandomWalkTarget(maid, NORMAL_SPEED);
        }

        private void eatFood(EntityMaid maid, ItemStack food) {
            maid.eat(maid.level(), food);
            playVoice(maid);
            maid.getChatBubbleManager().addTextChatBubble("好吃~");
        }

        private void setRandomWalkTarget(EntityMaid maid, float speed) {
            BlockPos currentPos = maid.blockPosition();
            int randomX = currentPos.getX() + (int)(RANDOM.nextDouble() * 20 - 10);
            int randomZ = currentPos.getZ() + (int)(RANDOM.nextDouble() * 20 - 10);
            BlockPos targetPos = new BlockPos(randomX, currentPos.getY(), randomZ);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(targetPos), speed, 2));
        }

        private boolean isWorkTime(EntityMaid maid) {
            if (--workCheckTimer > 0) return cachedWorkTime;
            workCheckTimer = 20;
            String scheduleMode = maid.getSchedule().name();
            long dayTime = maid.level().getDayTime() % 24000;
            if ("NIGHT".equals(scheduleMode)) {
                cachedWorkTime = dayTime >= 13000 || dayTime <= 6000;
            } else if ("DAY".equals(scheduleMode)) {
                cachedWorkTime = dayTime >= 6000 && dayTime <= 13000;
            } else {
                cachedWorkTime = true;
            }
            return cachedWorkTime;
        }

        private boolean isReachedTarget(EntityMaid maid, BlockPos target) {
            if (target == null) return false;
            double dx = maid.getX() - (target.getX() + 0.5);
            double dz = maid.getZ() - (target.getZ() + 0.5);
            double distance = Math.sqrt(dx * dx + dz * dz);
            double dy = Math.abs(maid.getY() - target.getY());
            return distance <= 1 && dy <= 2.0;
        }

        private BlockPos findNearestRestPlace(ServerLevel level, EntityMaid maid) {
            BlockPos maidPos = maid.blockPosition();
            int searchRadius = 20;
            BlockPos nearest = null;
            double closestDist = searchRadius + 1;
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos checkPos = maidPos.offset(dx, dy, dz);
                        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(level.getBlockState(checkPos).getBlock());
                        if (blockKey != null && blockKey.toString().equals("touhou_little_maid:maid_bed")) {
                            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                            if (dist < closestDist) {
                                closestDist = dist;
                                nearest = checkPos;
                            }
                        }
                    }
                }
            }
            return nearest;
        }

        private BlockPos findNearestFoodSource(ServerLevel level, EntityMaid maid) {
            BlockPos maidPos = maid.blockPosition();
            int searchRadius = 20;
            BlockPos nearest = null;
            double closestDist = searchRadius + 1;
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos checkPos = maidPos.offset(dx, dy, dz);
                        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(level.getBlockState(checkPos).getBlock());
                        if (blockKey != null && blockKey.toString().equals("touhou_little_maid:snack_cabinet")) {
                            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                            if (dist < closestDist) {
                                closestDist = dist;
                                nearest = checkPos;
                            }
                        }
                    }
                }
            }
            return nearest;
        }

        private ItemStack takeOneFoodFromContainer(ServerLevel level, BlockPos pos, EntityMaid maid) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return ItemStack.EMPTY;
            java.util.List<Integer> foodSlots = new java.util.ArrayList<>();
            IItemHandler handler = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty() && stack.getFoodProperties(maid) != null) {
                        foodSlots.add(slot);
                    }
                }
                if (!foodSlots.isEmpty()) {
                    int chosen = foodSlots.get(RANDOM.nextInt(foodSlots.size()));
                    return handler.extractItem(chosen, 1, false);
                }
            } else if (be instanceof RandomizableContainerBlockEntity container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty() && stack.getFoodProperties(maid) != null) {
                        foodSlots.add(i);
                    }
                }
                if (!foodSlots.isEmpty()) {
                    int chosen = foodSlots.get(RANDOM.nextInt(foodSlots.size()));
                    ItemStack stack = container.getItem(chosen);
                    ItemStack taken = stack.split(1);
                    container.setItem(chosen, stack);
                    return taken;
                }
            }
            return ItemStack.EMPTY;
        }

        private BlockPos findNearestCake(ServerLevel level, EntityMaid maid) {
            BlockPos maidPos = maid.blockPosition();
            int searchRadius = 20;
            BlockPos nearest = null;
            double closestDist = searchRadius + 1;
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos checkPos = maidPos.offset(dx, dy, dz);
                        if (level.getBlockState(checkPos).is(Blocks.CAKE)) {
                            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                            if (dist < closestDist) {
                                closestDist = dist;
                                nearest = checkPos;
                            }
                        }
                    }
                }
            }
            return nearest;
        }

        private LivingEntity findOwner(EntityMaid maid) {
            if (maid.getOwner() instanceof Player owner && owner.isAlive() && owner.distanceTo(maid) <= 24) {
                return owner;
            }
            return null;
        }

        private ItemStack getFoodFromOwner(Player owner) {
            if (!owner.getMainHandItem().isEmpty() && owner.getMainHandItem().getFoodProperties(null) != null) {
                return owner.getMainHandItem();
            }
            if (!owner.getOffhandItem().isEmpty() && owner.getOffhandItem().getFoodProperties(null) != null) {
                return owner.getOffhandItem();
            }
            return ItemStack.EMPTY;
        }

        private NeedType getRandomNeed(ServerLevel level, EntityMaid maid) {
            boolean hasOwner = findOwner(maid) != null;
            if (!hasOwner) return NeedType.NONE;
            if (BaubleDetector.hasNoEat(maid)) return NeedType.NONE;
            boolean moreEat = BaubleDetector.hasMoreEat(maid);
            if (moreEat) {
                int r = RANDOM.nextInt(3);
                switch (r) {
                    case 0: return NeedType.OWNER_FOOD;
                    case 1: return NeedType.FOOD_SOURCE;
                    default: return NeedType.CAKE;
                }
            }
            int r = RANDOM.nextInt(5);
            switch (r) {
                case 0: return NeedType.REST;
                case 1: return NeedType.OWNER_FOOD;
                case 2: return NeedType.FOOD_SOURCE;
                case 3: return NeedType.CAKE;
                default: return NeedType.GROUND_NAP;
            }
        }

        private boolean isHitByOwner(EntityMaid maid) {
            var lastHurtBy = maid.getLastHurtByMob();
            if (lastHurtBy != null && lastHurtBy.equals(maid.getOwner())) {
                if (lastHurtTick == 0 || maid.tickCount - lastHurtTick > 20) {
                    lastHurtTick = maid.tickCount;
                    return true;
                }
            }
            return false;
        }

        private void playVoice(EntityMaid maid) {
            maid.playSound(InitSounds.MAID_IDLE.get(), 0.5f, 1.0f);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
            if (isExclusiveRestState(currentState)) {
                return LazyMaidHitHandler.isLazyMode(maid);
            }
            if (!isLazyMode(maid) || !isWorkTime(maid)
                    || maid.isSleeping() && !isGroundNapping(maid)) return false;
            loadState(maid);
            return true;
        }

        @Override
        protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
            if (currentState == State.IDLE && !maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) && stateTimer > 0) {
                setRandomWalkTarget(maid, NORMAL_SPEED);
                stateTimer = RANDOM.nextInt(400) + 200;
            }
        }

        @Override
        protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
            if (isExclusiveRestState(currentState)) {
                if (!LazyMaidHitHandler.isLazyMode(maid)) {
                    standUp(maid);
                    clearAllMemories(maid);
                    currentState = State.IDLE;
                    stateTimer = 0;
                    return;
                }
                tickExclusiveRest(level, maid);
                return;
            }
            if (!isLazyMode(maid) || !isWorkTime(maid)
                    || maid.isSleeping() && !isGroundNapping(maid)) return;
            // 低饥饿正式 Behavior 正在寻食时，懒惰循环暂时让出 WALK_TARGET。
            if (SeekFoodBehavior.isSeeking(maid)) return;

            NeedType forcedNeed = consumeForcedNeed(maid);
            if (forcedNeed != null) {
                standUp(maid);
                clearAllMemories(maid);
                currentState = State.IDLE;
                currentNeed = forcedNeed;
                stateTimer = 0;
                isSpeedBoosted = false;
                speedBoostTimer = 0;
            }

            if (!hasRepliedHit && LazyMaidHitHandler.checkEscape(maid)) {
                hasRepliedHit = true;
                standUp(maid);
                clearAllMemories(maid);
                currentState = State.IDLE;
                isSpeedBoosted = true;
                speedBoostTimer = 60;
                setRandomWalkTarget(maid, BOOST_SPEED);
                stateTimer = RANDOM.nextInt(200) + 100;
                return;
            }

            State prevState = currentState;

            boolean shouldEscape = isHitByOwner(maid) && !hasRepliedHit;
            if (shouldEscape) {
                hasRepliedHit = true;
                standUp(maid);
                clearAllMemories(maid);
                currentState = State.IDLE;
                isSpeedBoosted = true;
                speedBoostTimer = 60;
                setRandomWalkTarget(maid, BOOST_SPEED);
                stateTimer = RANDOM.nextInt(200) + 100;
                return;
            }

            if (maid.tickCount % 20 == 0) {
                int currentExp = maid.getExperience();
                maid.setExperience(currentExp + 3);
            }

            if (maid.tickCount % 1200 == 0) {
                addFavorabilityAndHearts(maid);
            }

            trustTimer++;
            if (trustTimer >= TRUST_INTERVAL) {
                trustTimer = 0;
                if (maid.getOwner() instanceof ServerPlayer sp) {
                    EmotionData.addTrust(maid, sp.getUUID(), 1);
                }
            }

            if (voiceCooldown > 0) voiceCooldown--;
            if (speedBoostTimer > 0) {
                speedBoostTimer--;
                if (speedBoostTimer == 0) {
                    isSpeedBoosted = false;
                    setRandomWalkTarget(maid, NORMAL_SPEED);
                }
            }

            if (currentState == State.IDLE && speedBoostTimer == 0 && hasRepliedHit) {
                hasRepliedHit = false;
            }

            if (maid.isInSittingPose() && currentState != State.RESTING
                    && currentState != State.RESTING_TIRED && currentState != State.RESTING_CAKE) {
                sitDown(maid);
                clearAllMemories(maid);
                currentState = State.RESTING;
                stateTimer = RANDOM.nextInt(800) + 400;
                return;
            }
            if (!maid.isInSittingPose() && (currentState == State.RESTING
                    || currentState == State.RESTING_TIRED || currentState == State.RESTING_CAKE)) {
                standUp(maid);
                clearAllMemories(maid);
                currentState = State.IDLE;
                stateTimer = RANDOM.nextInt(200) + 100;
                setRandomWalkTarget(maid, NORMAL_SPEED);
                return;
            }

            if (currentState == State.IDLE && stateTimer <= 0) {
                if (isSpeedBoosted) {
                    isSpeedBoosted = false;
                    speedBoostTimer = 0;
                }
                currentNeed = forcedNeed != null ? forcedNeed : getRandomNeed(level, maid);
                if (currentNeed == NeedType.REST) {
                    targetRestPos = findNearestRestPlace(level, maid);
                    if (targetRestPos != null) {
                        currentState = State.GOING_TO_REST;
                        stateTimer = GOING_TO_TIMEOUT;
                        hasShownMessage = false;
                        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                new WalkTarget(new BlockPosTracker(targetRestPos), 0.6f, 1));
                    } else {
                        currentState = State.SEARCHING_REST;
                        hasShownMessage = false;
                        searchCooldown = 0;
                        stateTimer = SEARCH_TIMEOUT;
                        isSpeedBoosted = true;
                        speedBoostTimer = SEARCH_TIMEOUT;
                        setRandomWalkTarget(maid, BOOST_SPEED);
                    }
                } else if (currentNeed == NeedType.OWNER_FOOD) {
                    targetOwner = findOwner(maid);
                    if (targetOwner != null) {
                        currentState = State.GOING_TO_OWNER;
                        hasShownMessage = false;
                        hasShownNoFoodMessage = false;
                        hasShownIgnoreMessage = false;
                        ownerSearchTimer = 0;
                        ownerWaitTimer = 0;
                        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                new WalkTarget(new BlockPosTracker(targetOwner.blockPosition()), 0.4f, 3));
                    } else {
                        ownerSearchTimer = 1;
                        currentState = State.GOING_TO_OWNER;
                        hasShownMessage = false;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                    }
                } else if (currentNeed == NeedType.FOOD_SOURCE) {
                    targetFoodPos = findNearestFoodSource(level, maid);
                    if (targetFoodPos != null) {
                        currentState = State.GOING_TO_FOOD;
                        stateTimer = GOING_TO_TIMEOUT;
                        hasShownMessage = false;
                        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                new WalkTarget(new BlockPosTracker(targetFoodPos), 0.6f, 1));
                    } else {
                        currentState = State.SEARCHING_FOOD;
                        hasShownMessage = false;
                        searchCooldown = 0;
                        stateTimer = SEARCH_TIMEOUT;
                        isSpeedBoosted = true;
                        speedBoostTimer = SEARCH_TIMEOUT;
                        setRandomWalkTarget(maid, BOOST_SPEED);
                    }
                } else if (currentNeed == NeedType.CAKE) {
                    targetCakePos = findNearestCake(level, maid);
                    if (targetCakePos != null) {
                        currentState = State.GOING_TO_CAKE;
                        stateTimer = GOING_TO_TIMEOUT;
                        hasShownMessage = false;
                        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                new WalkTarget(new BlockPosTracker(targetCakePos), 0.6f, 1));
                    } else {
                        stateTimer = RANDOM.nextInt(200) + 100;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                        maid.getChatBubbleManager().addTextChatBubble("想念蛋糕ing...");
                    }
                } else if (currentNeed == NeedType.GROUND_NAP) {
                    if (canNapHere(level, maid)) {
                        beginGroundNap(level, maid);
                    } else {
                        stateTimer = RANDOM.nextInt(200) + 100;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                    }
                } else {
                    stateTimer = RANDOM.nextInt(200) + 100;
                    setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                }
                return;
            }

            switch (currentState) {
                case IDLE:
                    if (isSpeedBoosted && !maid.isUsingItem()) setRandomWalkTarget(maid, BOOST_SPEED);
                    stateTimer--;
                    if (stateTimer <= 0) {
                    } else if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) && !maid.isUsingItem()) {
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                    }
                    break;

                case GOING_TO_REST:
                    stateTimer--;
                    if (stateTimer <= 0) {
                        currentState = State.IDLE;
                        clearAllMemories(maid);
                        stateTimer = RANDOM.nextInt(200) + 100;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                        break;
                    }
                    if (targetRestPos != null) {
                        boolean reached = isReachedTarget(maid, targetRestPos);
                        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(level.getBlockState(targetRestPos).getBlock());
                        boolean isValid = (blockKey != null && blockKey.toString().equals("touhou_little_maid:maid_bed"));
                        if (!isValid) {
                            currentState = State.SEARCHING_REST;
                            hasShownMessage = false;
                            standUp(maid);
                            clearAllMemories(maid);
                            searchCooldown = 0;
                            stateTimer = SEARCH_TIMEOUT;
                            isSpeedBoosted = true;
                            speedBoostTimer = SEARCH_TIMEOUT;
                            setRandomWalkTarget(maid, BOOST_SPEED);
                        } else if (reached) {
                            sitDown(maid);
                            playVoice(maid);
                            maid.getChatBubbleManager().addTextChatBubble("休息一下");
                            stateTimer = RANDOM.nextInt(800) + 400;
                            currentState = State.RESTING;
                            clearAllMemories(maid);
                        }
                    }
                    break;

                case RESTING:
                    stateTimer--;
                    if (stateTimer <= 0) {
                        standUp(maid);
                        clearAllMemories(maid);
                        currentState = State.IDLE;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                        stateTimer = RANDOM.nextInt(400) + 200;
                        playVoice(maid);
                        maid.getChatBubbleManager().addTextChatBubble("休息够了，溜达溜达");
                    }
                    break;

                case SEARCHING_REST:
                    if (!hasShownMessage) {
                        maid.getChatBubbleManager().addTextChatBubble("我的床呢？！");
                        hasShownMessage = true;
                    }
                    if (voiceCooldown == 0) {
                        playVoice(maid);
                        voiceCooldown = 80;
                    }
                    stateTimer--;
                    if (stateTimer <= 0) {
                        standUp(maid);
                        clearAllMemories(maid);
                        sitDown(maid);
                        maid.getChatBubbleManager().addTextChatBubble("好累啊...");
                        playVoice(maid);
                        currentState = State.RESTING_TIRED;
                        stateTimer = SIT_REST_DURATION;
                        isSpeedBoosted = false;
                        speedBoostTimer = 0;
                        break;
                    }
                    searchCooldown++;
                    if (searchCooldown >= 60) {
                        searchCooldown = 0;
                        targetRestPos = findNearestRestPlace(level, maid);
                        if (targetRestPos != null) {
                            currentState = State.GOING_TO_REST;
                            hasShownMessage = false;
                            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                    new WalkTarget(new BlockPosTracker(targetRestPos), 0.6f, 1));
                            isSpeedBoosted = false;
                            speedBoostTimer = 0;
                        } else {
                            setRandomWalkTarget(maid, BOOST_SPEED);
                        }
                    }
                    break;

                case SEARCHING_FOOD:
                    if (!hasShownMessage) {
                        maid.getChatBubbleManager().addTextChatBubble("主人把吃的藏哪了？");
                        hasShownMessage = true;
                    }
                    if (voiceCooldown == 0) {
                        playVoice(maid);
                        voiceCooldown = 80;
                    }
                    stateTimer--;
                    if (stateTimer <= 0) {
                        standUp(maid);
                        clearAllMemories(maid);
                        sitDown(maid);
                        maid.getChatBubbleManager().addTextChatBubble("好累啊...");
                        playVoice(maid);
                        currentState = State.RESTING_TIRED;
                        stateTimer = SIT_REST_DURATION;
                        isSpeedBoosted = false;
                        speedBoostTimer = 0;
                        break;
                    }
                    searchCooldown++;
                    if (searchCooldown >= 60) {
                        searchCooldown = 0;
                        targetFoodPos = findNearestFoodSource(level, maid);
                        if (targetFoodPos != null) {
                            currentState = State.GOING_TO_FOOD;
                            hasShownMessage = false;
                            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                    new WalkTarget(new BlockPosTracker(targetFoodPos), 0.6f, 1));
                            isSpeedBoosted = false;
                            speedBoostTimer = 0;
                        } else {
                            setRandomWalkTarget(maid, BOOST_SPEED);
                        }
                    }
                    break;

                case RESTING_TIRED:
                    stateTimer--;
                    if (stateTimer <= 0) {
                        standUp(maid);
                        clearAllMemories(maid);
                        currentState = State.IDLE;
                        setRandomWalkTarget(maid, NORMAL_SPEED);
                        stateTimer = RANDOM.nextInt(200) + 100;
                        maid.getChatBubbleManager().addTextChatBubble("继续溜达~");
                        playVoice(maid);
                    }
                    break;

                case GOING_TO_OWNER:
                    if (targetOwner != null && targetOwner.isAlive()) {
                        ownerSearchTimer = 0;
                        maid.setBegging(true);
                        maid.getLookControl().setLookAt(targetOwner, 10.0F, 10.0F);
                        double distance = maid.distanceTo(targetOwner);
                        if (distance > 3) {
                            if (voiceCooldown == 0 && !hasAskedForFood) {
                                maid.getChatBubbleManager().addTextChatBubble("主人你有吃的吗");
                                hasAskedForFood = true;
                                voiceCooldown = 100;
                            }
                            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                    new WalkTarget(new BlockPosTracker(targetOwner.blockPosition()), 0.4f, 3));
                            ownerWaitTimer = 0;
                        } else if (distance <= 1.0) {
                            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                            maid.setBegging(false);
                            if (targetOwner instanceof Player owner) {
                                ItemStack food = getFoodFromOwner(owner);
                                if (!food.isEmpty()) {
                                    hasShownNoFoodMessage = false;
                                    ItemStack foodToGive = food.split(1);
                                    maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, foodToGive.copy());
                                    maid.getChatBubbleManager().addTextChatBubble("谢谢主人~");
                                    playVoice(maid);
                                    foodToEat = foodToGive.copy();
                                    addFavorabilityAndHearts(maid);
                                    currentState = State.EATING;
                                    stateTimer = 40;
                                } else if (!hasShownNoFoodMessage) {
                                    hasShownNoFoodMessage = true;
                                    maid.setBegging(false);
                                    maid.getChatBubbleManager().addTextChatBubble("原来主人也没吃的吗");
                                    playVoice(maid);
                                    currentState = State.IDLE;
                                    clearAllMemories(maid);
                                    stateTimer = RANDOM.nextInt(200) + 100;
                                    setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                                }
                            }
                            ownerWaitTimer = 0;
                        } else {
                            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                            ownerWaitTimer++;
                            if (ownerWaitTimer >= OWNER_WAIT_TIMEOUT && !hasShownIgnoreMessage) {
                                hasShownIgnoreMessage = true;
                                maid.setBegging(false);
                                maid.getChatBubbleManager().addTextChatBubble("主人怎么不理我");
                                playVoice(maid);
                                currentState = State.IDLE;
                                clearAllMemories(maid);
                                stateTimer = RANDOM.nextInt(200) + 100;
                                setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                            }
                        }
                    } else {
                        ownerSearchTimer++;
                        if (ownerSearchTimer >= OWNER_SEARCH_TIMEOUT) {
                            maid.setBegging(false);
                            currentState = State.IDLE;
                            clearAllMemories(maid);
                            stateTimer = RANDOM.nextInt(200) + 100;
                            setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                            maid.getChatBubbleManager().addTextChatBubble("主人去哪儿了？");
                            playVoice(maid);
                            ownerSearchTimer = 0;
                        } else {
                            if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                                setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                            }
                        }
                    }
                    break;

                case EATING:
                    stateTimer--;
                    if (stateTimer <= 0) {
                        if (!foodToEat.isEmpty()) {
                            maid.setItemInHand(InteractionHand.MAIN_HAND, foodToEat);
                            if (foodToEat.getFoodProperties(maid) != null) {
                                maid.startUsingItem(InteractionHand.MAIN_HAND);
                            } else {
                                eatFood(maid, foodToEat);
                                maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            }
                            foodToEat = ItemStack.EMPTY;
                        }
                        currentState = State.IDLE;
                        clearAllMemories(maid);
                        stateTimer = RANDOM.nextInt(200) + 100;
                    }
                    break;

                case GOING_TO_FOOD:
                    stateTimer--;
                    if (stateTimer <= 0) {
                        currentState = State.IDLE;
                        clearAllMemories(maid);
                        stateTimer = RANDOM.nextInt(200) + 100;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                        break;
                    }
                    if (targetFoodPos != null) {
                        boolean reached = isReachedTarget(maid, targetFoodPos);
                        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(level.getBlockState(targetFoodPos).getBlock());
                        boolean isValid = (blockKey != null && blockKey.toString().equals("touhou_little_maid:snack_cabinet"));
                        if (!isValid) {
                            currentState = State.SEARCHING_FOOD;
                            hasShownMessage = false;
                            standUp(maid);
                            clearAllMemories(maid);
                            searchCooldown = 0;
                            stateTimer = SEARCH_TIMEOUT;
                            isSpeedBoosted = true;
                            speedBoostTimer = SEARCH_TIMEOUT;
                            setRandomWalkTarget(maid, BOOST_SPEED);
                        } else if (reached) {
                            ItemStack food = takeOneFoodFromContainer(level, targetFoodPos, maid);
                            if (!food.isEmpty()) {
                                maid.getChatBubbleManager().addTextChatBubble("找到零食！");
                                playVoice(maid);
                                maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, food.copy());
                                foodToEat = food.copy();
                                addFavorabilityAndHearts(maid);
                                currentState = State.EATING;
                                stateTimer = 40;
                            } else {
                                maid.getChatBubbleManager().addTextChatBubble("柜子里没有吃的...");
                                currentState = State.IDLE;
                                clearAllMemories(maid);
                                stateTimer = RANDOM.nextInt(200) + 100;
                                setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                            }
                        }
                    }
                    break;

                case GOING_TO_CAKE:
                    stateTimer--;
                    if (stateTimer <= 0) {
                        currentState = State.IDLE;
                        clearAllMemories(maid);
                        stateTimer = RANDOM.nextInt(200) + 100;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                        break;
                    }
                    if (targetCakePos != null) {
                        boolean reached = isReachedTarget(maid, targetCakePos);
                        boolean isStillCake = level.getBlockState(targetCakePos).is(Blocks.CAKE);
                        if (!isStillCake) {
                            currentState = State.IDLE;
                            clearAllMemories(maid);
                            stateTimer = RANDOM.nextInt(200) + 100;
                            setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                            maid.getChatBubbleManager().addTextChatBubble("蛋糕不见了...");
                        } else if (reached) {
                            sitDown(maid);
                            maid.getChatBubbleManager().addTextChatBubble("好想吃...");
                            playVoice(maid);
                            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                            maid.setBegging(false);
                            stateTimer = CAKE_SIT_DURATION;
                            currentState = State.RESTING_CAKE;
                        }
                    }
                    break;

                case RESTING_CAKE:
                    if (targetCakePos == null || !level.getBlockState(targetCakePos).is(Blocks.CAKE)) {
                        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                        standUp(maid);
                        clearAllMemories(maid);
                        currentState = State.IDLE;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                        stateTimer = RANDOM.nextInt(200) ;
                        maid.getChatBubbleManager().addTextChatBubble("蛋糕不见了...");
                        playVoice(maid);
                        break;
                    }
                    maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetCakePos));
                    stateTimer--;
                    if (stateTimer <= 0) {
                        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                        standUp(maid);
                        clearAllMemories(maid);
                        currentState = State.IDLE;
                        setRandomWalkTarget(maid, isSpeedBoosted ? BOOST_SPEED : NORMAL_SPEED);
                        stateTimer = RANDOM.nextInt(200) ;
                        maid.getChatBubbleManager().addTextChatBubble("继续溜达~");
                        playVoice(maid);
                    }
                    break;

                case GROUND_NAPPING:
                    stateTimer--;
                    boolean groundStillValid = groundNapGroundPos != null
                            && level.getBlockState(groundNapGroundPos).isFaceSturdy(
                            level, groundNapGroundPos, net.minecraft.core.Direction.UP);
                    if (stateTimer <= 0 || !isGroundNapping(maid) || !groundStillValid) {
                        endGroundNap(maid);
                        clearAllMemories(maid);
                        currentState = State.IDLE;
                        stateTimer = RANDOM.nextInt(400) + 200;
                        setRandomWalkTarget(maid, NORMAL_SPEED);
                    } else {
                        // 其他实体更新可能重算 pose；小睡期间只维持动画姿态与静止，不写入持久数据。
                        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        maid.getNavigation().stop();
                        maid.setDeltaMovement(0, 0, 0);
                        maid.setPos(groundNapX, groundNapY, groundNapZ);
                        maid.setPose(Pose.SLEEPING);
                    }
                    break;
            }

            if (currentState != prevState) {
                saveTimer = 0;
                saveState(maid);
            } else {
                saveTimer++;
                if (saveTimer >= 20) {
                    saveTimer = 0;
                    saveState(maid);
                }
            }
        }

        @Override
        protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
            if (isExclusiveRestState(currentState)) {
                return LazyMaidHitHandler.isLazyMode(maid);
            }
            return isLazyMode(maid) && isWorkTime(maid)
                    && (!maid.isSleeping() || isGroundNapping(maid));
        }

        @Override
        protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
            if (isExclusiveRestState(currentState) || isGroundNapping(maid)) {
                endGroundNap(maid);
                currentState = State.IDLE;
                stateTimer = RANDOM.nextInt(400) + 200;
            }
            MaidMovementControl.end(maid, MaidMovementControl.Reason.LAZY_POSE);
            saveState(maid);
            maid.setXRot(0);
            hasRepliedHit = false;
            lazyModeCheckTimer = 0;
            workCheckTimer = 0;
        }

        private void addFavorabilityAndHearts(EntityMaid maid) {
            maid.getFavorabilityManager().add(1);
            NetworkHandler.sendToNearby(maid, new SpawnParticleMessage(maid.getId(), SpawnParticleMessage.Type.HEART));
        }
    }
}
