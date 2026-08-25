package com.github.JumDa5he.callresponse.compat.talk;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.ChatTextSanitizer;
import com.github.JumDa5he.callresponse.compat.broadcast.DialogueApiLimiter;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerManager;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side state machine for the maid talk event. */
public final class TalkEventManager {
    private static final long SCAN_INTERVAL = 100;
    private static final long GATHER_TIMEOUT = 20L * 60;
    private static final long TALK_DURATION = 20L * 120;
    private static final long AI_TIMEOUT = 20L * 30;
    private static final double ARRIVE_DISTANCE_SQR = 1.5 * 1.5;
    private static final double OWNER_INVITE_RANGE_SQR = 8.0 * 8.0;
    private static final double OWNER_COMMAND_RANGE_SQR = 6.0 * 6.0;
    private static final long OWNER_INVITE_TIMEOUT = 20L * 30;
    private static final float WALK_SPEED = 0.70f;
    private static final int MAX_REPLIES_PER_LINE = 3;

    private static final String[] HAPPY_TOPICS = {
            "分享最近发生的一件开心小事，重点说清楚是谁做了什么",
            "复盘一次把大家逗笑的失误、误会或小闹剧",
            "友善调侃在场某位女仆最近的可爱表现，并允许本人反驳",
            "讲一个冷笑话、谐音梗或故意很烂的谜语让别人接",
            "夸张地复盘最近一件普通小事，把它讲得像传奇冒险",
            "模仿主人常说的一句话或习惯，让大家猜像不像",
            "聊一个最近让自己忍不住笑出来的瞬间",
            "假设明天完全不用工作，讨论大家最想一起玩的事情",
            "给在场成员起一个友善又好笑的绰号，并解释缘由",
            "分享一次歪打正着、最后结果出乎意料地不错的经历"
    };

    private static final String[] SAD_TOPICS = {
            "说一件最近让自己难过或委屈的具体事情，但不要无端指责主人",
            "聊最近有没有很久没吃到满意的饭，并说清楚最怀念什么",
            "说说最近一次没休息好、工作太累或独自硬撑的经历",
            "回忆一次被误解、被忽略或没能帮上忙时的失落",
            "聊一件过去没来得及好好告别的人或事",
            "说一个最近担心却一直没敢告诉其他人的小烦恼",
            "讨论做错事情后最希望别人怎样安慰自己",
            "聊一次努力很久却没有成功的经历，以及后来怎么面对",
            "说说什么时候会觉得孤单，以及在场谁最可能注意到",
            "分享最近一次想哭却忍住的原因，让别人可以认真回应"
    };

    private static final String[] OTHER_TOPICS = {
            "评价主人最近做过的一件让人佩服、无奈或想吐槽的事",
            "八卦在场某位女仆最近的反常表现，但不要恶意羞辱",
            "讨论附近最漂亮或最离谱的一处建筑，并给主人提改造意见",
            "聊各自工作里遇到过的意外、麻烦和值得炫耀的成果",
            "回忆一次险些闯祸、最后却歪打正着的经历",
            "争论谁最受主人宠爱，并各自拿出有趣的理由",
            "给在场成员起一个贴合性格的绰号，并解释缘由",
            "讲一件最近听来的村民、怪物或其他女仆的传闻",
            "讨论如果明天完全不用工作，大家最想偷偷做什么",
            "聊最近做过的怪梦，并猜它到底预示着什么",
            "吐槽主人仓库、背包或房间里最混乱的一处",
            "讨论谁最会偷懒、谁最爱逞强，允许本人反驳",
            "聊一种最喜欢或最讨厌的天气，以及曾因此发生的事",
            "假设主人突然变成史莱姆，认真讨论该怎么照顾他",
            "讲一个冷笑话、双关梗或故意很烂的谜语让别人接",
            "讨论最近附近有没有值得探险的地方或可疑动静",
            "分享一件平时不好意思承认的小习惯或小秘密",
            "讨论各自最欣赏在场哪位女仆的哪一个优点",
            "就衣服、发型、模型装扮或审美展开一场友善辩论",
            "聊如果自己当一天主人，会给大家安排什么离谱任务",
            "模仿主人常说的一句话或习惯动作，让大家猜真假",
            "讨论面对苦力怕、末影人或其他怪物时谁最可靠",
            "分享最近一次开心、委屈、吃醋或生气的真实原因",
            "争论家里最需要新增什么设施，理由可以大胆荒唐",
            "聊过去认识的人或旧日经历，但要讲一个具体细节",
            "互相出一道脑筋急转弯，并允许别人吐槽题目不讲理",
            "讨论如果能获得一种奇怪能力，大家会怎么使用它",
            "聊一件主人不知道、但女仆们彼此心照不宣的趣事",
            "夸张地复盘最近一件普通小事，把它讲得像传奇冒险",
            "讨论今天的饭或点心，但必须谈口味分歧、糗事或新奇搭配，不能只说想吃什么",
            "选一位在场女仆进行友善吐槽，随后也要说出她的优点",
            "讨论谁最适合负责侦察、做饭、战斗或管账，并允许争抢职位",
            "想象多年以后大家还住在这里，会变成什么样",
            "讨论主人要是听见这段聊天，最可能对哪句话心虚"
    };

