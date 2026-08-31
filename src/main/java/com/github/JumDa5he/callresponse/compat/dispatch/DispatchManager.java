package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.compat.block.RewardBoxBlockEntity;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.state.MaidPathCommand;
import com.github.JumDa5he.callresponse.config.DispatchConfig;
import com.github.JumDa5he.callresponse.mixin.accessor.CompositeEntryBaseAccessor;
import com.github.JumDa5he.callresponse.mixin.accessor.LootItemAccessor;
import com.github.JumDa5he.callresponse.mixin.accessor.LootPoolAccessor;
import com.github.JumDa5he.callresponse.mixin.accessor.LootTableAccessor;
import com.github.JumDa5he.callresponse.mixin.accessor.NestedLootTableAccessor;
import com.github.JumDa5he.callresponse.mixin.accessor.TagEntryAccessor;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class DispatchManager {
    private static final String RETURN_DIALOGUE = "callresponse$dispatch_return_dialogue";
    private static final String RETURN_OWNER = "callresponse$dispatch_return_owner";
    private static final String RETURN_TITLE = "callresponse$dispatch_return_title";
    private static final String RETURN_DESC = "callresponse$dispatch_return_desc";
    private static long lastCheck;
    private static final Map<UUID, Long> LAST_RETURN_DIALOGUE = new ConcurrentHashMap<>();

    private DispatchManager() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("callresponse")
                .then(MaidPathCommand.node())
                .then(Commands.literal("dispatch")
                        .then(Commands.literal("open")
                                .executes(context -> openCommand(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("refresh")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> refresh(context.getSource())))
                        .then(Commands.literal("clear_cooldowns")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> clearCooldowns(context.getSource())))
                        .then(Commands.literal("finish")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> finishOne(context.getSource())))));
    }

    private static int openCommand(CommandSourceStack source) throws CommandSyntaxException {
        open(source.getPlayerOrException());
        return 1;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DispatchData data = DispatchData.get(player.level().getServer());
        DispatchData.EventPool pool = ensurePool(data, player);
        int loaded = DispatchEventLoader.all().size();
        int available = pool.work().size() + pool.play().size();
        int active = data.active(player.getUUID()).size();
        source.sendSuccess(() -> Component.translatable("command.callresponse.dispatch.status",
                loaded, available, active, DispatchConfig.MAX_ACTIVE_DISPATCHES.get()), false);
        return active;
    }

    private static int refresh(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DispatchData.EventPool pool = refreshPool(DispatchData.get(player.level().getServer()), player);
        source.sendSuccess(() -> Component.translatable("command.callresponse.dispatch.refreshed",
                pool.work().size(), pool.play().size()), true);
        open(player);
        return pool.work().size() + pool.play().size();
    }

    private static int clearCooldowns(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DispatchData data = DispatchData.get(player.level().getServer());
        int removed = data.clearCooldowns(player.getUUID());
        refreshPool(data, player);
        source.sendSuccess(() -> Component.translatable("command.callresponse.dispatch.cooldowns_cleared", removed), true);
        return removed;
    }

    /** 立即正常结算执行者名下最早派出的一只女仆，保留全部奖励与情感结算。 */
    private static int finishOne(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DispatchData data = DispatchData.get(player.level().getServer());
        List<DispatchData.DispatchRecord> candidates = new ArrayList<>(data.active(player.getUUID()));
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
            Set<UUID> known = new HashSet<>();
            candidates.forEach(record -> known.add(record.dispatchId()));
            for (DispatchData.DispatchRecord record : DispatchGlobalStore.owner(player.getUUID(), player.level().getServer().registryAccess())) {
                if (known.add(record.dispatchId())) {
                    candidates.add(record);
                }
            }
        }
        DispatchData.DispatchRecord record = candidates.stream()
                .min(Comparator.comparingLong(DispatchData.DispatchRecord::startAt)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.translatable("message.callresponse.dispatch.none"));
            return 0;
        }

        // 调试结算永远在执行指令的当前存档回收，优先当前存档最近的报酬箱。
        DispatchData.BoxRef targetBox = nearestValidBox(data, player.level().getServer(), player.getUUID(),
                (ServerLevel) player.level(), player.blockPosition());
        DispatchData.DispatchRecord currentWorldRecord = new DispatchData.DispatchRecord(
                record.dispatchId(), record.ownerId(), record.maidId(), record.maidNbt(), record.eventId(),
                record.eventTitle(), record.eventDescription(), record.category(), record.startAt(),
                System.currentTimeMillis(), record.originDimension(), record.originPos(),
                targetBox == null ? "" : targetBox.dimension(), targetBox == null ? null : targetBox.pos(),
                record.trust(), record.fear(), record.favor(), record.hunger(), record.rewards(),
                record.modelId(), record.displayName());
        if (!restore(player.level().getServer(), currentWorldRecord, player, true)) {
            source.sendFailure(Component.translatable("message.callresponse.dispatch.restore_failed"));
            return 0;
        }
        data.remove(player.getUUID(), record.dispatchId());
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
            DispatchGlobalStore.remove(record.dispatchId(), player.level().getServer().registryAccess());
        }
        source.sendSuccess(() -> Component.translatable("message.callresponse.dispatch.finished",
                record.displayName().getString(), record.eventTitle().getString()), false);
        return 1;
    }

    public static void open(ServerPlayer player) {
        DispatchData data = DispatchData.get(player.level().getServer());
        DispatchData.EventPool pool = ensurePool(data, player);
        List<OpenDispatchScreenS2CPacket.EventInfo> events = new ArrayList<>();
        appendEvents(events, pool.work(), player);
        appendEvents(events, pool.play(), player);
        List<OpenDispatchScreenS2CPacket.MaidInfo> maids = ownedLoadedMaids(player).stream()
                .filter(maid -> !data.isDispatched(maid.getUUID()))
                .map(maid -> new OpenDispatchScreenS2CPacket.MaidInfo(maid.getUUID(), maid.getDisplayName().getString(), maid.getModelId()))
                .toList();
        List<OpenDispatchScreenS2CPacket.ActiveInfo> active = data.active(player.getUUID()).stream()
                .map(record -> new OpenDispatchScreenS2CPacket.ActiveInfo(record.dispatchId(), record.displayName(), record.modelId(), record.eventTitle(), record.finishAt()))
                .toList();
        PacketDistributor.sendToPlayer(player,
                new OpenDispatchScreenS2CPacket(events, maids, active, DispatchConfig.MAX_ACTIVE_DISPATCHES.get()));
    }

    public static void handle(ServerPlayer player, DispatchActionC2SPacket.Action action, String eventId, UUID targetId) {
        if (action == DispatchActionC2SPacket.Action.REQUEST) {
            open(player);
            return;
        }
        if (action == DispatchActionC2SPacket.Action.RECALL) {
            DispatchData data = DispatchData.get(player.level().getServer());
            data.active(player.getUUID()).stream().filter(record -> record.dispatchId().equals(targetId)).findFirst().ifPresent(record -> {
                if (restore(player.level().getServer(), record, player, false)) {
                    data.remove(player.getUUID(), targetId);
                    if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
                        DispatchGlobalStore.remove(targetId, player.level().getServer().registryAccess());
                    }
                }
            });
            open(player);
            return;
        }
        start(player, eventId, targetId);
    }

    private static void start(ServerPlayer player, String eventId, UUID maidId) {
        DispatchData data = DispatchData.get(player.level().getServer());
        if (data.active(player.getUUID()).size() >= DispatchConfig.MAX_ACTIVE_DISPATCHES.get()) {
            player.sendSystemMessage(Component.translatable("message.callresponse.dispatch.full"));
            return;
        }
        DispatchData.EventPool pool = ensurePool(data, player);
        if (!pool.work().contains(eventId) && !pool.play().contains(eventId)) {
            player.sendSystemMessage(Component.translatable("message.callresponse.dispatch.event_unavailable"));
            open(player);
            return;
        }
        DispatchEventDefinition event = DispatchEventLoader.get(eventId);
        EntityMaid maid = findMaid(player.level().getServer(), maidId);
        if (event == null || maid == null || !maid.isAlive() || !maid.isTame()
                || !player.getUUID().equals((maid.getOwner() == null ? null : maid.getOwner().getUUID())) || data.isDispatched(maidId)) {
            player.sendSystemMessage(Component.translatable("message.callresponse.dispatch.maid_unavailable"));
            open(player);
            return;
        }
        TagValueOutput maidOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, maid.registryAccess());
        maid.saveWithoutId(maidOutput);
        CompoundTag maidNbt = maidOutput.buildResult();
        long now = System.currentTimeMillis();
        DispatchData.BoxRef box = nearestValidBox(data, player.level().getServer(), player.getUUID(), (ServerLevel) maid.level(), maid.blockPosition());
        var emotion = event.emotion();
        DispatchData.DispatchRecord record = new DispatchData.DispatchRecord(UUID.randomUUID(), player.getUUID(), maid.getUUID(), maidNbt,
                eventId, event.title(), event.description(), event.category().serializedName(), now, now + event.rollDurationMillis(maid.getRandom()),
                maid.level().dimension().identifier().toString(), maid.blockPosition(), box == null ? "" : box.dimension(), box == null ? null : box.pos(),
                emotion.trust(), emotion.fear(), emotion.favor(), emotion.hunger(),
                event.rewards(), maid.getModelId(), maid.getDisplayName());
        data.add(record);
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
            DispatchGlobalStore.put(record, player.level().getServer().registryAccess());
        }
        data.setCooldown(player.getUUID(), eventId, now + (long) event.cooldownMin() * 60_000L);
        maid.discard();
        player.sendSystemMessage(Component.translatable("message.callresponse.dispatch.started", record.displayName().getString(), record.eventTitle().getString()));
        // 派出去后立即换一批事件，避免界面一直停留在同一批（刚派过的事件处于冷却，不会立刻重复出现）
        refreshPool(data, player);
        open(player);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long ticks = server.overworld().getGameTime();
        if (ticks - lastCheck < 20) {
            return;
        }
        lastCheck = ticks;
        DispatchData data = DispatchData.get(server);
        long now = System.currentTimeMillis();
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
            synchronizeGlobal(data, server);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (DispatchData.DispatchRecord record : data.active(player.getUUID())) {
                if (record.finishAt() <= now && restore(server, record, player, true)) {
                    data.remove(player.getUUID(), record.dispatchId());
                    if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
                        DispatchGlobalStore.remove(record.dispatchId(), server.registryAccess());
                    }
                }
            }
        }
        // 多人服主人离线时也照常归还到原箱/原坐标。
        Set<UUID> online = new HashSet<>();
        server.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
        data.owners().stream().filter(owner -> !online.contains(owner)).forEach(owner -> {
            for (DispatchData.DispatchRecord record : data.active(owner)) {
                if (record.finishAt() <= now && restore(server, record, null, true)) {
                    data.remove(owner, record.dispatchId());
                    if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
                        DispatchGlobalStore.remove(record.dispatchId(), server.registryAccess());
                    }
                }
            }
        });
        scanReturnDialogues(server);
    }

    private static boolean restore(MinecraftServer server, DispatchData.DispatchRecord record, ServerPlayer player, boolean reward) {
        ServerLevel level = null;
        BlockPos base = null;
        RewardBoxBlockEntity box = null;
        if (!record.boxDimension().isEmpty() && record.boxPos() != null) {
            level = level(server, record.boxDimension());
            if (level != null) {
                level.getChunkAt(record.boxPos());
            }
            if (level != null && level.getBlockEntity(record.boxPos()) instanceof RewardBoxBlockEntity candidate
                    && record.ownerId().equals(candidate.getOwnerId())) {
                box = candidate;
                base = record.boxPos();
            }
        }
        if (base == null && player != null) {
            level = (ServerLevel) player.level();
            base = player.blockPosition().offset(2, 0, 0);
        }
        if (base == null) {
            level = level(server, record.originDimension());
            base = record.originPos();
            if (level != null) {
                level.getChunkAt(base);
            }
        }
        if (level == null) {
            return false;
        }
        EntityMaid maid = InitEntities.MAID.get().create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (maid == null) {
            return false;
        }
        maid.load(TagValueInput.create(ProblemReporter.DISCARDING, maid.registryAccess(), record.maidNbt().copy()));
        BlockPos spawn = findSpawn(level, base);
        maid.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        maid.setInSittingPose(true);
        if (!level.addFreshEntity(maid)) {
            return false;
        }
        refreshReturnedMaidCenters(maid, level, spawn);
        if (reward) {
            EmotionData.addTrust(maid, record.ownerId(), record.trust());
            EmotionData.addFear(maid, record.ownerId(), record.fear());
            maid.getFavorabilityManager().add(record.favor());
            HungerData.add(maid, record.hunger());
            for (ItemStack value : getRewardsFromAdvancement(record.rewards(), maid)) {
                distribute(level, spawn, box, maid, value.copy());
            }
            triggerCommandFromAdvancement(record.rewards(), maid);
            CompoundTag persistent = maid.getPersistentData();
            persistent.putBoolean(RETURN_DIALOGUE, true);
            persistent.store(RETURN_OWNER, UUIDUtil.CODEC, record.ownerId());

            DispatchData.DispatchRecord.putComponent(persistent, RETURN_TITLE, record.eventTitle());
            DispatchData.DispatchRecord.putComponent(persistent, RETURN_DESC, record.eventDescription());
        }
        return true;
    }

    /**
     * 派遣数据会保留女仆原世界的日程中心和 Brain 寻路记忆。回收后按魂符释放的语义
     * 关闭旧居家限制，并把全部中心重建在当前落点，防止站起后返回甚至传送到旧坐标。
     */
    private static void refreshReturnedMaidCenters(EntityMaid maid, ServerLevel level, BlockPos spawn) {
        maid.getNavigation().stop();
        maid.setHomeModeEnable(false);
        SchedulePos schedule = maid.getSchedulePos();
        schedule.setWorkPos(spawn);
        schedule.setIdlePos(spawn);
        schedule.setSleepPos(spawn);
        schedule.setDimension(level.dimension().identifier());
        schedule.setConfigured(false);
        maid.setHomeTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    private static void distribute(ServerLevel level, BlockPos pos, RewardBoxBlockEntity box, EntityMaid maid, ItemStack stack) {
        if (box != null) {
            stack = insertBox(box, stack);
        }
        if (!stack.isEmpty()) {
            stack = ItemUtil.insertItemReturnRemaining(maid.getMaidInv(), stack, false, null);
        }
        if (!stack.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, stack));
        }
    }

    private static ItemStack insertBox(RewardBoxBlockEntity box, ItemStack stack) {
        for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = box.getItem(i);
            if (existing.isEmpty()) {
                box.setItem(i, stack.copy());
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    stack.shrink(moved);
                    box.setChanged();
                }
            }
        }
        return stack;
    }

    private static void scanReturnDialogues(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof EntityMaid maid) {
                    CompoundTag tag = maid.getPersistentData();
                    UUID ownerId = tag.read(RETURN_OWNER, UUIDUtil.CODEC).orElse(null);
                    if (!tag.getBooleanOr(RETURN_DIALOGUE, false) || ownerId == null) {
                        continue;
                    }
                    ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
                    if (player == null || player.level() != maid.level() || player.distanceToSqr(maid) > 64) {
                        continue;
                    }
                    long now = System.currentTimeMillis();
                    if (now - LAST_RETURN_DIALOGUE.getOrDefault(player.getUUID(), 0L) < 60_000L) {
                        continue;
                    }
                    String prompt = "你刚刚结束外出派遣回到主人身边。经历：" + tag.getStringOr(RETURN_TITLE, "") + "；" + tag.getStringOr(RETURN_DESC, "")
                            + "。结合你的性格和现在的情感，用自然口语说一到两句感想，总共不超过30字；不要提AI、系统、提示词或任何数值。"
                            + EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
                    MaidResponder.processBroadcast(player, List.of(maid), prompt, false);
                    LAST_RETURN_DIALOGUE.put(player.getUUID(), now);
                    tag.remove(RETURN_DIALOGUE);
                    tag.remove(RETURN_OWNER);
                    tag.remove(RETURN_TITLE);
                    tag.remove(RETURN_DESC);
                }
            }
        }
    }

    private static void synchronizeGlobal(DispatchData data, MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (DispatchData.DispatchRecord record : DispatchGlobalStore.owner(player.getUUID(), server.registryAccess())) {
                if (!data.hasDispatch(record.dispatchId())) {
                    data.add(record);
                }
            }
        }
        for (UUID owner : data.owners()) {
            for (DispatchData.DispatchRecord record : data.active(owner)) {
                if (DispatchGlobalStore.isClaimed(record.dispatchId())) {
                    data.remove(owner, record.dispatchId());
                } else if (!DispatchGlobalStore.contains(record.dispatchId(), server.registryAccess())) {
                    DispatchGlobalStore.put(record, server.registryAccess());
                }
            }
        }
    }

    private static void appendEvents(List<OpenDispatchScreenS2CPacket.EventInfo> output, List<String> ids,
                                     ServerPlayer player) {
        for (String id : ids) {
            DispatchEventDefinition event = DispatchEventLoader.get(id);
            if (event != null) {
                output.add(new OpenDispatchScreenS2CPacket.EventInfo(id, event.category().serializedName(),
                        event.title(), event.description(), event.duration().minInclusive(), event.duration().maxInclusive(),
                        getAllRewardsFromAdvancement(event.rewards(), player)));
            }
        }
    }

    /**
     * 943 写的就是 12 经验=一个经验瓶
     * <p>
     * 那么我们就让其消耗 12 点经验获得一个附魔之瓶吧
     */
    private static final int PER_BOTTLE_XP = 12;
    private static List<ItemStack> getRewardsFromAdvancement(AdvancementRewards rewards, Entity player){
        var level = player.level();
        if(!(level instanceof ServerLevel serverLevel))return List.of();
        var items = new ArrayList<ItemStack>();
        LootParams lootparams = (new LootParams.Builder(serverLevel)).withParameter(LootContextParams.THIS_ENTITY, player).withParameter(LootContextParams.ORIGIN, player.position()).create(LootContextParamSets.ADVANCEMENT_REWARD);
        for(ResourceKey<LootTable> resourcekey : rewards.loot()) {
            items.addAll(serverLevel.getServer().reloadableRegistries().getLootTable(resourcekey).getRandomItems(lootparams));
        }

        int count = rewards.experience() / PER_BOTTLE_XP;
        if (count <= 0) {
            count = 0;
        }
        if(count > 0)
            items.add(new ItemStack(Items.EXPERIENCE_BOTTLE, count));

        if(!rewards.recipes().isEmpty()){
            var stack = new ItemStack(Items.KNOWLEDGE_BOOK, 1);
            stack.set(DataComponents.RECIPES, rewards.recipes());
            items.add(stack);
        }

        return items;
    }

    /** 枚举该奖励能产生的所有可能物品（去重；不评估条件与函数，仅按条目类型展开）。 */
    private static List<ItemStack> getAllRewardsFromAdvancement(AdvancementRewards rewards, Entity player){
        var level = player.level();
        if(!(level instanceof ServerLevel serverLevel))return List.of();
        Set<Item> items = new HashSet<>();
        Set<ResourceKey<LootTable>> visited = new HashSet<>();
        MinecraftServer server = serverLevel.getServer();
        for (ResourceKey<LootTable> resourceKey : rewards.loot()) {
            collectAllItems(server, resourceKey, items, visited);
        }
        List<ItemStack> result = new ArrayList<>();
        items.forEach(item -> result.add(item.getDefaultInstance()));

        int count = rewards.experience() / PER_BOTTLE_XP;
        if (count > 0) {
            result.add(new ItemStack(Items.EXPERIENCE_BOTTLE, count));
        }
        if(!rewards.recipes().isEmpty()){
            var stack = new ItemStack(Items.KNOWLEDGE_BOOK, 1);
            stack.set(DataComponents.RECIPES, rewards.recipes());
            result.add(stack);
        }
        return result;
    }

    private static void collectAllItems(MinecraftServer server, ResourceKey<LootTable> key,
                                        Set<Item> items, Set<ResourceKey<LootTable>> visited) {
        if (!visited.add(key)) {
            return;
        }
        collectTable(server, server.reloadableRegistries().getLootTable(key), items, visited);
    }

    private static void collectTable(MinecraftServer server, LootTable table,
                                     Set<Item> items, Set<ResourceKey<LootTable>> visited) {
        for (LootPool pool : ((LootTableAccessor) table).callresponse$pools()) {
            for (LootPoolEntryContainer entry : ((LootPoolAccessor) pool).callresponse$entries()) {
                collectEntry(server, entry, items, visited);
            }
        }
    }

    private static void collectEntry(MinecraftServer server, LootPoolEntryContainer entry,
                                     Set<Item> items, Set<ResourceKey<LootTable>> visited) {
        if (entry instanceof LootItem lootItem) {
            items.add(((LootItemAccessor) lootItem).callresponse$item().value());
        } else if (entry instanceof TagEntry tagEntry) {
            server.registryAccess().lookupOrThrow(Registries.ITEM)
                    .get(((TagEntryAccessor) tagEntry).callresponse$tag())
                    .ifPresent(holders -> holders.forEach(holder -> items.add(holder.value())));
        } else if (entry instanceof NestedLootTable nested) {
            ((NestedLootTableAccessor) nested).callresponse$contents()
                    .ifLeft(childKey -> collectAllItems(server, childKey, items, visited))
                    .ifRight(childTable -> collectTable(server, childTable, items, visited));
        } else if (entry instanceof CompositeEntryBase composite) {
            for (LootPoolEntryContainer child : ((CompositeEntryBaseAccessor) composite).callresponse$children()) {
                collectEntry(server, child, items, visited);
            }
        }
    }

    private static void triggerCommandFromAdvancement(AdvancementRewards rewards, EntityMaid maid){
        var level = maid.level();
        if(!(level instanceof ServerLevel serverLevel))return;

        MinecraftServer minecraftserver = serverLevel.getServer();
        rewards.function().flatMap(function -> function.get(minecraftserver.getFunctions()))
                .ifPresent((stackCommandFunction) ->
                        minecraftserver.getFunctions().execute(stackCommandFunction,
                                maid.createCommandSourceStackForNameResolution(serverLevel)
                                        .withSuppressedOutput()
                                        .withPermission(PermissionSet.ALL_PERMISSIONS)));
    }

    private static DispatchData.EventPool ensurePool(DispatchData data, ServerPlayer player) {
        long now = System.currentTimeMillis();
        DispatchData.EventPool existing = data.pool(player.getUUID());
        int min = DispatchConfig.EVENT_COUNT_MIN.get();
        int max = Math.max(min, DispatchConfig.EVENT_COUNT_MAX.get());
        // 未到刷新时间，但池子数量与当前配置不符（例如玩家改了配置）时也立即重建
        if (existing != null && existing.refreshAt() > now
                && existing.work().size() >= min && existing.work().size() <= max
                && existing.play().size() >= min && existing.play().size() <= max) {
            return existing;
        }
        return refreshPool(data, player);
    }

    /** 立即重新抽取一批事件（忽略旧池的刷新时间）。 */
    private static DispatchData.EventPool refreshPool(DispatchData data, ServerPlayer player) {
        long now = System.currentTimeMillis();
        int min = DispatchConfig.EVENT_COUNT_MIN.get();
        int max = Math.max(min, DispatchConfig.EVENT_COUNT_MAX.get());
        RandomSource random = player.getRandom();
        int count = min + random.nextInt(max - min + 1);
        DispatchData.EventPool pool = new DispatchData.EventPool(player.getUUID(), now + DispatchConfig.EVENT_REFRESH_MINUTES.get() * 60_000L,
                choose(data, player.getUUID(), DispatchEventDefinition.Category.WORK, count, random, now),
                choose(data, player.getUUID(), DispatchEventDefinition.Category.PLAY, count, random, now));
        data.setPool(pool);
        return pool;
    }

    private static List<String> choose(DispatchData data, UUID owner, DispatchEventDefinition.Category category,
                                       int count, RandomSource random, long now) {
        List<Map.Entry<Identifier, DispatchEventDefinition>> available =
                DispatchEventLoader.allMap().entrySet().stream()
                        .filter(e -> {
                            DispatchEventDefinition def = e.getValue();
                            return def.category() == category && data.cooldown(owner, e.getKey().toString()) <= now;
                        })
                        .collect(Collectors.toList());

        List<String> result = new ArrayList<>();
        while (!available.isEmpty() && result.size() < count) {
            NavigableMap<Integer, Identifier> weightMap = new TreeMap<>();
            int cumulative = 0;
            for (Map.Entry<Identifier, DispatchEventDefinition> entry : available) {
                int weight = Math.max(1, entry.getValue().weight());
                cumulative += weight;
                weightMap.put(cumulative, entry.getKey());  // key 是累积值，value 是 ID
            }

            int total = cumulative;
            int roll = random.nextInt(total) + 1;
            Map.Entry<Integer, Identifier> chosenEntry = weightMap.ceilingEntry(roll);
            Identifier chosenId = chosenEntry.getValue();
            result.add(chosenId.toString());

            available.removeIf(entry -> entry.getKey().equals(chosenId));
        }
        return List.copyOf(result);
    }

    private static List<EntityMaid> ownedLoadedMaids(ServerPlayer player) {
        List<EntityMaid> result = new ArrayList<>();
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof EntityMaid maid && maid.isAlive() && maid.isTame()
                        && player.getUUID().equals((maid.getOwner() == null ? null : maid.getOwner().getUUID()))) {
                    result.add(maid);
                }
            }
        }
        result.sort(Comparator.comparingDouble(maid -> maid.level() == player.level()
                ? maid.distanceToSqr(player) : Double.MAX_VALUE));
        return result;
    }

    private static EntityMaid findMaid(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }

    private static DispatchData.BoxRef nearestValidBox(DispatchData data, MinecraftServer server, UUID owner,
                                                       ServerLevel origin, BlockPos pos) {
        return data.boxes(owner).stream()
                .filter(ref -> {
                    ServerLevel level = level(server, ref.dimension());
                    return level != null && level.getBlockEntity(ref.pos()) instanceof RewardBoxBlockEntity box
                            && owner.equals(box.getOwnerId());
                })
                .min(Comparator.comparingDouble(ref -> ref.dimension().equals(origin.dimension().identifier().toString())
                        ? ref.pos().distSqr(pos) : Double.MAX_VALUE))
                .orElse(null);
    }

    private static ServerLevel level(MinecraftServer server, String id) {
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(id)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BlockPos findSpawn(ServerLevel level, BlockPos base) {
        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = base.offset(dx, 0, dz);
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                        return pos;
                    }
                }
            }
        }
        return base.above();
    }
}
