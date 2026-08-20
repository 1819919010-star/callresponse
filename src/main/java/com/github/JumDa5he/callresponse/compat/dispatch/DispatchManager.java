package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.block.RewardBoxBlockEntity;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.config.DispatchConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class DispatchManager {
    private static final String RETURN_DIALOGUE = "callresponse$dispatch_return_dialogue";
    private static final String RETURN_OWNER = "callresponse$dispatch_return_owner";
    private static final String RETURN_TITLE = "callresponse$dispatch_return_title";
    private static final String RETURN_DESC = "callresponse$dispatch_return_desc";
    private static long lastCheck;
    private static final Map<UUID, Long> LAST_RETURN_DIALOGUE = new ConcurrentHashMap<>();

    private DispatchManager() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("callresponse")
                .then(Commands.literal("dispatch")
                        .then(Commands.literal("finish")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> finishOne(context.getSource())))));
    }

    /** 立即正常结算执行者名下最早派出的一只女仆，保留全部奖励与情感结算。 */
    private static int finishOne(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DispatchData data = DispatchData.get(player.server);
        List<DispatchData.DispatchRecord> candidates = new ArrayList<>(data.active(player.getUUID()));
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) {
            Set<UUID> known = new HashSet<>(); candidates.forEach(record -> known.add(record.dispatchId()));
            for (DispatchData.DispatchRecord record : DispatchGlobalStore.owner(player.getUUID())) {
                if (known.add(record.dispatchId())) candidates.add(record);
            }
        }
        DispatchData.DispatchRecord record = candidates.stream()
                .min(Comparator.comparingLong(DispatchData.DispatchRecord::startAt)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.literal("当前没有可以结算的派遣。"));
            return 0;
        }

        // 调试结算永远在执行指令的当前存档回收，优先当前存档最近的报酬箱。
        DispatchData.BoxRef targetBox = nearestValidBox(data, player.server, player.getUUID(),
                (ServerLevel) player.level(), player.blockPosition());
        DispatchData.DispatchRecord currentWorldRecord = new DispatchData.DispatchRecord(
                record.dispatchId(), record.ownerId(), record.maidId(), record.maidNbt(), record.eventId(),
                record.eventTitle(), record.eventDescription(), record.category(), record.startAt(),
                System.currentTimeMillis(), record.originDimension(), record.originPos(),
                targetBox == null ? "" : targetBox.dimension(), targetBox == null ? null : targetBox.pos(),
                record.trust(), record.fear(), record.favor(), record.hunger(), record.rewards(),
                record.modelId(), record.displayName());
        if (!restore(player.server, currentWorldRecord, player, true)) {
            source.sendFailure(Component.literal("派遣女仆重建失败，本次记录没有被删除。"));
            return 0;
        }
        data.remove(player.getUUID(), record.dispatchId());
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) DispatchGlobalStore.remove(record.dispatchId());
        source.sendSuccess(() -> Component.literal("已立即结算 " + record.displayName()
                + " 的派遣：" + record.eventTitle()), false);
        return 1;
    }

    public static void open(ServerPlayer player) {
        DispatchData data = DispatchData.get(player.server);
        DispatchData.EventPool pool = ensurePool(data, player);
        List<OpenDispatchScreenS2CPacket.EventInfo> events = new ArrayList<>();
        appendEvents(events, pool.work()); appendEvents(events, pool.play());
        List<OpenDispatchScreenS2CPacket.MaidInfo> maids = ownedLoadedMaids(player).stream()
                .filter(maid -> !data.isDispatched(maid.getUUID()))
                .map(maid -> new OpenDispatchScreenS2CPacket.MaidInfo(maid.getUUID(), maid.getDisplayName().getString(), maid.getModelId()))
                .toList();
        List<OpenDispatchScreenS2CPacket.ActiveInfo> active = data.active(player.getUUID()).stream()
                .map(record -> new OpenDispatchScreenS2CPacket.ActiveInfo(record.dispatchId(), record.displayName(), record.modelId(), record.eventTitle(), record.finishAt()))
                .toList();
        CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenDispatchScreenS2CPacket(events, maids, active, DispatchConfig.MAX_ACTIVE_DISPATCHES.get()));
    }

    public static void handle(ServerPlayer player, DispatchActionC2SPacket.Action action, String eventId, UUID targetId) {
        if (action == DispatchActionC2SPacket.Action.REQUEST) { open(player); return; }
        if (action == DispatchActionC2SPacket.Action.RECALL) {
            DispatchData data = DispatchData.get(player.server);
            data.active(player.getUUID()).stream().filter(record -> record.dispatchId().equals(targetId)).findFirst().ifPresent(record -> {
                if (restore(player.server, record, player, false)) {
                    data.remove(player.getUUID(), targetId);
                    if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) DispatchGlobalStore.remove(targetId);
                }
            });
            open(player); return;
        }
        start(player, eventId, targetId);
    }

    private static void start(ServerPlayer player, String eventId, UUID maidId) {
        DispatchData data = DispatchData.get(player.server);
        if (data.active(player.getUUID()).size() >= DispatchConfig.MAX_ACTIVE_DISPATCHES.get()) {
            player.sendSystemMessage(Component.translatable("message.callresponse.dispatch.full")); return;
        }
        DispatchData.EventPool pool = ensurePool(data, player);
        if (!pool.work().contains(eventId) && !pool.play().contains(eventId)) return;
        DispatchEventDefinition event = DispatchEventLoader.get(eventId);
        EntityMaid maid = findMaid(player.server, maidId);
        if (event == null || maid == null || !maid.isAlive() || !maid.isTame()
                || !player.getUUID().equals(maid.getOwnerUUID()) || data.isDispatched(maidId)) return;
        CompoundTag maidNbt = new CompoundTag(); maid.saveWithoutId(maidNbt);
        long now = System.currentTimeMillis();
        DispatchData.BoxRef box = nearestValidBox(data, player.server, player.getUUID(), (ServerLevel) maid.level(), maid.blockPosition());
        var emotion = event.emotion();
        DispatchData.DispatchRecord record = new DispatchData.DispatchRecord(UUID.randomUUID(), player.getUUID(), maid.getUUID(), maidNbt,
                event.id(), event.title(), event.description(), event.category().serializedName(), now, now + event.rollDurationMillis(maid.getRandom()),
                maid.level().dimension().location().toString(), maid.blockPosition(), box == null ? "" : box.dimension(), box == null ? null : box.pos(),
                emotion.trust(), emotion.fear(), emotion.favor(), emotion.hunger(), event.rollRewards(maid.getRandom()), maid.getModelId(), maid.getDisplayName().getString());
        data.add(record);
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) DispatchGlobalStore.put(record);
        data.setCooldown(player.getUUID(), event.id(), now + (long) event.cooldownMin() * 60_000L);
        maid.discard();
        player.sendSystemMessage(Component.translatable("message.callresponse.dispatch.started", record.displayName(), record.eventTitle()));
        open(player);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer(); long ticks = server.overworld().getGameTime();
        if (ticks - lastCheck < 20) return; lastCheck = ticks;
        DispatchData data = DispatchData.get(server); long now = System.currentTimeMillis();
        if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) synchronizeGlobal(data, server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (DispatchData.DispatchRecord record : data.active(player.getUUID())) {
                if (record.finishAt() <= now && restore(server, record, player, true)) { data.remove(player.getUUID(), record.dispatchId()); if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) DispatchGlobalStore.remove(record.dispatchId()); }
            }
        }
        // 多人服主人离线时也照常归还到原箱/原坐标。
        Set<UUID> online = new HashSet<>(); server.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
        data.owners().stream().filter(owner -> !online.contains(owner)).forEach(owner -> {
            for (DispatchData.DispatchRecord record : data.active(owner)) if (record.finishAt() <= now && restore(server, record, null, true)) { data.remove(owner, record.dispatchId()); if (DispatchConfig.CROSS_WORLD_RECOVERY.get()) DispatchGlobalStore.remove(record.dispatchId()); }
        });
        scanReturnDialogues(server);
    }

    private static boolean restore(MinecraftServer server, DispatchData.DispatchRecord record, ServerPlayer player, boolean reward) {
        ServerLevel level = null; BlockPos base = null; RewardBoxBlockEntity box = null;
        if (!record.boxDimension().isEmpty() && record.boxPos() != null) {
            level = level(server, record.boxDimension());
            if (level != null) level.getChunkAt(record.boxPos());
            if (level != null && level.getBlockEntity(record.boxPos()) instanceof RewardBoxBlockEntity candidate && record.ownerId().equals(candidate.getOwnerId())) { box = candidate; base = record.boxPos(); }
        }
        if (base == null && player != null) { level = (ServerLevel) player.level(); base = player.blockPosition().offset(2, 0, 0); }
        if (base == null) { level = level(server, record.originDimension()); base = record.originPos(); if (level != null) level.getChunkAt(base); }
        if (level == null) return false;
        EntityMaid maid = InitEntities.MAID.get().create(level); if (maid == null) return false;
        maid.load(record.maidNbt().copy());
        BlockPos spawn = findSpawn(level, base); maid.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5); maid.setInSittingPose(true);
        if (!level.addFreshEntity(maid)) return false;
        refreshReturnedMaidCenters(maid, level, spawn);
        if (reward) {
            EmotionData.addTrust(maid, record.ownerId(), record.trust()); EmotionData.addFear(maid, record.ownerId(), record.fear());
            maid.getFavorabilityManager().add(record.favor()); HungerData.add(maid, record.hunger());
            for (ItemStack value : record.rewards()) distribute(level, spawn, box, maid, value.copy());
            CompoundTag persistent = maid.getPersistentData(); persistent.putBoolean(RETURN_DIALOGUE, true); persistent.putUUID(RETURN_OWNER, record.ownerId());
            persistent.putString(RETURN_TITLE, record.eventTitle()); persistent.putString(RETURN_DESC, record.eventDescription());
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
        schedule.setDimension(level.dimension().location());
        schedule.setConfigured(false);
        maid.restrictTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    private static void distribute(ServerLevel level, BlockPos pos, RewardBoxBlockEntity box, EntityMaid maid, ItemStack stack) {
        if (box != null) stack = insertBox(box, stack);
        if (!stack.isEmpty()) stack = ItemHandlerHelper.insertItemStacked(maid.getMaidInv(), stack, false);
        if (!stack.isEmpty()) level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, stack));
    }

    private static ItemStack insertBox(RewardBoxBlockEntity box, ItemStack stack) {
        for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = box.getItem(i);
            if (existing.isEmpty()) { box.setItem(i, stack.copy()); return ItemStack.EMPTY; }
            if (ItemStack.isSameItemSameTags(existing, stack)) { int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount()); if (moved > 0) { existing.grow(moved); stack.shrink(moved); box.setChanged(); } }
        }
        return stack;
    }

    private static void scanReturnDialogues(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) for (Entity entity : level.getEntities().getAll()) if (entity instanceof EntityMaid maid) {
            CompoundTag tag = maid.getPersistentData(); if (!tag.getBoolean(RETURN_DIALOGUE) || !tag.hasUUID(RETURN_OWNER)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(tag.getUUID(RETURN_OWNER));
            if (player == null || player.level() != maid.level() || player.distanceToSqr(maid) > 64) continue;
            long now = System.currentTimeMillis(); if (now - LAST_RETURN_DIALOGUE.getOrDefault(player.getUUID(), 0L) < 60_000L) continue;
            String prompt = "你刚刚结束外出派遣回到主人身边。经历：" + tag.getString(RETURN_TITLE) + "；" + tag.getString(RETURN_DESC)
                    + "。结合你的性格和现在的情感，用自然口语说一到两句感想，总共不超过30字；不要提AI、系统、提示词或任何数值。"
                    + EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
            MaidResponder.processBroadcast(player, List.of(maid), prompt, false); LAST_RETURN_DIALOGUE.put(player.getUUID(), now); tag.remove(RETURN_DIALOGUE); tag.remove(RETURN_OWNER); tag.remove(RETURN_TITLE); tag.remove(RETURN_DESC);
        }
    }

    private static void synchronizeGlobal(DispatchData data, MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (DispatchData.DispatchRecord record : DispatchGlobalStore.owner(player.getUUID())) if (!data.hasDispatch(record.dispatchId())) data.add(record);
        }
        for (UUID owner : data.owners()) for (DispatchData.DispatchRecord record : data.active(owner)) {
            if (DispatchGlobalStore.isClaimed(record.dispatchId())) data.remove(owner, record.dispatchId());
            else if (!DispatchGlobalStore.contains(record.dispatchId())) DispatchGlobalStore.put(record);
        }
    }

    private static void appendEvents(List<OpenDispatchScreenS2CPacket.EventInfo> output, List<String> ids) {
        for (String id : ids) { DispatchEventDefinition event = DispatchEventLoader.get(id); if (event != null) output.add(new OpenDispatchScreenS2CPacket.EventInfo(event.id(), event.category().serializedName(), event.title(), event.description(), event.durationMin(), event.durationMax(), event.rewards().stream().map(DispatchEventDefinition.Reward::preview).filter(s -> !s.isEmpty()).toList())); }
    }

    private static DispatchData.EventPool ensurePool(DispatchData data, ServerPlayer player) {
        long now = System.currentTimeMillis(); DispatchData.EventPool existing = data.pool(player.getUUID());
        if (existing != null && existing.refreshAt() > now) return existing;
        int min = DispatchConfig.EVENT_COUNT_MIN.get(), max = Math.max(min, DispatchConfig.EVENT_COUNT_MAX.get());
        RandomSource random = player.getRandom(); int count = min + random.nextInt(max - min + 1);
        DispatchData.EventPool pool = new DispatchData.EventPool(player.getUUID(), now + DispatchConfig.EVENT_REFRESH_MINUTES.get() * 60_000L,
                choose(data, player.getUUID(), DispatchEventDefinition.Category.WORK, count, random, now), choose(data, player.getUUID(), DispatchEventDefinition.Category.PLAY, count, random, now));
        data.setPool(pool); return pool;
    }

    private static List<String> choose(DispatchData data, UUID owner, DispatchEventDefinition.Category category, int count, RandomSource random, long now) {
        List<DispatchEventDefinition> available = new ArrayList<>(DispatchEventLoader.all().stream().filter(e -> e.category() == category && data.cooldown(owner, e.id()) <= now).toList());
        List<String> result = new ArrayList<>();
        while (!available.isEmpty() && result.size() < count) { int total = available.stream().mapToInt(e -> Math.max(1, e.weight())).sum(), roll = random.nextInt(total); DispatchEventDefinition chosen = available.get(0); for (DispatchEventDefinition event : available) { roll -= Math.max(1, event.weight()); if (roll < 0) { chosen = event; break; } } result.add(chosen.id()); available.remove(chosen); }
        return List.copyOf(result);
    }

    private static List<EntityMaid> ownedLoadedMaids(ServerPlayer player) {
        List<EntityMaid> result = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) for (Entity entity : level.getEntities().getAll()) if (entity instanceof EntityMaid maid && maid.isAlive() && maid.isTame() && player.getUUID().equals(maid.getOwnerUUID())) result.add(maid);
        result.sort(Comparator.comparingDouble(maid -> maid.level() == player.level() ? maid.distanceToSqr(player) : Double.MAX_VALUE)); return result;
    }
    private static EntityMaid findMaid(MinecraftServer server, UUID id) { for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(id); if (entity instanceof EntityMaid maid) return maid; } return null; }
    private static DispatchData.BoxRef nearestValidBox(DispatchData data, MinecraftServer server, UUID owner, ServerLevel origin, BlockPos pos) {
        return data.boxes(owner).stream().filter(ref -> { ServerLevel level = level(server, ref.dimension()); return level != null && level.getBlockEntity(ref.pos()) instanceof RewardBoxBlockEntity box && owner.equals(box.getOwnerId()); })
                .min(Comparator.comparingDouble(ref -> ref.dimension().equals(origin.dimension().location().toString()) ? ref.pos().distSqr(pos) : Double.MAX_VALUE)).orElse(null);
    }
    private static ServerLevel level(MinecraftServer server, String id) { try { return server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(id))); } catch (Exception ignored) { return null; } }
    private static BlockPos findSpawn(ServerLevel level, BlockPos base) { for (int radius = 1; radius <= 2; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) { BlockPos pos = base.offset(dx, 0, dz); if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) return pos; } return base.above(); }
}
