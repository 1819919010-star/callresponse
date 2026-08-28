package com.github.JumDa5he.callresponse.compat.npc;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedOwner;
import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** NPC 日常事件的服务器状态机，不接管女仆任务和寻路。 */
public final class NpcEventManager {
    private static final long MANAGER_INTERVAL = 20L;
    private static final long RANDOM_CHECK_INTERVAL = 12_000L;
    private static final double RANDOM_CHANCE = 0.20D;
    /** 梦境只在 TLM 真实睡眠状态中检查，数值集中放置，方便后续平衡。 */
    private static final long DREAM_CHECK_INTERVAL = 200L;
    private static final double NIGHTMARE_CHANCE = 0.05D;
    private static final double GOOD_DREAM_CHANCE = 0.05D;
    private static final double OWNER_HURT_MIN_DAMAGE = 4.0D;
    private static final double OWNER_HURT_RANGE = 16.0D;
    private static final long FOOD_PROMISE_TIMEOUT = 1_200L;
    private static final long PLAYER_HURT_WINDOW = 60L;
    private static final float PLAYER_HURT_THRESHOLD = 10.0F;
    private static final TaskFeedOwner FEED_OWNER_TASK = new TaskFeedOwner();
    private static final String EVENT_BUBBLE_KEY = "bubble.callresponse.npc_event.pending";
    private static final SimpleCommandExceptionType NO_MAID = new SimpleCommandExceptionType(
            Component.translatable("command.callresponse.npc_event.no_maid"));
    private static final SimpleCommandExceptionType NOT_OWNER = new SimpleCommandExceptionType(
            Component.translatable("command.callresponse.npc_event.not_owner"));

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long serverTick = event.getServer().getTickCount();
        if (serverTick % MANAGER_INTERVAL != 0L) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid && eligible(maid)) {
                    tickMaid(maid);
                }
            }
        }
    }

    private static boolean eligible(EntityMaid maid) {
        return maid.isAlive() && maid.isTame() && maid.getOwnerUUID() != null
                && !WanderingMaidData.isSpecial(maid) && !TradingMaidData.isTrading(maid);
    }

    private static void tickMaid(EntityMaid maid) {
        long gameTime = maid.level().getGameTime();
        if (maid.getScheduleDetail() == Activity.WORK) {
            // 每次管理器检查间隔累计一次，完全使用 TLM 当前日程活动。
            NpcEventData.addWorkTicks(maid, MANAGER_INTERVAL);
        }

        checkDreamEvent(maid, gameTime);
        checkFoodPromise(maid, gameTime);
        repairMissingDefinition(maid);
        evaluateConditionalEvents(maid, gameTime);
        if (!NpcEventData.hasCurrent(maid)) {
            promotePending(maid, gameTime);
        }
        if (!NpcEventData.hasCurrent(maid)) {
            tryRandomEvent(maid, gameTime);
        }
    }

    private static void checkDreamEvent(EntityMaid maid, long gameTime) {
        if (!maid.isSleeping()) return;
        long lastCheck = NpcEventData.lastDreamCheck(maid);
        if (lastCheck == 0L || gameTime < lastCheck) {
            NpcEventData.setLastDreamCheck(maid, gameTime);
            return;
        }
        if (gameTime - lastCheck < DREAM_CHECK_INTERVAL) return;
        NpcEventData.setLastDreamCheck(maid, gameTime);

        long day = maid.level().getDayTime() / 24_000L;
        if (NpcEventData.lastDreamEventDay(maid) == day) return;

        NpcEventDefinition nightmare = dreamCandidate(maid, "nightmare", gameTime);
        NpcEventDefinition goodDream = dreamCandidate(maid, "good_dream", gameTime);
        double nightmareChance = nightmare == null ? 0.0D : NIGHTMARE_CHANCE;
        double goodDreamChance = goodDream == null ? 0.0D : GOOD_DREAM_CHANCE;
        double roll = maid.getRandom().nextDouble();
        NpcEventDefinition selected = roll < nightmareChance ? nightmare
                : roll < nightmareChance + goodDreamChance ? goodDream : null;
        if (selected == null) return;

        // 即使只能进入 pending，也立即锁定当天，避免同一晚反复堆积梦境。
        NpcEventData.setLastDreamEventDay(maid, day);
        if (NpcEventData.hasCurrent(maid)) {
            NpcEventData.addPending(maid, selected.id());
        } else {
            startEvent(maid, selected, gameTime);
        }
    }

    private static NpcEventDefinition dreamCandidate(EntityMaid maid, String id, long gameTime) {
        NpcEventDefinition definition = NpcEventLoader.get(id);
        if (definition == null || definition.type() != NpcEventDefinition.Type.CONDITIONAL
                || gameTime < NpcEventData.cooldownUntil(maid, id)
                || !matchesEmotion(maid, definition)) {
            return null;
        }
        return definition;
    }

    private static void checkFoodPromise(EntityMaid maid, long gameTime) {
        if (!NpcEventData.hasFoodPromise(maid)) return;
        ItemStack addedFood = NpcEventData.findNewPromiseFood(maid);
        if (!addedFood.isEmpty()) {
            completeFoodPromise(maid, addedFood);
            return;
        }
        if (gameTime >= NpcEventData.foodPromiseDeadline(maid)) {
            maid.getChatBubbleManager().addTextChatBubble(
                    "bubble.callresponse.npc_event.food_promise_timeout");
            NpcEventData.clearFoodPromise(maid);
        }
    }

    private static void completeFoodPromise(EntityMaid maid, ItemStack food) {
        if (!NpcEventData.hasFoodPromise(maid)) return;
        boolean repeated = "variety".equals(NpcEventData.foodPromiseKind(maid))
                && NpcEventData.promisePreviouslyAte(maid, food);
        maid.getChatBubbleManager().addTextChatBubble(repeated
                ? "bubble.callresponse.npc_event.food_repeat"
                : "bubble.callresponse.npc_event.food_thanks");
        NpcEventData.clearFoodPromise(maid);
    }

    private static void repairMissingDefinition(EntityMaid maid) {
        String current = NpcEventData.current(maid);
        if (!current.isEmpty() && NpcEventLoader.get(current) == null) {
            CallResponseMod.LOGGER.warn("女仆 {} 的当前 NPC 事件 {} 已不存在，自动清理",
                    maid.getUUID(), current);
            NpcEventData.clearCurrent(maid);
        }
        Set<String> pending = NpcEventData.pending(maid);
        if (pending.removeIf(id -> NpcEventLoader.get(id) == null)) {
            NpcEventData.setPending(maid, pending);
        }
    }

    private static void evaluateConditionalEvents(EntityMaid maid, long gameTime) {
        List<NpcEventDefinition> ready = new ArrayList<>();
        for (NpcEventDefinition definition : NpcEventLoader.byType(NpcEventDefinition.Type.CONDITIONAL)) {
            if (gameTime < NpcEventData.cooldownUntil(maid, definition.id())) continue;
            if (!matchesEmotion(maid, definition)) continue;
            if (matchesCondition(maid, definition, gameTime)) ready.add(definition);
        }
        if (ready.isEmpty()) return;
        ready.sort(Comparator.comparingInt(NpcEventDefinition::priority).reversed());

        if (NpcEventData.hasCurrent(maid)) {
            ready.forEach(definition -> {
                if (!definition.id().equals(NpcEventData.current(maid))) {
                    NpcEventData.addPending(maid, definition.id());
                }
            });
            return;
        }

        startEvent(maid, ready.get(0), gameTime);
        for (int i = 1; i < ready.size(); i++) {
            NpcEventData.addPending(maid, ready.get(i).id());
        }
    }

    private static boolean matchesEmotion(EntityMaid maid, NpcEventDefinition definition) {
        NpcEventDefinition.EmotionCondition condition = definition.emotionCondition();
        UUID ownerId = maid.getOwnerUUID();
        if (condition == null || ownerId == null) return true;
        EmotionData.EmotionValues values = EmotionData.get(maid, ownerId);
        int value = condition.type() == NpcEventDefinition.EmotionType.TRUST
                ? values.trust() : values.fear();
        return value >= condition.min() && value <= condition.max();
    }

    private static boolean matchesCondition(EntityMaid maid, NpcEventDefinition definition, long gameTime) {
        double first = definition.conditionValue();
        double second = definition.conditionValue2();
        return switch (definition.condition()) {
            case "overwork" -> NpcEventData.workTicks(maid) >= positiveOr(first, 72_000.0D);
            case "nutrition_shortage" -> HungerData.get(maid) <= positiveOr(first, 20.0D);
            case "food_variety" -> NpcEventData.mealCount(maid) >= positiveOr(first, 6.0D)
                    && NpcEventData.foodTypeCount(maid) <= positiveOr(second, 2.0D);
            // 梦境由真实睡眠状态下的统一抽取处理，不能再被通用条件循环重复生成。
            case "nightmare", "good_dream" -> false;
            case "battle_praise" -> NpcEventData.flag(maid, "battle_praise");
            case "mistake" -> NpcEventData.flag(maid, "mistake");
            case "hunger_high" -> HungerData.get(maid) >= positiveOr(first, 90.0D);
            case "long_time_no_interaction" -> longTimeNoInteraction(maid, gameTime,
                    (long) positiveOr(first, 72_000.0D));
            case "owner_hurt_nearby" -> NpcEventData.flag(maid, "owner_hurt_nearby");
            case "thunderstorm" -> maid.level().isThundering();
            case "has_shareable_food" -> hasShareableFood(maid);
            case "player_hurt_maid" -> NpcEventData.flag(maid, "player_hurt_maid");
            // 首次进入系统后先等待一个完整周期；之后由事件自身的独立冷却继续计时。
            case "self_worth" -> periodicEventDue(maid, definition.id(), gameTime,
                    (long) positiveOr(first, 120_000.0D));
            default -> false;
        };
    }

    private static boolean longTimeNoInteraction(EntityMaid maid, long gameTime, long interval) {
        if (!NpcEventData.hasLastOwnerInteraction(maid)
                || gameTime < NpcEventData.lastOwnerInteraction(maid)) {
            NpcEventData.setLastOwnerInteraction(maid, gameTime);
            return false;
        }
        return gameTime - NpcEventData.lastOwnerInteraction(maid) >= interval;
    }

    private static boolean periodicEventDue(EntityMaid maid, String eventId, long gameTime, long interval) {
        long scheduled = NpcEventData.cooldownUntil(maid, eventId);
        if (scheduled == 0L) {
            NpcEventData.setCooldown(maid, eventId, gameTime + interval);
            return false;
        }
        return gameTime >= scheduled;
    }

    private static double positiveOr(double value, double fallback) {
        return value > 0.0D ? value : fallback;
    }

    private static void tryRandomEvent(EntityMaid maid, long gameTime) {
        long lastCheck = NpcEventData.lastRandomCheck(maid);
        if (lastCheck == 0L || gameTime < lastCheck) {
            NpcEventData.setLastRandomCheck(maid, gameTime);
            return;
        }
        if (gameTime - lastCheck < RANDOM_CHECK_INTERVAL) return;
        NpcEventData.setLastRandomCheck(maid, gameTime);
        if (maid.getRandom().nextDouble() >= RANDOM_CHANCE) return;

        List<NpcEventDefinition> candidates = NpcEventLoader.byType(NpcEventDefinition.Type.RANDOM)
                .stream().filter(definition -> gameTime >= NpcEventData.cooldownUntil(maid, definition.id()))
                .filter(definition -> matchesEmotion(maid, definition))
                .filter(definition -> definition.condition().isEmpty()
                        || matchesCondition(maid, definition, gameTime)).toList();
        int totalWeight = candidates.stream().mapToInt(NpcEventDefinition::weight).sum();
        if (totalWeight <= 0) return;
        int roll = maid.getRandom().nextInt(totalWeight);
        for (NpcEventDefinition definition : candidates) {
            roll -= definition.weight();
            if (roll < 0) {
                startEvent(maid, definition, gameTime);
                return;
            }
        }
    }

    private static void startEvent(EntityMaid maid, NpcEventDefinition definition, long gameTime) {
        if (NpcEventData.hasCurrent(maid)) return;
        NpcEventData.setCurrent(maid, definition.id(), gameTime);
        maid.getChatBubbleManager().addTextChatBubble(EVENT_BUBBLE_KEY);
        CallResponseMod.LOGGER.debug("女仆 {} 生成 NPC 事件 {}", maid.getUUID(), definition.id());
    }

    private static void promotePending(EntityMaid maid, long gameTime) {
        Set<String> pending = NpcEventData.pending(maid);
        NpcEventDefinition selected = pending.stream().map(NpcEventLoader::get)
                .filter(java.util.Objects::nonNull)
                .filter(definition -> gameTime >= NpcEventData.cooldownUntil(maid, definition.id()))
                .max(Comparator.comparingInt(NpcEventDefinition::priority)).orElse(null);
        if (selected == null) return;
        pending.remove(selected.id());
        NpcEventData.setPending(maid, pending);
        startEvent(maid, selected, gameTime);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        // TLM 的 SlabClickEvent 会在 EntityMaid.mobInteract 内处理空魂符并 discard 女仆。
        // 此处必须在发送 GUI 包和取消 Forge 交互前让权，否则客户端收纳、服务端开界面会发生状态分裂。
        if (shouldYieldToTlmInteraction(player, event.getHand())) {
            return;
        }
        if (eligible(maid) && maid.isOwnedBy(player)) {
            recordOwnerInteraction(maid, player);
        }
        if (!eligible(maid) || !maid.isOwnedBy(player) || !NpcEventData.hasCurrent(maid)) {
            return;
        }
        NpcEventDefinition definition = NpcEventLoader.get(NpcEventData.current(maid));
        if (definition == null) return;
        CallResponseMod.CHANNEL.sendTo(new OpenNpcEventS2CPacket(maid.getId(), maid.getUUID(),
                        definition.titleKey(), definition.descriptionKey(),
                        definition.options().stream().map(NpcEventDefinition.Option::textKey).toList()),
                player.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean shouldYieldToTlmInteraction(ServerPlayer player, InteractionHand hand) {
        ItemStack interactionStack = player.getItemInHand(hand);
        return interactionStack.is(InitItems.SMART_SLAB_EMPTY.get());
    }

    public static void handleChoice(ServerPlayer player, UUID maidId, int optionIndex) {
        Entity entity = player.serverLevel().getEntity(maidId);
        if (!(entity instanceof EntityMaid maid) || !eligible(maid) || !maid.isOwnedBy(player)
                || player.distanceToSqr(maid) > 64.0D) return;
        applyChoice(player, maid, optionIndex);
    }

    private static boolean applyChoice(ServerPlayer player, EntityMaid maid, int optionIndex) {
        String currentId = NpcEventData.current(maid);
        NpcEventDefinition definition = NpcEventLoader.get(currentId);
        if (definition == null || optionIndex < 0 || optionIndex >= definition.options().size()) return false;

        NpcEventDefinition.Option option = definition.options().get(optionIndex);
        UUID ownerId = player.getUUID();
        EmotionData.addTrust(maid, ownerId, option.trust());
        EmotionData.addFear(maid, ownerId, option.fear());
        HungerData.add(maid, option.hunger());
        maid.setFavorability(Math.max(0, Math.min(384,
                maid.getFavorability() + option.favor())));

        long gameTime = maid.level().getGameTime();
        performOptionAction(player, maid, option);
        recordOwnerInteraction(maid, player);
        NpcEventData.setCooldown(maid, currentId, gameTime + definition.cooldownTicks());
        consumeCondition(maid, definition.condition());
        NpcEventData.clearCurrent(maid);
        promotePending(maid, gameTime);

        if (!option.responseKey().isEmpty()) {
            maid.getChatBubbleManager().addTextChatBubble(option.responseKey());
        } else if (option.aiResponse()) {
            requestAiResponse(player, maid, definition, option);
        }
        return true;
    }

    private static void consumeCondition(EntityMaid maid, String condition) {
        switch (condition) {
            case "overwork" -> NpcEventData.resetWorkTicks(maid);
            case "food_variety" -> NpcEventData.resetFoodStats(maid);
            case "battle_praise", "mistake", "owner_hurt_nearby", "player_hurt_maid" ->
                    NpcEventData.setFlag(maid, condition, false);
            default -> {
            }
        }
    }

    private static void performOptionAction(ServerPlayer player, EntityMaid maid,
                                            NpcEventDefinition.Option option) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        switch (option.action()) {
            case "dream_heart" -> level.sendParticles(ParticleTypes.HEART,
                    maid.getX(), maid.getY() + maid.getBbHeight() * 0.7D, maid.getZ(),
                    10, 0.5D, 0.35D, 0.5D, 0.03D);
            case "wake_nightmare", "wake_good_dream" -> {
                if (maid.isSleeping()) maid.stopSleeping();
                maid.setInSittingPose(true);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        maid.getX(), maid.getY() + maid.getBbHeight() * 0.8D, maid.getZ(),
                    8, 0.4D, 0.45D, 0.4D, 0.02D);
            }
            case "rest_schedule" -> switchToCurrentRestSchedule(maid);
            case "wait_food" -> NpcEventData.startFoodPromise(maid, "food",
                    level.getGameTime() + FOOD_PROMISE_TIMEOUT);
            case "wait_food_variety" -> NpcEventData.startFoodPromise(maid, "variety",
                    level.getGameTime() + FOOD_PROMISE_TIMEOUT);
            case "throw_into_storm" -> teleportOutside(maid, 10.0D, 16.0D);
            case "share_food" -> feedOwnerOnce(maid, player);
            default -> {
            }
        }
    }

    private static boolean hasShareableFood(EntityMaid maid) {
        if (!(maid.getOwner() instanceof ServerPlayer owner)) return false;
        CombinedInvWrapper inventory = maid.getAvailableInv(true);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (FEED_OWNER_TASK.isFood(inventory.getStackInSlot(slot), owner)) return true;
        }
        return false;
    }

    private static void feedOwnerOnce(EntityMaid maid, ServerPlayer owner) {
        CombinedInvWrapper inventory = maid.getAvailableInv(true);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (FEED_OWNER_TASK.isFood(inventory.getStackInSlot(slot), owner)) slots.add(slot);
        }
        if (slots.isEmpty()) return;
        int slot = slots.get(maid.getRandom().nextInt(slots.size()));
        inventory.setStackInSlot(slot, FEED_OWNER_TASK.feed(inventory.getStackInSlot(slot), owner));
        maid.swing(InteractionHand.MAIN_HAND);
        InitTrigger.MAID_EVENT.trigger(owner, TriggerType.MAID_FEED_PLAYER);
    }

    private static void teleportOutside(EntityMaid maid, double minDistance, double maxDistance) {
        if (!(maid.level() instanceof ServerLevel level)) return;

        // 先固定坐姿并清掉旧的跟随路径，再执行传送，避免刚落地就被跟随任务拉回主人身边。
        maid.setInSittingPose(true);
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0,
                false, false, true));

        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = maid.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + maid.getRandom().nextDouble() * (maxDistance - minDistance);
            double x = maid.getX() + Math.cos(angle) * distance;
            double z = maid.getZ() + Math.sin(angle) * distance;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(x), (int) Math.floor(z));
            net.minecraft.core.BlockPos target = net.minecraft.core.BlockPos.containing(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(target)
                    || !level.getFluidState(target).isEmpty()) continue;
            if (maid.randomTeleport(x, y, z, true)) return;
        }
    }

    private static void switchToCurrentRestSchedule(EntityMaid maid) {
        int time = Math.floorMod((int) (maid.level().getDayTime() % 24_000L), 24_000);
        // 白天前半段选夜班，傍晚以后选日班：当前阶段一定是 REST 或 IDLE，不再是 WORK。
        maid.setSchedule(time < 12_000 ? MaidSchedule.NIGHT : MaidSchedule.DAY);
    }

    private static void requestAiResponse(ServerPlayer player, EntityMaid maid,
                                          NpcEventDefinition definition,
                                          NpcEventDefinition.Option option) {
        Language language = Language.getInstance();
        String title = language.getOrDefault(definition.titleKey());
        String description = language.getOrDefault(definition.descriptionKey());
        String choice = language.getOrDefault(option.textKey());
        String actionContext = switch (option.action()) {
            case "wake_nightmare" -> "女仆刚刚正在做噩梦，主人却故意突然把她吓醒。";
            case "wake_good_dream" -> "女仆刚才正在做一个很开心的美梦，主人却突然叫醒并打断了它。";
            case "throw_into_storm" -> "主人不但没有安慰害怕雷声的女仆，还把她丢到了十格以外的雷雨中。";
            case "share_food" -> "主人同意了女仆分享食物的提议，女仆刚刚亲手喂给主人一份食物。";
            default -> "";
        };
        String prompt = "这是一次女仆日常事件。事件：" + title + "。背景：" + description
                + "。主人选择：" + choice + "。" + actionContext
                + "结合你当前的人格、信任与恐惧状态自然回应主人；"
                + "不超过30字，不提系统、数值或AI，不执行任何动作指令。";
        MaidResponder.processBroadcast(player, List.of(maid), prompt, false);
    }

    public static void recordFood(EntityMaid maid, ItemStack food) {
        if (!maid.level().isClientSide && eligible(maid)) {
            NpcEventData.recordFood(maid, food);
        }
    }

    /** 记录主人主动右键、对话或 NPC 事件处理；经过身边不会触发。 */
    public static void recordOwnerInteraction(EntityMaid maid, ServerPlayer owner) {
        if (maid.level().isClientSide || !eligible(maid) || owner == null
                || !maid.isOwnedBy(owner)) return;
        NpcEventData.setLastOwnerInteraction(maid, maid.level().getGameTime());
        NpcEventData.removePending(maid, "long_time_no_interaction");
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof EntityMaid maid && eligible(maid)
                && event.getEntity() != maid && event.getEntity() != maid.getOwner()) {
            NpcEventData.setFlag(maid, "battle_praise", true);
        }
        if (event.getEntity() instanceof EntityMaid deadMaid) {
            NpcEventData.remove(deadMaid);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        LivingEntity victim = event.getEntity();
        if (attacker instanceof EntityMaid maid && eligible(maid)
                && maid.isOwnedBy(victim)) {
            NpcEventData.setFlag(maid, "mistake", true);
        }
    }

    /** LivingDamageEvent 位于减伤结算后，只记录真正落到主人血量上的最终伤害。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOwnerDamaged(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0F) return;
        if (event.getEntity() instanceof EntityMaid hurtMaid) {
            recordPlayerDamageToMaid(hurtMaid, event);
        }
        if (!(event.getEntity() instanceof ServerPlayer owner)
                || event.getAmount() < OWNER_HURT_MIN_DAMAGE) return;
        Entity causing = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        ServerLevel level = owner.serverLevel();
        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class,
                owner.getBoundingBox().inflate(OWNER_HURT_RANGE), NpcEventManager::eligible)) {
            if (owner.distanceToSqr(maid) > OWNER_HURT_RANGE * OWNER_HURT_RANGE
                    || !maid.isOwnedBy(owner) || causing == maid || direct == maid) continue;
            NpcEventDefinition definition = NpcEventLoader.get("owner_hurt_nearby");
            if (definition != null && level.getGameTime() >= NpcEventData.cooldownUntil(maid, definition.id())) {
                NpcEventData.setFlag(maid, "owner_hurt_nearby", true);
            }
        }
    }

    private static void recordPlayerDamageToMaid(EntityMaid maid, LivingDamageEvent event) {
        if (!eligible(maid) || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !maid.isOwnedBy(player)) return;
        NpcEventDefinition definition = NpcEventLoader.get("player_hurt_maid");
        long gameTime = maid.level().getGameTime();
        if (definition == null || gameTime < NpcEventData.cooldownUntil(maid, definition.id())) {
            NpcEventData.clearOwnerDamageWindow(maid);
            return;
        }
        float total = NpcEventData.addOwnerDamage(maid, gameTime, event.getAmount(), PLAYER_HURT_WINDOW);
        if (total > PLAYER_HURT_THRESHOLD) {
            NpcEventData.setFlag(maid, "player_hurt_maid", true);
            NpcEventData.clearOwnerDamageWindow(maid);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("callresponse")
                .then(Commands.literal("npc_event")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> listEvents(context.getSource())))
                        .then(Commands.literal("trigger")
                                .then(Commands.argument("event_id", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                NpcEventLoader.all().stream().map(NpcEventDefinition::id), builder))
                                        .executes(context -> triggerEvent(context.getSource(), null,
                                                StringArgumentType.getString(context, "event_id")))
                                        .then(Commands.argument("maid", EntityArgument.entity())
                                                .executes(context -> triggerEvent(context.getSource(),
                                                        EntityArgument.getEntity(context, "maid"),
                                                        StringArgumentType.getString(context, "event_id"))))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearEvent(context.getSource(), null))
                                .then(Commands.argument("maid", EntityArgument.entity())
                                        .executes(context -> clearEvent(context.getSource(),
                                                EntityArgument.getEntity(context, "maid")))))
                        .then(Commands.literal("resolve")
                                .then(Commands.argument("option", IntegerArgumentType.integer(1))
                                        .executes(context -> resolveEvent(context.getSource(), null,
                                                IntegerArgumentType.getInteger(context, "option")))
                                        .then(Commands.argument("maid", EntityArgument.entity())
                                                .executes(context -> resolveEvent(context.getSource(),
                                                        EntityArgument.getEntity(context, "maid"),
                                                        IntegerArgumentType.getInteger(context, "option"))))))));
    }

    private static int listEvents(CommandSourceStack source) {
        String ids = NpcEventLoader.all().stream().map(NpcEventDefinition::id).sorted()
                .reduce((left, right) -> left + ", " + right).orElse("-");
        source.sendSuccess(() -> Component.translatable("command.callresponse.npc_event.list", ids), false);
        return NpcEventLoader.all().size();
    }

    private static int triggerEvent(CommandSourceStack source, Entity selected, String eventId)
            throws CommandSyntaxException {
        EntityMaid maid = commandMaid(source, selected);
        NpcEventDefinition definition = NpcEventLoader.get(eventId);
        if (definition == null) {
            source.sendFailure(Component.translatable("command.callresponse.npc_event.unknown", eventId));
            return 0;
        }
        NpcEventData.clearCurrent(maid);
        startEvent(maid, definition, maid.level().getGameTime());
        source.sendSuccess(() -> Component.translatable("command.callresponse.npc_event.triggered",
                eventId, maid.getDisplayName()), true);
        return 1;
    }

    private static int clearEvent(CommandSourceStack source, Entity selected) throws CommandSyntaxException {
        EntityMaid maid = commandMaid(source, selected);
        String currentId = NpcEventData.current(maid);
        NpcEventDefinition definition = NpcEventLoader.get(currentId);
        if (definition != null) {
            NpcEventData.setCooldown(maid, currentId,
                    maid.level().getGameTime() + definition.cooldownTicks());
            consumeCondition(maid, definition.condition());
        }
        NpcEventData.clearCurrent(maid);
        NpcEventData.setPending(maid, Collections.emptySet());
        source.sendSuccess(() -> Component.translatable("command.callresponse.npc_event.cleared",
                maid.getDisplayName()), true);
        return 1;
    }

    private static int resolveEvent(CommandSourceStack source, Entity selected, int oneBasedOption)
            throws CommandSyntaxException {
        EntityMaid maid = commandMaid(source, selected);
        ServerPlayer player = source.getPlayerOrException();
        if (!applyChoice(player, maid, oneBasedOption - 1)) {
            source.sendFailure(Component.translatable("command.callresponse.npc_event.invalid_option",
                    oneBasedOption));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.callresponse.npc_event.resolved",
                oneBasedOption, maid.getDisplayName()), true);
        return 1;
    }

    private static EntityMaid commandMaid(CommandSourceStack source, Entity selected)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EntityMaid maid;
        if (selected == null) {
            maid = player.serverLevel().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(16.0D), candidate -> candidate.isAlive()
                                    && candidate.isTame() && candidate.isOwnedBy(player))
                    .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElseThrow(NO_MAID::create);
        } else if (selected instanceof EntityMaid selectedMaid) {
            maid = selectedMaid;
        } else {
            throw NO_MAID.create();
        }
        if (!eligible(maid) || !maid.isOwnedBy(player)) throw NOT_OWNER.create();
        return maid;
    }
}