    private static final String[] TALK_STYLES = {
            "带一点冷幽默，结尾最好有轻微反转",
            "情绪鲜明一些，可以开心、吃醋、得意、委屈或不服气",
            "像熟人互损一样友善吐槽，但不能恶意攻击",
            "讲一个具体小细节，不要只作笼统评价",
            "大胆提出不同意见，让别人有反驳空间",
            "用夸张比喻把普通事情讲得很有戏剧性",
            "先认真分析，最后突然抛出一句轻松的笑点",
            "带一点神秘传闻的口吻，让其他人想追问",
            "可以自嘲或揭自己的短，但不要重复别人刚说的话",
            "像关系很好的姐妹聊天，允许抢话题、争宠和开玩笑",
            "抛出一个有意思的选择题，让其他人站队",
            "说出明确态度和理由，不要只说‘我也是’或‘确实’",
            "自然夹带一两个中文网络用语或原创梗，例如‘绷不住了’‘这也太离谱了’‘属于是反向操作’，但不要硬塞",
            "用有点抽象的网络吐槽风格说话，让笑点来自具体事情而不是无意义发疯",
            "像在群聊里接梗一样回应，可以玩谐音、反差或回旋镖，但必须符合自己的人设"
    };

    private static final Map<UUID, TalkSession> BY_MAID = new ConcurrentHashMap<>();
    private static final Map<Long, TalkSession> BY_CHUNK = new ConcurrentHashMap<>();
    private static final Set<TalkSession> SESSIONS = ConcurrentHashMap.newKeySet();
    private static final List<EscapeRun> ESCAPES = new ArrayList<>();

    public static boolean isParticipant(EntityMaid maid) {
        return BY_MAID.containsKey(maid.getUUID());
    }

    private static boolean isTalkEnabled() {
        return BroadcastConfig.TALK_EVENT_ENABLED.get();
    }

    private static int minimumMaids() {
        return BroadcastConfig.TALK_EVENT_MIN_MAIDS.get();
    }

    private static long replyIntervalTicks() {
        return BroadcastConfig.TALK_REPLY_INTERVAL_SECONDS.get() * 20L;
    }

    /** Follow/await mixins use this to stop vanilla tasks from replacing our path. */
    public static boolean controlsMovement(EntityMaid maid) {
        return isParticipant(maid);
    }

    /** Called before HungerManager snapshots navigation, so it saves the true pre-talk state. */
    public static void leaveForFood(EntityMaid maid) {
        TalkSession session = BY_MAID.get(maid.getUUID());
        if (session == null) {
            return;
        }
        Member member = session.members.get(maid.getUUID());
        if (member != null) {
            releaseForFood(session, member);
            if (session.members.size() < 2) {
                endSession(session, EndReason.TOO_FEW);
            }
        }
    }

