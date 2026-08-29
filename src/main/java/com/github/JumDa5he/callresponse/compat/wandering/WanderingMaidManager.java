package com.github.JumDa5he.callresponse.compat.wandering;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.gui.OpenWanderingMaidRequestS2CPacket;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import com.github.JumDa5he.callresponse.mixin.accessor.EntityMaidTameInvoker;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class WanderingMaidManager {
    public static final String DEFAULT_MODEL = "geckolib:winefox";
    private static final int SEARCH_MIN_RADIUS = 24;
    private static final int SEARCH_MAX_RADIUS = 48;
    private static final int MAX_ARRIVALS = 3;
    private static final double ARRIVE_DISTANCE = 1.5;
    private static final double RETRY_DISTANCE = 5.0;
    private static final double LEAVE_DISTANCE = 16.0;
    private static final long NO_PROGRESS_TIMEOUT = 20L * 20L;
    private static final long APPROACH_TIMEOUT = 20L * 90L;
    private static final double NEARBY_EMOTION_RADIUS = 16.0;
    private static final int SPAWN_GLOW_DURATION = 20 * 60;


    public static boolean blocksNormalInteraction(EntityMaid maid) {
        return WanderingMaidData.isSpecial(maid);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWanderingMaidTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide
                && WanderingMaidData.isSpecial(maid) && !WanderingMaidData.mayAccept(maid)) {
            enforceWildState(maid);
        }
    }

    private static void enforceWildState(EntityMaid maid) {
        maid.setTame(false);
        maid.setOwnerUUID(null);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long gameTime = level.getGameTime();
        WanderingMaidSavedData data = WanderingMaidSavedData.get(level);

        tickAllWanderingMaids(level, data, gameTime);

        long interval = configuredIntervalTicks();
        if (interval <= 0) {
            return;
        }
        if (data.nextAttemptTick() <= 0 || data.nextAttemptTick() > gameTime + interval) {
            data.setNextAttemptTick(gameTime + interval);
        }
        if (gameTime < data.nextAttemptTick()) {
            return;
        }
        data.setNextAttemptTick(gameTime + interval);

        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return;
        }
        if (level.getRandom().nextInt(100) >= EmotionPassiveConfig.WANDERING_MAID_SPAWN_CHANCE.get()) {
            data.recordSpawnResult(false);
            return;
        }

        List<ServerPlayer> candidates = level.players().stream()
                .filter(player -> !player.isSpectator() && player.isAlive())
                .toList();
        if (candidates.isEmpty()) {
            data.recordSpawnResult(false);
            return;
        }
        boolean spawnedAny = false;
        for (ServerPlayer player : candidates) {
            spawnedAny |= spawnEventForPlayer(player, false) > 0;
        }
        data.recordSpawnResult(spawnedAny);
    }

    private static long configuredIntervalTicks() {
        return EmotionPassiveConfig.WANDERING_MAID_INTERVAL_MINUTES.get() * 60L * 20L;
    }

    public static boolean spawnForPlayer(ServerPlayer player, boolean commandTriggered) {
        return spawnEventForPlayer(player, commandTriggered) > 0;
    }

    private static int spawnEventForPlayer(ServerPlayer player, boolean commandTriggered) {
        int successes = 0;
        int count = EmotionPassiveConfig.WANDERING_MAID_COUNT.get();
        for (int i = 0; i < count; i++) {
            if (spawnSingleForPlayer(player, commandTriggered && i == 0)) {
                successes++;
            }
        }
        return successes;
    }

    private static boolean spawnSingleForPlayer(ServerPlayer player, boolean commandTriggered) {
        if (!(player.level() instanceof ServerLevel level) || player.getServer() == null
                || level != player.getServer().overworld()) {
            if (commandTriggered) {
                player.sendSystemMessage(Component.translatable("message.callresponse.wandering.overworld_only"));
            }
            return false;
        }
        WanderingMaidSavedData data = WanderingMaidSavedData.get(level);
        BlockPos spawnPos = findSpawnPosition(level, player.blockPosition());
        if (spawnPos == null) {
            if (commandTriggered) {
                player.sendSystemMessage(Component.translatable("message.callresponse.wandering.no_spawn_position"));
            }
            return false;
        }
        EntityMaid maid = EntityMaid.TYPE.create(level);
        if (maid == null) {
            return false;
        }
        maid.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                level.getRandom().nextFloat() * 360.0f, 0.0f);
        if (!level.noCollision(maid)) {
            return false;
        }
        maid.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        maid.setModelId(selectModel(data, player.getUUID(), level));
        maid.setPersistenceRequired();
        WanderingMaidData.initialize(maid, player.getUUID(), level.getGameTime());
        maid.addEffect(new MobEffectInstance(MobEffects.GLOWING, SPAWN_GLOW_DURATION, 0, false, false));
        if (!level.addFreshEntity(maid)) {
            return false;
        }
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.WANDERING_WAIT,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
        if (commandTriggered) {
            player.sendSystemMessage(Component.translatable("message.callresponse.wandering.spawned"));
        }
        return true;
    }

    private static String selectModel(WanderingMaidSavedData data, UUID player, ServerLevel level) {
        Set<String> available = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet();
        List<String> pool = data.skinPool(player).stream().filter(available::contains).toList();
        if (!pool.isEmpty()) {
            return pool.get(level.getRandom().nextInt(pool.size()));
        }
        return available.contains(DEFAULT_MODEL) ? DEFAULT_MODEL : available.stream().findFirst().orElse(DEFAULT_MODEL);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = Mth.nextInt(level.getRandom(), SEARCH_MIN_RADIUS, SEARCH_MAX_RADIUS);
            int x = center.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                continue;
            }
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                continue;
            }
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
                continue;
            }
            if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                return pos;
            }
        }
        return null;
    }

    private static void tickAllWanderingMaids(ServerLevel level, WanderingMaidSavedData savedData, long gameTime) {
        for (EntityMaid maid : level.getEntities(EntityMaid.TYPE,
                maid -> maid.isAlive() && WanderingMaidData.isSpecial(maid))) {
            // Second server-side route: force exactly the two owner fields clear every tick.
            if (!WanderingMaidData.mayAccept(maid)) {
                enforceWildState(maid);
            }
            tickWanderingMaid(level, maid, savedData, gameTime);
        }
    }

    private static void tickWanderingMaid(ServerLevel level, EntityMaid maid,
                                           WanderingMaidSavedData savedData, long gameTime) {
        WanderingMaidState state = WanderingMaidData.state(maid);
        if (state == WanderingMaidState.REJECTED) {
            return;
        }
        UUID targetId = WanderingMaidData.target(maid).orElse(null);
        ServerPlayer player = targetId == null ? null : level.getServer().getPlayerList().getPlayer(targetId);
        if (player == null || player.level() != level || !player.isAlive() || player.isSpectator()) {
            expire(maid, savedData);
            return;
        }

        switch (state) {
            case APPROACHING, RETRYING -> tickApproach(maid, player, savedData, gameTime);
            case WAITING -> tickWaiting(maid, player, savedData, gameTime);
            case LEAVING -> tickLeaving(maid, player, savedData, gameTime);
            case REJECTED -> { }
        }
    }

    private static void tickApproach(EntityMaid maid, ServerPlayer player, WanderingMaidSavedData savedData, long gameTime) {
        if (maid.closerThan(player, ARRIVE_DISTANCE)) {
            stopNavigation(maid);
            maid.setInSittingPose(true);
            maid.setBegging(true);
            WanderingMaidData.incrementArrivals(maid);
            WanderingMaidData.setState(maid, WanderingMaidState.WAITING, gameTime);
            maid.getChatBubbleManager().addTextChatBubble("你能收留我吗？");
            return;
        }
        if (gameTime - WanderingMaidData.stateSince(maid) > APPROACH_TIMEOUT) {
            expire(maid, savedData);
            return;
        }
        double distanceSqr = maid.distanceToSqr(player);
        if (distanceSqr + 0.25 < WanderingMaidData.closestDistance(maid)) {
            WanderingMaidData.recordProgress(maid, distanceSqr, gameTime);
        } else if (gameTime - WanderingMaidData.lastProgress(maid) > NO_PROGRESS_TIMEOUT) {
            expire(maid, savedData);
            return;
        }
        maid.setInSittingPose(false);
        maid.setBegging(false);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new EntityTracker(player, false), 0.5f, 2));
        maid.getNavigation().moveTo(player, 0.5d);
    }

    private static void tickWaiting(EntityMaid maid, ServerPlayer player, WanderingMaidSavedData savedData, long gameTime) {
        stopNavigation(maid);
        maid.setInSittingPose(true);
        maid.setBegging(true);
        maid.getLookControl().setLookAt(player, 30.0f, 30.0f);
        if (!maid.closerThan(player, RETRY_DISTANCE)) {
            maid.setInSittingPose(false);
            maid.setBegging(false);
            if (WanderingMaidData.arrivals(maid) >= MAX_ARRIVALS) {
                rejectPermanently(maid, savedData, gameTime);
            } else {
                WanderingMaidData.setState(maid, WanderingMaidState.RETRYING, gameTime);
            }
        }
    }

    private static void tickLeaving(EntityMaid maid, ServerPlayer player, WanderingMaidSavedData savedData, long gameTime) {
        maid.setInSittingPose(false);
        maid.setBegging(false);
        if (!maid.closerThan(player, LEAVE_DISTANCE)
                || gameTime - WanderingMaidData.stateSince(maid) > NO_PROGRESS_TIMEOUT) {
            stopNavigation(maid);
            WanderingMaidData.setState(maid, WanderingMaidState.REJECTED, gameTime);
            return;
        }
        Vec3 away = maid.position().subtract(player.position());
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(1, 0, 0);
        }
        Vec3 destination = maid.position().add(away.normalize().scale(LEAVE_DISTANCE));
        BlockPos surface = levelSurface((ServerLevel) maid.level(), BlockPos.containing(destination));
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(surface));
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(surface), 0.5f, 1));
        maid.getNavigation().moveTo(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5, 0.5d);
    }

    private static BlockPos levelSurface(ServerLevel level, BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
    }

    private static void stopNavigation(EntityMaid maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    private static void expire(EntityMaid maid, WanderingMaidSavedData savedData) {
        rejectPermanently(maid, savedData, maid.level().getGameTime());
    }

    private static void rejectPermanently(EntityMaid maid, WanderingMaidSavedData savedData, long gameTime) {
        stopNavigation(maid);
        MaidMovementControl.end(maid, MaidMovementControl.Reason.WANDERING_WAIT);
        maid.setInSittingPose(false);
        maid.setBegging(false);
        WanderingMaidData.setAcceptAuthorized(maid, false);
        WanderingMaidData.setState(maid, WanderingMaidState.REJECTED, gameTime);
        enforceWildState(maid);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof EntityMaid maid)
                || !WanderingMaidData.isSpecial(maid)) {
            return;
        }
        // Repair any pseudo-tame owner/sit state before handling this click.
        if (!WanderingMaidData.mayAccept(maid)) {
            enforceWildState(maid);
        }
        WanderingMaidState state = WanderingMaidData.state(maid);
        if (state == WanderingMaidState.REJECTED) {
            denyInteraction(event, player, "message.callresponse.wandering.rejected");
            return;
        }
        UUID target = WanderingMaidData.target(maid).orElse(null);
        if (!player.getUUID().equals(target)) {
            denyInteraction(event, player, "message.callresponse.wandering.other_player");
            return;
        }
        if (state != WanderingMaidState.WAITING) {
            denyInteraction(event, player, "message.callresponse.wandering.approaching");
            return;
        }
        CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenWanderingMaidRequestS2CPacket(maid.getId(), maid.getUUID(), maid.getName()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void denyInteraction(PlayerInteractEvent.EntityInteract event, ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static void handleDecision(ServerPlayer player, UUID maidId, boolean accept) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(maidId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !WanderingMaidData.isSpecial(maid)
                || WanderingMaidData.state(maid) != WanderingMaidState.WAITING
                || !WanderingMaidData.target(maid).filter(player.getUUID()::equals).isPresent()
                || !maid.closerThan(player, 7.0)) {
            return;
        }
        if (player.getServer() == null) {
            return;
        }
        WanderingMaidSavedData savedData = WanderingMaidSavedData.get(player.getServer().overworld());
        if (accept) {
            acceptMaid(player, maid, savedData);
        } else {
            maid.setInSittingPose(false);
            maid.setBegging(false);
            WanderingMaidData.setState(maid, WanderingMaidState.LEAVING, level.getGameTime());
        }
    }

    private static void acceptMaid(ServerPlayer player, EntityMaid maid, WanderingMaidSavedData savedData) {
        InteractionResult result;
        WanderingMaidData.setAcceptAuthorized(maid, true);
        try {
            result = ((EntityMaidTameInvoker) maid)
                    .callresponse$invokeTameMaid(new ItemStack(Items.CAKE), player);
        } finally {
            WanderingMaidData.setAcceptAuthorized(maid, false);
        }
        if (!result.consumesAction() || !maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.callresponse.wandering.tame_failed"));
            return;
        }
        stopNavigation(maid);
        MaidMovementControl.end(maid, MaidMovementControl.Reason.WANDERING_WAIT);
        maid.setInSittingPose(false);
        maid.setBegging(false);
        EmotionData.set(maid, player.getUUID(), 35, 15);
        String newcomerName = maid.getName().getString();
        AABB nearby = maid.getBoundingBox().inflate(NEARBY_EMOTION_RADIUS);
        List<EntityMaid> witnesses = new ArrayList<>();
        for (EntityMaid other : maid.level().getEntitiesOfClass(EntityMaid.class, nearby)) {
            if (other != maid && other.isAlive() && other.isOwnedBy(player)) {
                EmotionData.addTrust(other, player.getUUID(), 3);
                EmotionData.addFear(other, player.getUUID(), -2);
                witnesses.add(other);
            }
        }
        WanderingMaidData.clear(maid);
        player.sendSystemMessage(Component.translatable("message.callresponse.wandering.accepted", maid.getName()));
        giveRandomItems(player, (ServerLevel) maid.level(), 2);
        for (EntityMaid witness : witnesses) {
            String prompt = "主人刚刚收留了在外流浪的女仆“" + newcomerName
                    + "”。你亲眼见证她成为家里新的伙伴，因此感到高兴和安心。"
                    + "请结合你对主人的感情和你自己的性格，自然地欢迎她，或向主人表达此刻的感受。";
            MaidResponder.processBroadcast(player, List.of(witness), prompt, false);
        }
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !WanderingMaidData.isSpecial(maid)
                || !(event.getSource().getEntity() instanceof ServerPlayer)
                || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        for (Item item : selectRandomItems(level, 3 + level.getRandom().nextInt(3))) {
            ItemEntity drop = new ItemEntity(level, maid.getX(), maid.getY() + 0.4, maid.getZ(), item.getDefaultInstance());
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
    }

    private static void giveRandomItems(ServerPlayer player, ServerLevel level, int count) {
        for (Item item : selectRandomItems(level, count)) {
            ItemStack gift = item.getDefaultInstance();
            if (!player.addItem(gift)) {
                player.drop(gift, false);
            }
        }
    }

    private static List<Item> selectRandomItems(ServerLevel level, int requestedCount) {
        Set<String> blacklist = normalizeItemIdList(EmotionPassiveConfig.WANDERING_MAID_DROP_BLACKLIST.get());
        Set<String> whitelist = normalizeItemIdList(EmotionPassiveConfig.WANDERING_MAID_DROP_WHITELIST.get());
        List<Item> candidates = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack stack = item.getDefaultInstance();
            if (item != Items.AIR && !stack.isEmpty() && stack.isItemEnabled(level.enabledFeatures())
                    && isAllowedRandomItem(item, blacklist, whitelist)) {
                candidates.add(item);
            }
        }
        List<Item> selected = new ArrayList<>();
        int count = Math.min(candidates.size(), requestedCount);
        for (int i = 0; i < count; i++) {
            selected.add(candidates.remove(level.getRandom().nextInt(candidates.size())));
        }
        return selected;
    }

    /** 黑名单永远优先；白名单为空时只排除黑名单，白名单非空时只允许其中的物品。 */
    private static boolean isAllowedRandomItem(Item item, Set<String> blacklist, Set<String> whitelist) {
        var key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) {
            return false;
        }
        String itemId = key.toString().toLowerCase(Locale.ROOT);
        if (blacklist.contains(itemId)) {
            return false;
        }
        return whitelist.isEmpty() || whitelist.contains(itemId);
    }

    private static Set<String> normalizeItemIdList(List<? extends String> itemIds) {
        Set<String> normalized = new HashSet<>();
        for (String itemId : itemIds) {
            if (itemId != null && !itemId.isBlank()) {
                normalized.add(itemId.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(event.getLevel() instanceof ServerLevel level)
                || level != level.getServer().overworld() || !WanderingMaidData.isSpecial(maid)) {
            return;
        }
        enforceWildState(maid);
        if (WanderingMaidData.state(maid) != WanderingMaidState.REJECTED
                && !MaidMovementControl.isActive(maid, MaidMovementControl.Reason.WANDERING_WAIT)) {
            // 旧版本可能把等待坐姿直接写入 Sitting；这是明确的附属临时状态，可安全归零后建立基线。
            maid.setInSittingPose(false);
            MaidMovementControl.begin(maid, MaidMovementControl.Reason.WANDERING_WAIT,
                    java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("callresponse")
                .then(Commands.literal("wanderingmaid")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return spawnForPlayer(player, true) ? 1 : 0;
                                })
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            return spawnForPlayer(player, true) ? 1 : 0;
                                        })))));
    }
}