    /**
     * Keeps talk members out of the ordinary broadcast executor. In range, the
     * command becomes one orderly talk turn; out of range it is simply refused.
     */
    public static List<EntityMaid> routeBroadcastCommand(ServerPlayer player, List<EntityMaid> maids,
                                                         String command) {
        List<EntityMaid> result = new ArrayList<>(maids);
        Set<TalkSession> routed = new HashSet<>();
        result.removeIf(maid -> {
            TalkSession session = BY_MAID.get(maid.getUUID());
            if (session == null || !session.ownerId.equals(player.getUUID())) {
                return false;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(session.center)) <= OWNER_COMMAND_RANGE_SQR
                    && routed.add(session)) {
                queueOwnerLine(session, command);
            }
            return true;
        });
        return result;
    }

    /** During a talk, command text may be answered but cannot alter a member's action. */
    public static boolean suppressBroadcastAction(EntityMaid maid, ServerPlayer player) {
        TalkSession session = BY_MAID.get(maid.getUUID());
        return session != null && session.ownerId.equals(player.getUUID());
    }

    /** Ordinary chat is accepted only after the invited owner really joins. */
    public static void handleOwnerChat(ServerPlayer player, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (TalkSession session : SESSIONS) {
            if (session.state == State.TALKING && session.ownerJoined
                    && session.ownerId.equals(player.getUUID())
                    && player.level() == session.level
                    && player.distanceToSqr(Vec3.atCenterOf(session.center)) <= OWNER_COMMAND_RANGE_SQR) {
                queueOwnerLine(session, message);
            }
        }
    }

    /** Owner speech interrupts ordinary maid chatter and is handled on the next server tick. */
    private static void queueOwnerLine(TalkSession session, String line) {
        String cleaned = line == null ? "" : line.trim();
        if (cleaned.isEmpty()) {
            return;
        }
        while (session.ownerLines.size() >= 8) {
            session.ownerLines.removeLast();
        }
        session.ownerLines.addLast(cleaned);
        boolean invitationInProgress = session.inviteRequested && !session.invitedOwner;
        if (!invitationInProgress && session.waitingRequest != null) {
            UUID interrupted = session.waitingRequest;
            session.waitingRequest = null;
            TalkDialogueBridge.cancel(interrupted);
        }
        session.rootSpeaker = null;
        session.rootSpeakerId = null;
        session.rootText = null;
        session.pendingResponders.clear();
        session.receivedReply.clear();
        session.nextDialogueTick = session.level.getGameTime() + 1;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onParticipantAttacked(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            TalkSession session = BY_MAID.get(maid.getUUID());
            if (session != null) {
                endSession(session, EndReason.ATTACKED);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onParticipantDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            TalkSession session = BY_MAID.get(maid.getUUID());
            if (session != null) {
                endSession(session, EndReason.ATTACKED);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();

        tickEscapes(now);
        if (!isTalkEnabled()) {
            for (TalkSession session : List.copyOf(SESSIONS)) {
                endSession(session, EndReason.TOO_FEW);
            }
            return;
        }
        for (TalkSession session : List.copyOf(SESSIONS)) {
            tickSession(session, now);
        }
        if (now % SCAN_INTERVAL == 0) {
            scanForNewSessions(level, now);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("callresponse")
                .then(Commands.literal("talk")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> forceTalkInCurrentChunk(context.getSource().getPlayerOrException()))
                        .then(Commands.literal("finish")
                                .executes(context -> finishTalkInCurrentChunk(
                                        context.getSource().getPlayerOrException())))));
        // Clickable invitation replies must be available to ordinary players, so they use a
        // separate tightly validated command instead of inheriting the admin-only talk node.
        event.getDispatcher().register(Commands.literal("callresponse_talk_reply")
                .then(Commands.literal("join")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .executes(context -> respondToInvitation(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "session"), true))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .executes(context -> respondToInvitation(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "session"), false)))));
    }

    /** Admin test command: bypasses the daily timer, but keeps all participant safety filters. */
    private static int forceTalkInCurrentChunk(ServerPlayer player) {
        if (!isTalkEnabled()) {
            player.sendSystemMessage(Component.literal("[谈话] 谈话事件已在配置中关闭。"));
            return 0;
        }
        if (!(player.level() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            player.sendSystemMessage(Component.literal("[谈话] 只能在主世界触发。"));
            return 0;
        }
        long chunkKey = player.chunkPosition().toLong();
        if (BY_CHUNK.containsKey(chunkKey)) {
            player.sendSystemMessage(Component.literal("[谈话] 这个区块已经有一场谈话正在进行。"));
            return 0;
        }
        List<EntityMaid> maids = new ArrayList<>();
        for (EntityMaid maid : level.getEntities(EntityMaid.TYPE, candidate ->
                eligibleAtStart(candidate) && player.getUUID().equals(candidate.getOwnerUUID())
                        && candidate.chunkPosition().toLong() == chunkKey)) {
            maids.add(maid);
        }
        if (maids.size() < minimumMaids()) {
            player.sendSystemMessage(Component.literal("[谈话] 当前区块只有 " + maids.size()
                    + " 只符合条件的女仆，至少需要 " + minimumMaids() + " 只。"));
            return 0;
        }
        startSession(level, new GroupKey(chunkKey, player.getUUID()), maids, level.getGameTime());
        TalkEventSavedData.get(level).recordStart(player.getUUID(), level.getDayTime() / 24000L);
        player.sendSystemMessage(Component.literal("[谈话] 已让当前区块的女仆立即尝试聚集谈话。"));
        return 1;
    }

    /** Ends the owner's active talk in this chunk through the normal reward path. */
    private static int finishTalkInCurrentChunk(ServerPlayer player) {
        TalkSession session = BY_CHUNK.get(player.chunkPosition().toLong());
        if (session == null || !session.ownerId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[谈话] 当前区块没有属于你的谈话可以结算。"));
            return 0;
        }
        endSession(session, EndReason.NORMAL);
        return 1;
    }

    private static int respondToInvitation(ServerPlayer player, String sessionText, boolean accept) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionText);
        } catch (IllegalArgumentException ignored) {
            player.sendSystemMessage(Component.literal("[谈话] 这份邀请已经失效。"));
            return 0;
        }
        TalkSession session = SESSIONS.stream()
                .filter(candidate -> candidate.sessionId.equals(sessionId))
                .findFirst().orElse(null);
        long now = player.serverLevel().getGameTime();
        if (session == null || !session.ownerId.equals(player.getUUID())
                || session.level != player.level() || !session.invitedOwner
                || session.invitationClosed || session.ownerJoined
                || now > session.invitationExpires) {
            player.sendSystemMessage(Component.literal("[谈话] 这份邀请已经失效。"));
            return 0;
        }
        session.invitationClosed = true;
        if (!accept) {
            player.sendSystemMessage(Component.literal("[谈话] 你拒绝了女仆们的邀请。"));
            return 1;
        }
        session.ownerJoined = true;
        session.ownerArrivalPending = true;
        session.nextDialogueTick = Math.min(session.nextDialogueTick, now + 1);
        player.sendSystemMessage(Component.literal("[谈话] 你加入了女仆们的谈话。"));
        return 1;
    }

    private static void scanForNewSessions(ServerLevel level, long now) {
        Map<GroupKey, List<EntityMaid>> groups = new LinkedHashMap<>();
        for (EntityMaid maid : level.getEntities(EntityMaid.TYPE, TalkEventManager::eligibleAtStart)) {
            UUID owner = maid.getOwnerUUID();
            if (owner == null || level.getServer().getPlayerList().getPlayer(owner) == null) {
                continue;
            }
            groups.computeIfAbsent(new GroupKey(maid.chunkPosition().toLong(), owner), key -> new ArrayList<>())
                    .add(maid);
        }

        TalkEventSavedData data = TalkEventSavedData.get(level);
        long day = level.getDayTime() / 24000L;
        for (Map.Entry<GroupKey, List<EntityMaid>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<EntityMaid> maids = entry.getValue();
            if (maids.size() < minimumMaids() || BY_CHUNK.containsKey(key.chunk)
                    || !data.mayStart(key.owner, day)) {
                continue;
            }
            startSession(level, key, maids, now);
            data.recordStart(key.owner, day);
        }
    }

    private static boolean eligibleAtStart(EntityMaid maid) {
        if (!maid.isAlive() || !maid.isTame() || maid.getOwnerUUID() == null
                || BY_MAID.containsKey(maid.getUUID())
                || WanderingMaidData.isSpecial(maid) || TradingMaidData.isTrading(maid)
                || HungerManager.isBeggingForFood(maid) || HuntOrderManager.isHunting(maid)) {
            return false;
        }
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) || maid.getTarget() != null) {
            return false;
        }
        ResourceLocation taskId = maid.getTask().getUid();
        String id = taskId.toString().toLowerCase();
        if ("callresponse:lazy".equals(id) || maid.getTask() instanceof IAttackTask) {
            return false;
        }
        return !(id.contains("attack") || id.contains("combat") || id.contains("battle")
                || id.contains("fight") || id.contains("hunt") || id.contains("gun"));
    }

    private static void startSession(ServerLevel level, GroupKey key, List<EntityMaid> maids, long now) {
        ChunkPos chunk = new ChunkPos(key.chunk);
        int centerX = chunk.x * 16 + 8;
        int centerZ = chunk.z * 16 + 8;
        BlockPos center = surfacePos(level, centerX, centerZ);
        TalkSession session = new TalkSession(level, key.owner, chunk, center, now);

        double radius = Mth.clamp(2.5 + maids.size() * 0.18, 2.5, 5.0);
        for (int i = 0; i < maids.size(); i++) {
            EntityMaid maid = maids.get(i);
            double angle = Math.PI * 2.0 * i / maids.size();
            int x = Mth.floor(center.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(center.getZ() + Math.sin(angle) * radius);
            BlockPos seat = surfacePos(level, x, z);
            Member member = new Member(maid, seat);
            session.members.put(maid.getUUID(), member);
            BY_MAID.put(maid.getUUID(), session);
            beginControl(member, level);
        }
        SESSIONS.add(session);
        BY_CHUNK.put(key.chunk, session);
    }

    private static BlockPos surfacePos(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos original = new BlockPos(x, y, z);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos candidate = original.offset(0, dy, 0);
            if (isSafeStandPos(level, candidate)) {
                return candidate;
            }
        }
        return original;
    }

    private static boolean isSafeStandPos(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static void tickSession(TalkSession session, long now) {
        if (!SESSIONS.contains(session)) {
            return;
        }
        for (Member member : List.copyOf(session.members.values())) {
            EntityMaid maid = member.maid;
            if (!maid.isAlive() || maid.isRemoved()) {
                endSession(session, EndReason.MISSING);
                return;
            }
            if (HungerManager.isBeggingForFood(maid)) {
                releaseForFood(session, member);
                continue;
            }
            if (maid.chunkPosition().toLong() != session.chunk.toLong()) {
                endSession(session, EndReason.LEFT_CHUNK);
                return;
            }
            if (HuntOrderManager.isHunting(maid)
                    || maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    || maid.getTarget() != null) {
                endSession(session, EndReason.ATTACKED);
                return;
            }
        }
        if (session.members.size() < 2) {
            endSession(session, EndReason.TOO_FEW);
            return;
        }

        if (session.waitingRequest != null && now - session.waitingSince > AI_TIMEOUT) {
            UUID timedOut = session.waitingRequest;
            session.waitingRequest = null;
            TalkDialogueBridge.cancel(timedOut);
        }

        if (session.state == State.GATHERING) {
            boolean allArrived = true;
            for (Member member : session.members.values()) {
                EntityMaid maid = member.maid;
                if (maid.distanceToSqr(Vec3.atBottomCenterOf(member.seat)) <= ARRIVE_DISTANCE_SQR) {
                    holdSeat(maid, member.seat, session.center);
                } else {
                    allArrived = false;
                    forceWalk(maid, member.seat, 1);
                }
            }
            if (allArrived) {
                session.state = State.TALKING;
                session.talkStarted = now;
                session.nextDialogueTick = now + 20 + session.level.getRandom().nextInt(21);
                session.lastInviteRoll = now;
                ServerPlayer owner = session.level.getServer().getPlayerList().getPlayer(session.ownerId);
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("[谈话] 一群女仆在区块中心围坐了下来，她们好像正聊着什么……"));
                }
            } else if (now - session.createdAt >= GATHER_TIMEOUT) {
                endSession(session, EndReason.GATHER_TIMEOUT);
            }
            return;
        }

        for (Member member : session.members.values()) {
            holdSeat(member.maid, member.seat, session.center);
        }
        if (now - session.talkStarted >= TALK_DURATION) {
            endSession(session, EndReason.NORMAL);
            return;
        }

        tickOwnerParticipation(session, now);
        tickDialogue(session, now);
    }

    private static void tickOwnerParticipation(TalkSession session, long now) {
        ServerPlayer owner = session.level.getServer().getPlayerList().getPlayer(session.ownerId);
        if (owner == null || owner.level() != session.level) {
            return;
        }
        double distance = owner.distanceToSqr(Vec3.atCenterOf(session.center));
        if (session.invitedOwner && !session.ownerJoined && !session.invitationClosed
                && now > session.invitationExpires) {
            session.invitationClosed = true;
            owner.sendSystemMessage(Component.literal("[谈话] 你没有回应邀请，这次就不加入了。"));
        }
        if (!session.inviteRequested && !session.invitedOwner && distance <= OWNER_INVITE_RANGE_SQR
                && session.waitingRequest == null) {
            session.lastInviteRoll = now;
            List<Member> members = new ArrayList<>(session.members.values());
            Member speaker = members.get(session.level.getRandom().nextInt(members.size()));
            session.inviteRequested = true;
            requestInvitation(session, speaker, owner, now);
        }
    }

    private static void tickDialogue(TalkSession session, long now) {
        if (session.waitingRequest != null || now < session.nextDialogueTick) {
            return;
        }
        ServerPlayer owner = session.level.getServer().getPlayerList().getPlayer(session.ownerId);
        if (owner == null) {
            session.nextDialogueTick = now + 100;
            return;
        }
        if (session.ownerArrivalPending) {
            session.ownerArrivalPending = false;
            requestOwnerArrivalReaction(session, owner, now);
            return;
        }
        if (!session.pendingResponders.isEmpty() && session.rootText != null) {
            UUID id = session.pendingResponders.removeFirst();
            Member responder = session.members.get(id);
            if (responder != null) {
                requestResponse(session, responder, owner, now);
                return;
            }
        }
        if (session.rootText != null) {
            finishRound(session, now);
            return;
        }
        if (!session.ownerLines.isEmpty()) {
            beginResponseRound(session, "主人", null, session.ownerLines.removeFirst(), now);
            return;
        }
        requestNewTopic(session, owner, now);
    }

    private static void requestNewTopic(TalkSession session, ServerPlayer owner, long now) {
        List<Member> members = new ArrayList<>(session.members.values());
        Member speaker = members.get(session.level.getRandom().nextInt(members.size()));
        String privateContext = session.privateInbox.remove(speaker.maid.getUUID());
        String topic = chooseTopic(session);
        String style = TALK_STYLES[session.level.getRandom().nextInt(TALK_STYLES.length)];
        String prompt = commonPrompt(session, speaker.maid, owner)
                + "\n你现在是本轮主动起头的人。围绕这个话题自然地说一句：" + topic + "。"
                + "\n本轮表达方式：" + style + "。"
                + (privateContext == null ? "" : "\n你之前单独听到了一句回应，可以自然接下去：" + privateContext)
                + "\n必须提出新信息、明确观点或具体细节，给别人留下回应空间。"
                + "除非本轮明确抽到饮食话题，否则不要主动转去聊吃饭、点心或谁带了食物。"
                + "不要沿用最近几轮的关键词、句式和结论。"
                + "\n只输出一段自然口语，18到55个字；不要写姓名前缀、动作、旁白、引号或解释。";
        requestAi(session, speaker, owner, prompt, now, text ->
                beginResponseRound(session, describe(speaker.maid), speaker.maid.getUUID(), text,
                        session.level.getGameTime()));
    }

    private static String chooseTopic(TalkSession session) {
        String[] pool = switch (session.topicMoodCursor++ % 3) {
            case 0 -> HAPPY_TOPICS;
            case 1 -> SAD_TOPICS;
            default -> OTHER_TOPICS;
        };
        String chosen = pool[session.level.getRandom().nextInt(pool.length)];
        for (int attempt = 0; attempt < 16 && session.recentTopics.contains(chosen); attempt++) {
            chosen = pool[session.level.getRandom().nextInt(pool.length)];
        }
        session.recentTopics.addLast(chosen);
        while (session.recentTopics.size() > 10) {
            session.recentTopics.removeFirst();
        }
        return chosen;
    }

    private static void requestResponse(TalkSession session, Member responder, ServerPlayer owner, long now) {
        String privateContext = session.privateInbox.remove(responder.maid.getUUID());
        String style = TALK_STYLES[session.level.getRandom().nextInt(TALK_STYLES.length)];
        String prompt = commonPrompt(session, responder.maid, owner)
                + (session.rootSpeakerId == null
                    ? "\n刚才说话者是主人。"
                    : "\n刚才说话女仆的完整身份资料是：" + session.rootSpeaker
                        + "。请严格按她的名字、模型名称、模型ID和工作识别她，不要认成其他成员。")
                + "\n对方刚才真实说了：『" + session.rootText + "』"
                + (privateContext == null ? "" : "\n你还单独听到过：『" + privateContext + "』")
                + "\n请像真实聚会聊天一样直接回应她，但不要复述她的句子，也不要只说赞同。"
                + "你要从不同角度补充具体经历、提出异议、追问、吐槽、抛梗或制造一个小反转。"
                + "\n本次回应方式：" + style + "。"
                + "可以自然使用一两个常见中文网络用语、谐音梗或轻微抽象梗，但要贴合上下文，不能硬堆。"
                + "即使对方提到酒，也不要继续围绕喝酒、劝酒、灌酒、醉酒或酒馆展开；接住情绪后换成别的具体内容。"
                + (session.rootSpeakerId == null
                    ? ownerConflictPrompt(responder.maid, session.ownerId)
                    : "")
                + (session.rootSpeakerId == null
                    ? "\n如果主人的话要求打架、攻击、起身、集合或离开谈话圈，必须自然地拒绝并继续坐在原地；开饭等生活内容可以口头回应，但不要移动。"
                    : "")
                + "\n只输出一段自然口语，15到50个字；禁止‘我也是’‘确实如此’这类没有新内容的机械附和；"
                + "不要写姓名前缀、动作、旁白、引号或解释。";
        requestAi(session, responder, owner, prompt, now, text -> {
            distributeReplyContext(session, responder.maid, text);
            session.nextDialogueTick = session.level.getGameTime() + replyIntervalTicks();
        });
    }

    private static void requestInvitation(TalkSession session, Member speaker, ServerPlayer owner, long now) {
        String prompt = commonPrompt(session, speaker.maid, owner)
                + "\n你注意到主人正在谈话圈附近。坐在原地，用一句自然、符合性格的话邀请主人过来一起聊天。"
                + "\n只输出一句15到30字的口语；不要移动，不要写动作、旁白、姓名前缀或解释。";
        requestAi(session, speaker, owner, prompt, now, text -> {
            session.inviteRequested = false;
            session.invitedOwner = true;
            session.invitationExpires = session.level.getGameTime() + OWNER_INVITE_TIMEOUT;
            sendInvitationChoices(session, owner);
            session.nextDialogueTick = session.level.getGameTime() + 20;
        }, () -> session.inviteRequested = false);
    }

    private static String ownerConflictPrompt(EntityMaid maid, UUID ownerId) {
        EmotionData.EmotionValues values = EmotionData.get(maid, ownerId);
        String contextRule = "\n先判断主人是在骂你、骂其他人，还是仅仅在抱怨某件事；不要看见脏话就误认成自己被骂。";
        if (values.fear() >= 65) {
            return contextRule + "如果主人确实是在辱骂你，你虽然害怕，也要按自己性格表达受伤、警惕或防御，"
                    + "可以小心地顶嘴，但不要突然表现得像完全不怕主人。";
        }
        if (values.trust() >= 65 && values.fear() <= 30) {
            return contextRule + "如果主人确实是在辱骂你，可以像关系很近的人一样犀利回怼、互损，必要时用少量同等强度的脏话对骂，"
                    + "但保留亲近感，不要升级成真实仇恨、死亡威胁或无关的人身攻击。";
        }
        if (values.trust() <= 30) {
            return contextRule + "如果主人确实是在辱骂你，不必讨好或忍让；可以冷脸划清界限、讽刺反击或直接对骂，"
                    + "语气要符合目前较低的信任。";
        }
        return contextRule + "如果主人确实是在辱骂你，可以按照当前性格和信任、恐惧值进行回怼、吐槽或有限度对骂，不能机械道歉。"
                + "如果主人只是骂别的事而不是骂你，则不要误以为自己被攻击，应针对他真正抱怨的对象回应。";
    }

    private static void sendInvitationChoices(TalkSession session, ServerPlayer owner) {
        String id = session.sessionId.toString();
        Component message = Component.literal("[谈话] 女仆们邀请你一起来聊聊。 ")
                .append(Component.literal("[加入]").withStyle(style -> style
                        .withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/callresponse_talk_reply join " + id))))
                .append(Component.literal("  "))
                .append(Component.literal("[拒绝]").withStyle(style -> style
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/callresponse_talk_reply decline " + id))));
        owner.sendSystemMessage(message);
    }

    private static void requestOwnerArrivalReaction(TalkSession session, ServerPlayer owner, long now) {
        List<Member> members = new ArrayList<>(session.members.values());
        Member speaker = members.get(session.level.getRandom().nextInt(members.size()));
        String prompt = commonPrompt(session, speaker.maid, owner)
                + "\n主人刚刚点击接受邀请，正式加入了谈话。自然说一句‘主人来了’一类的欢迎、打趣或接梗，"
                + "让人知道你注意到了主人，但不要像系统播报。可以使用一个自然的网络用语或梗。"
                + "\n只输出一句15到40字的口语；不要写动作、旁白、姓名前缀或解释。";
        requestAi(session, speaker, owner, prompt, now,
                text -> session.nextDialogueTick = session.level.getGameTime() + 12);
    }

    private static void requestAi(TalkSession session, Member speaker, ServerPlayer owner,
                                  String prompt, long now, java.util.function.Consumer<String> success) {
        requestAi(session, speaker, owner, prompt, now, success, () -> { });
    }

    private static void requestAi(TalkSession session, Member speaker, ServerPlayer owner,
                                  String prompt, long now, java.util.function.Consumer<String> success,
                                  Runnable extraFailure) {
        if (!DialogueApiLimiter.tryAcquire()) {
            session.nextDialogueTick = now + 40;
            extraFailure.run();
            return;
        }
        final UUID[] holder = new UUID[1];
        UUID request = TalkDialogueBridge.request(speaker.maid, owner, prompt, text -> {
            if (!SESSIONS.contains(session) || !requestMatches(session, holder[0])) {
                return;
            }
            session.waitingRequest = null;
            success.accept(sanitizeTalkText(text, session.rootSpeakerId == null));
        }, () -> {
            if (SESSIONS.contains(session) && requestMatches(session, holder[0])) {
                session.waitingRequest = null;
                session.nextDialogueTick = session.level.getGameTime() + 30;
                extraFailure.run();
            }
        });
        holder[0] = request;
        session.waitingRequest = request;
        session.waitingSince = now;
    }

    private static boolean requestMatches(TalkSession session, UUID id) {
        return id != null && id.equals(session.waitingRequest);
    }

    private static void beginResponseRound(TalkSession session, String speaker, UUID speakerId,
                                           String text, long now) {
        session.rootSpeaker = speaker;
        session.rootSpeakerId = speakerId;
        session.rootText = text;
        session.recentRoots.addLast(speaker + "说：" + text);
        while (session.recentRoots.size() > 3) {
            session.recentRoots.removeFirst();
        }
        session.receivedReply.clear();
        session.pendingResponders.clear();
        List<Member> candidates = new ArrayList<>(session.members.values());
        Collections.shuffle(candidates);
        for (Member candidate : candidates) {
            if (candidate.maid.getUUID().equals(speakerId)) {
                continue;
            }
            if (session.level.getRandom().nextFloat() < 0.60f) {
                session.pendingResponders.add(candidate.maid.getUUID());
                if (session.pendingResponders.size() >= MAX_REPLIES_PER_LINE) {
                    break;
                }
            }
        }
        session.nextDialogueTick = now + replyIntervalTicks();
    }

    private static void distributeReplyContext(TalkSession session, EntityMaid responder, String reply) {
        List<Member> listeners = session.members.values().stream()
                .filter(member -> !member.maid.getUUID().equals(responder.getUUID()))
                .filter(member -> !session.receivedReply.contains(member.maid.getUUID()))
                .filter(member -> !session.privateInbox.containsKey(member.maid.getUUID()))
                .toList();
        if (!listeners.isEmpty()) {
            Member listener = listeners.get(session.level.getRandom().nextInt(listeners.size()));
            session.receivedReply.add(listener.maid.getUUID());
            session.privateInbox.put(listener.maid.getUUID(), describe(responder) + "回应说：" + reply);
        }
    }

    private static void finishRound(TalkSession session, long now) {
        session.rootSpeaker = null;
        session.rootSpeakerId = null;
        session.rootText = null;
        session.pendingResponders.clear();
        session.receivedReply.clear();
        session.nextDialogueTick = now + 40 + session.level.getRandom().nextInt(41);
    }

    private static String commonPrompt(TalkSession session, EntityMaid speaker, ServerPlayer owner) {
        return "【谈话事件】你正和同一位主人家的女仆围坐聊天。这是私人、轻松、真实的多人谈话。"
                + "\n在场成员：" + roster(session)
                + "\n你自己是：" + describe(speaker)
                + "\n主人身份：名字=" + owner.getName().getString()
                + "，实体类型=minecraft:player。主人的真实外貌和身体结构没有提供；"
                + "除非主人刚刚亲口说明，否则严禁声称主人有尾巴、兽耳、翅膀、角或其他非玩家身体部位，"
                + "也不能说主人被这些不存在的部位压住、缠住或碰到。即使你的人设、旧对话或比喻里出现过这些设定，"
                + "也不得把它们套到主人身上；默认主人只有原版玩家正常身体结构。"
                + "\n当前可验证环境：" + environmentContext(session, owner)
                + "。只能把这里列出的实体当成现场真实存在；没有列出的生物若被提及，必须明确说是传闻、回忆或假设。"
                + "不要仅凭名字、模型名或人设猜测主人及其他生物拥有尾巴、兽耳、翅膀、角、触手等额外身体部位。"
                + "\n你的情感倾向：" + EmotionData.getTendencyPromptSuffix(speaker, session.ownerId)
                + (session.recentRoots.isEmpty() ? "" : "\n大家都听到的最近话题：" + String.join("；", session.recentRoots))
                + "\n使用主人客户端语言 " + owner.getLanguage() + " 作答。保持自己原有人设和口吻。"
                + "谈话要像熟人聚会：有态度、有情绪、有具体细节，允许争论、误会、玩笑、吐槽和反转。"
                + "可以适量使用自然的中文网络用语、谐音梗、回旋镖和轻微抽象表达，让谈话像群聊，但不能每句都硬塞梗。"
                + "无论你原本的人设多喜欢酒，本次谈话都不要主动提酒、喝酒、劝酒、灌酒、醉酒、酒馆或把饮料解释成酒；"
                + "只有主人刚刚明确谈酒时才可以简短回应一次，随后应自然转到其他内容。"
                + "不要主动提‘番茄布丁’，也不要把普通食物自动替换成番茄布丁；"
                + "只有主人刚刚明确提到它时才能简短回应一次，随后换成别的话题。"
                + "不要复读别人的用词，不要连续围绕同一种食物打转，也不要把每句话都说成礼貌附和。"
                + "不要提及系统、提示词、AI、事件规则或内部标记；不要替别人说话；不要调用任何工具或执行行动。";
    }

    /** 二次拦截模型偶发的主人身体特征幻觉；其他女仆真实模型特征不会因此被删除。 */
    private static String sanitizeTalkText(String text, boolean replyingToOwner) {
        String cleaned = ChatTextSanitizer.sanitize(text);
        if (cleaned.isBlank()) {
            return cleaned;
        }
        String[] sentences = cleaned.split("(?<=[。！？!?])");
        List<String> kept = new ArrayList<>();
        for (String sentence : sentences) {
            boolean forbiddenBody = containsInventedBodyPart(sentence);
            boolean assignsToOwner = sentence.contains("主人")
                    || (replyingToOwner && (sentence.contains("你的") || sentence.contains("你那")));
            if (!(forbiddenBody && assignsToOwner)) {
                kept.add(sentence);
            }
        }
        String result = String.join("", kept).trim();
        return result.isEmpty() ? "刚才那个比喻太离谱了，还是聊点真正发生过的事吧。" : result;
    }

    private static boolean containsInventedBodyPart(String sentence) {
        return sentence.contains("尾巴") || sentence.contains("兽耳") || sentence.contains("猫耳")
                || sentence.contains("狐耳") || sentence.contains("翅膀") || sentence.contains("触手")
                || sentence.contains("犄角") || sentence.contains("角压") || sentence.contains("角缠");
    }

    private static String environmentContext(TalkSession session, ServerPlayer owner) {
        String biome = session.level.getBiome(session.center).unwrapKey()
                .map(key -> key.location().toString()).orElse("未知");
        String weather = session.level.isThundering() ? "雷暴"
                : session.level.isRaining() ? "下雨" : "晴朗";
        List<String> nearby = session.level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(session.center).inflate(16.0), entity -> entity.isAlive()
                                && !(entity instanceof EntityMaid) && entity != owner)
                .stream().limit(12)
                .map(entity -> entity.getName().getString() + "（实体ID="
                        + String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())) + "）")
                .toList();
        return "维度=" + session.level.dimension().location()
                + "，生物群系=" + biome + "，天气=" + weather
                + "，16格内除主人和女仆外的实际生物="
                + (nearby.isEmpty() ? "无" : String.join("、", nearby));
    }

    private static String roster(TalkSession session) {
        return session.members.values().stream().map(member -> describe(member.maid))
                .reduce((a, b) -> a + "；" + b).orElse("无");
    }

    private static String describe(EntityMaid maid) {
        String displayName = maid.getName().getString();
        String modelName = ServerCustomPackLoader.SERVER_MAID_MODELS.getInfo(maid.getModelId())
                .map(info -> ParseI18n.parse(info.getName()).getString())
                .orElse(maid.getModelId());
        String taskName = maid.getTask().getName().getString();
        return displayName + "（模型=" + modelName + "/" + maid.getModelId()
                + "，工作=" + taskName + "/" + maid.getTask().getUid() + "）";
    }

    private static void beginControl(Member member, ServerLevel level) {
        EntityMaid maid = member.maid;
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.TALK,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH,
                        MaidMovementControl.Field.SCHEDULE, MaidMovementControl.Field.POSE));
        maid.setInSittingPose(false);
        overwriteSchedule(maid, member.seat, level);
        forceWalk(maid, member.seat, 1);
    }

    private static void overwriteSchedule(EntityMaid maid, BlockPos pos, ServerLevel level) {
        // restriction 不落盘；SchedulePos.tick 在 TALK reason 期间由 Mixin 暂停。
        maid.restrictTo(pos, 3);
    }

    private static void forceWalk(EntityMaid maid, BlockPos target, int closeEnough) {
        maid.setInSittingPose(false);
        overwriteSchedule(maid, target, (ServerLevel) maid.level());
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), WALK_SPEED, closeEnough));
        maid.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
    }

    private static void holdSeat(EntityMaid maid, BlockPos seat, BlockPos center) {
        overwriteSchedule(maid, seat, (ServerLevel) maid.level());
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(center));
        maid.setInSittingPose(true);
        maid.getLookControl().setLookAt(center.getX() + 0.5, center.getY() + 0.7, center.getZ() + 0.5);
    }

    private static void releaseForFood(TalkSession session, Member member) {
        session.members.remove(member.maid.getUUID());
        BY_MAID.remove(member.maid.getUUID(), session);
        restore(member);
    }

    private static void endSession(TalkSession session, EndReason reason) {
        if (!SESSIONS.remove(session)) {
            return;
        }
        BY_CHUNK.remove(session.chunk.toLong(), session);
        if (session.waitingRequest != null) {
            UUID request = session.waitingRequest;
            session.waitingRequest = null;
            TalkDialogueBridge.cancel(request);
        }
        boolean normal = reason == EndReason.NORMAL;
        boolean panic = reason == EndReason.ATTACKED;
        long now = session.level.getGameTime();
        int rewarded = 0;
        for (Member member : session.members.values()) {
            BY_MAID.remove(member.maid.getUUID(), session);
            if (normal && member.maid.isAlive()) {
                EmotionData.EmotionValues before = EmotionData.get(member.maid, session.ownerId);
                EmotionData.addTrust(member.maid, session.ownerId, 4);
                EmotionData.addFear(member.maid, session.ownerId, -8);
                EmotionData.EmotionValues after = EmotionData.get(member.maid, session.ownerId);
                rewarded++;
                CallResponseMod.LOGGER.info(
                        "[谈话调试] 女仆={} UUID={} 正常结算：信任 {} -> {}（+4），恐惧 {} -> {}（-8）",
                        member.maid.getName().getString(), member.maid.getUUID(),
                        before.trust(), after.trust(), before.fear(), after.fear());
            }
            if ((normal || panic) && member.maid.isAlive()) {
                beginEscape(member, now);
            } else {
                restore(member);
            }
        }
        if (normal) {
            ServerPlayer owner = session.level.getServer().getPlayerList().getPlayer(session.ownerId);
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("[谈话] 谈话结束了，女仆们各自散开。"));
                owner.sendSystemMessage(Component.literal("[谈话调试] 正常结算 " + rewarded
                        + " 只女仆：每只信任 +4、恐惧 -8。"));
            }
            CallResponseMod.LOGGER.info(
                    "[谈话调试] 谈话正常结束：主人={}，区块=({}, {})，结算人数={}，信任+4，恐惧-8",
                    session.ownerId, session.chunk.x, session.chunk.z, rewarded);
        }
        session.members.clear();
    }

    private static void beginEscape(Member member, long now) {
        EntityMaid maid = member.maid;
        maid.setInSittingPose(false);
        double angle = maid.getRandom().nextDouble() * Math.PI * 2.0;
        BlockPos target = BlockPos.containing(maid.getX() + Math.cos(angle) * 10.0,
                maid.getY(), maid.getZ() + Math.sin(angle) * 10.0);
        target = surfacePos((ServerLevel) maid.level(), target.getX(), target.getZ());
        ESCAPES.add(new EscapeRun(member, target, now + 100));
    }

    private static void tickEscapes(long now) {
        Iterator<EscapeRun> iterator = ESCAPES.iterator();
        while (iterator.hasNext()) {
            EscapeRun run = iterator.next();
            EntityMaid maid = run.member.maid;
            if (!maid.isAlive() || maid.isRemoved() || now >= run.until
                    || maid.distanceToSqr(Vec3.atBottomCenterOf(run.target)) <= 2.25) {
                restore(run.member);
                iterator.remove();
            } else {
                forceWalk(maid, run.target, 1);
            }
        }
    }

    private static void restore(Member member) {
        EntityMaid maid = member.maid;
        if (!maid.isRemoved()) {
            MaidMovementControl.clearNavigation(maid);
        }
        MaidMovementControl.end(maid, MaidMovementControl.Reason.TALK);
    }

    private enum State { GATHERING, TALKING }
    private enum EndReason { NORMAL, GATHER_TIMEOUT, LEFT_CHUNK, MISSING, ATTACKED, TOO_FEW }

    private record GroupKey(long chunk, UUID owner) {
    }

    private static final class Member {
        final EntityMaid maid;
        final BlockPos seat;

        Member(EntityMaid maid, BlockPos seat) {
            this.maid = maid;
            this.seat = seat;
        }
    }

    private static final class TalkSession {
        final UUID sessionId = UUID.randomUUID();
        final ServerLevel level;
        final UUID ownerId;
        final ChunkPos chunk;
        final BlockPos center;
        final long createdAt;
        final Map<UUID, Member> members = new LinkedHashMap<>();
        final Deque<String> ownerLines = new ArrayDeque<>();
        final Deque<String> recentRoots = new ArrayDeque<>();
        final Deque<String> recentTopics = new ArrayDeque<>();
        final Deque<UUID> pendingResponders = new ArrayDeque<>();
        final Set<UUID> receivedReply = new HashSet<>();
        final Map<UUID, String> privateInbox = new HashMap<>();
        State state = State.GATHERING;
        long talkStarted;
        long nextDialogueTick;
        long lastInviteRoll;
        UUID waitingRequest;
        long waitingSince;
        boolean invitedOwner;
        boolean inviteRequested;
        boolean invitationClosed;
        boolean ownerJoined;
        boolean ownerArrivalPending;
        long invitationExpires;
        String rootSpeaker;
        UUID rootSpeakerId;
        String rootText;
        int topicMoodCursor;

        TalkSession(ServerLevel level, UUID ownerId, ChunkPos chunk, BlockPos center, long createdAt) {
            this.level = level;
            this.ownerId = ownerId;
            this.chunk = chunk;
            this.center = center;
            this.createdAt = createdAt;
            this.topicMoodCursor = level.getRandom().nextInt(3);
        }
    }

    private record EscapeRun(Member member, BlockPos target, long until) {
    }
}
