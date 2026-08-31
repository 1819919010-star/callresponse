package com.github.JumDa5he.callresponse.compat.game;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.block.BlockCChess;
import com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku;
import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.block.BlockWChess;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.SpawnParticlePackage;
import com.github.tartaricacid.touhoulittlemaid.blockentity.BlockEntityCChess;
import com.github.tartaricacid.touhoulittlemaid.blockentity.BlockEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.blockentity.BlockEntityJoy;
import com.github.tartaricacid.touhoulittlemaid.blockentity.BlockEntityWChess;
import com.github.tartaricacid.touhoulittlemaid.util.CChessUtil;
import com.github.tartaricacid.touhoulittlemaid.util.WChessUtil;
import net.minecraft.util.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 三种 TLM 棋盘的共享双席位与围观运行层。
 * 棋盘规则和局面仍由 TLM 原类负责，本类只把固定 player/maid 身份改为 SIDE_A/SIDE_B。
 */
public final class BoardGameManager {
    private static final String ROOT = "callresponse:board_game";
    private static final String PHASE = "Phase";
    private static final String SIDE_A = "SideA";
    private static final String SIDE_B = "SideB";
    private static final String SPECTATORS = "Spectators";
    private static final String REVISION = "Revision";
    private static final String NEXT_AI = "NextAiMove";
    private static final String LAST_PLAYER = "LastPlayer";
    private static final String LAST_SPEECH = "LastSpectatorSpeech";
    private static final String WINNER_NAME = "WinnerDisplayName";
    private static final int MAX_SPECTATORS = 4;
    private static final long SPECTATOR_SPEECH_GAP = 40L;
    private static final Set<BoardKey> ACTIVE = new HashSet<>();
    private static final Set<BoardKey> AI_PENDING = new HashSet<>();
    private static final Map<UUID, BoardKey> SPECTATING = new HashMap<>();
    /** 主动离席后必须先走出原棋盘范围，防止下一秒又抢回刚释放的围观位。 */
    private static final Map<UUID, BoardKey> SPECTATOR_EXIT_LOCKS = new HashMap<>();

    private static final String[] JOIN_KEYS = {
            "message.callresponse.game.spectator.join.1",
            "message.callresponse.game.spectator.join.2",
            "message.callresponse.game.spectator.join.3",
            "message.callresponse.game.spectator.join.4",
            "message.callresponse.game.spectator.join.5",
            "message.callresponse.game.spectator.join.6"
    };
    private static final String[] COMMENT_KEYS = {
            "message.callresponse.game.spectator.comment.1",
            "message.callresponse.game.spectator.comment.2",
            "message.callresponse.game.spectator.comment.3",
            "message.callresponse.game.spectator.comment.4",
            "message.callresponse.game.spectator.comment.5",
            "message.callresponse.game.spectator.comment.6"
    };

    public enum Side {
        A, B;

        public Side opposite() {
            return this == A ? B : A;
        }
    }

    private enum ParticipantType {
        EMPTY, PLAYER, MAID
    }

    private enum Phase {
        WAITING, PLAYING, FINISHED
    }

    private enum GameType {
        GOMOKU, CCHESS, WCHESS
    }

    private record BoardKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record BoardRef(ServerLevel level, BlockPos pos, BlockEntityJoy tile,
                            BlockState state, GameType type) {
        BoardKey key() {
            return new BoardKey(level.dimension(), pos.immutable());
        }
    }

    /** 后台 AI 只接收不可变/独立副本，绝不读取世界、实体或方块实体。 */
    private record AiSnapshot(GameType type, Object boardData, @Nullable Point lastPoint, int maidWins) {
    }

    public BoardGameManager() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBoardUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        // 客户端原版 Block 会发送固定“玩家方”的旧棋局包；先截住，避免服务端新逻辑落子后又被旧包改一次。
        if (event.getLevel().isClientSide()) {
            if (gameType(event.getLevel().getBlockState(event.getPos())) != null) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BoardRef board = findBoard(level, event.getPos());
        if (board == null) return;

        InteractionResult result = handlePlayerUse(board, event.getEntity(), event.getHitVec());
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBoardBroken(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BoardRef board = findBoard(level, event.getPos());
        if (board != null) {
            abort(board, false);
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            // ChunkEvent.Load 内绝不能再通过 Level 查询当前区块，否则会等待“正在执行的加载”自身完成而死锁。
            BoardRef board = boardFromLoadedBlockEntity(level, entity);
            if (board != null && board.tile().getPersistentData().contains(ROOT)) {
                ACTIVE.add(board.key());
            }
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockEntity entity : List.copyOf(chunk.getBlockEntities().values())) {
            BoardRef board = boardFromLoadedBlockEntity(level, entity);
            if (board != null && board.tile().getPersistentData().contains(ROOT)) {
                // ChunkEvent.Unload/服务器退出期间禁止 reset、refresh 或增删实体；这些操作会反向触发区块工作，
                // 在整合包中可能把服务端主线程锁死。这里只撤销运行时索引，重载后再由合法性检查自愈。
                CompoundTag boardRoot = root(board, false);
                if (boardRoot != null) forgetSpectatorIndexes(board, boardRoot);
                ACTIVE.remove(board.key());
                AI_PENDING.remove(board.key());
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (BoardKey key : List.copyOf(ACTIVE)) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.isLoaded(key.pos())) continue;
            BoardRef board = findBoard(level, key.pos());
            if (board == null) {
                ACTIVE.remove(key);
                AI_PENDING.remove(key);
                continue;
            }
            tickBoard(board);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 集成服务器返回标题界面后仍可能复用同一 JVM，静态索引不能带进下一次开档。
        ACTIVE.clear();
        AI_PENDING.clear();
        SPECTATING.clear();
        SPECTATOR_EXIT_LOCKS.clear();
    }

    /** MaidBoardGameTask 查询棋盘时使用：两个女仆席位都占用后才算满。 */
    public static boolean isBoardFull(ServerLevel level, BlockPos pos) {
        BoardRef board = findBoard(level, pos);
        if (board == null) return true;
        CompoundTag root = root(board, true);
        importVanillaSeat(board, root);
        return phase(root) == Phase.FINISHED
                || participantType(root, Side.A) == ParticipantType.MAID
                && participantType(root, Side.B) == ParticipantType.MAID;
    }

    /** 精准接管三种棋的 startMaidSit，不改其他娱乐设施。 */
    public static void seatMaid(EntityMaid maid, BlockState ignoredState,
                                ServerLevel level, BlockPos pos) {
        BoardRef board = findBoard(level, pos);
        if (board == null || !validMaidParticipant(maid)) return;
        CompoundTag root = root(board, true);
        importVanillaSeat(board, root);
        if (phase(root) == Phase.FINISHED || containsParticipant(root, maid.getUUID())) return;

        Side side = participantType(root, Side.A) == ParticipantType.EMPTY ? Side.A
                : participantType(root, Side.B) == ParticipantType.EMPTY ? Side.B : null;
        if (side == null) return;
        assignParticipant(board, root, side, maid, ParticipantType.MAID);
        startIfReady(board, root);
    }

    private static InteractionResult handlePlayerUse(BoardRef board, Player player, BlockHitResult hit) {
        CompoundTag root = root(board, true);
        importVanillaSeat(board, root);
        ACTIVE.add(board.key());

        if (isResetArea(board, hit)) {
            reset(board);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            return swapMaidSides(board, root, player);
        }

        Phase phase = phase(root);
        if (phase == Phase.PLAYING) {
            Side current = currentSide(board);
            if (current == null || participantType(root, current) == ParticipantType.MAID) {
                player.sendSystemMessage(Component.translatable("message.callresponse.game.not_your_turn"));
                return InteractionResult.FAIL;
            }
            if (!canPlayerControl(board, root, player)) return InteractionResult.FAIL;
            return applyPlayerMove(board, root, current, player, hit);
        }

        if (phase == Phase.FINISHED) {
            reset(board);
            return InteractionResult.SUCCESS;
        }
        // 玩家不再占用实体座位。棋局必须由至少一名女仆入座后开始，玩家只负责空着的一方。
        if (!hasMaidParticipant(root)) {
            player.sendSystemMessage(Component.translatable("message.touhou_little_maid.gomoku.no_maid"));
            return InteractionResult.FAIL;
        }
        startIfReady(board, root);
        return InteractionResult.SUCCESS;
    }

    private static boolean canPlayerControl(BoardRef board, CompoundTag root, Player player) {
        if (!MaidConfig.MAID_GOMOKU_OWNER_LIMIT.get()) return true;
        for (Side side : Side.values()) {
            Entity participant = participantEntity(board, root, side);
            if (participant instanceof EntityMaid maid && !maid.isOwnedBy(player)) {
                player.sendSystemMessage(Component.translatable("message.touhou_little_maid.gomoku.not_owner"));
                return false;
            }
        }
        return true;
    }

    /** 潜行空手右键棋盘：交换两个女仆席位，并从初始局面重新开局。 */
    private static InteractionResult swapMaidSides(BoardRef board, CompoundTag root, Player player) {
        if (!hasMaidParticipant(root) || !canPlayerControl(board, root, player)) return InteractionResult.FAIL;
        Entity sideAEntity = participantEntity(board, root, Side.A);
        Entity sideBEntity = participantEntity(board, root, Side.B);
        EntityMaid sideAMaid = sideAEntity instanceof EntityMaid maid ? maid : null;
        EntityMaid sideBMaid = sideBEntity instanceof EntityMaid maid ? maid : null;

        releaseParticipantSeat(board, root, Side.A);
        releaseParticipantSeat(board, root, Side.B);
        clearParticipant(root, Side.A);
        clearParticipant(root, Side.B);
        if (sideBMaid != null && sideBMaid.isAlive()) {
            assignParticipant(board, root, Side.A, sideBMaid, ParticipantType.MAID);
        }
        if (sideAMaid != null && sideAMaid.isAlive()) {
            assignParticipant(board, root, Side.B, sideAMaid, ParticipantType.MAID);
        }
        reset(board);
        player.sendSystemMessage(Component.translatable("message.callresponse.game.sides_swapped"));
        return InteractionResult.SUCCESS;
    }

    private static boolean hasMaidParticipant(CompoundTag root) {
        return participantType(root, Side.A) == ParticipantType.MAID
                || participantType(root, Side.B) == ParticipantType.MAID;
    }

    private static void startIfReady(BoardRef board, CompoundTag root) {
        if (!hasMaidParticipant(root)) {
            setPhase(board, root, Phase.WAITING);
            return;
        }
        boolean starting = phase(root) != Phase.PLAYING;
        setPhase(board, root, Phase.PLAYING);
        Side current = currentSide(board);
        if (current != null && participantType(root, current) == ParticipantType.MAID) {
            // 第一名女仆默认坐 A 方，棋盘初始也轮到 A 方；下一 tick 就开始思考并落第一步。
            root.putLong(NEXT_AI, board.level().getGameTime() + 1L);
        }
        board.tile().setChanged();
        if (starting) broadcast(board, Component.translatable("message.callresponse.game.game_started"));
    }

    private static void tickBoard(BoardRef board) {
        CompoundTag root = root(board, false);
        if (root == null) {
            ACTIVE.remove(board.key());
            return;
        }
        removeLegacyPlayerParticipants(board, root);
        validateSpectators(board, root);
        if (phase(root) != Phase.PLAYING) return;
        if (!validParticipant(board, root, Side.A) || !validParticipant(board, root, Side.B)
                || !hasMaidParticipant(root)) {
            abort(board, true);
            return;
        }
        Side current = currentSide(board);
        if (current == null) {
            abort(board, true);
            return;
        }
        if (participantType(root, current) == ParticipantType.MAID
                && board.level().getGameTime() >= root.getLongOr(NEXT_AI, 0L)) {
            scheduleAiMove(board, root, current);
        }
    }

    private static InteractionResult applyPlayerMove(BoardRef board, CompoundTag root, Side side,
                                                     Player player, BlockHitResult hit) {
        UUID previousPlayer = hasUuid(root, LAST_PLAYER) ? uuid(root, LAST_PLAYER) : null;
        root.store(LAST_PLAYER, UUIDUtil.CODEC, player.getUUID());
        boolean moved = switch (board.type()) {
            case GOMOKU -> moveGomoku(board, root, side, hit, null);
            case CCHESS -> moveCChess(board, root, side, hit, player);
            case WCHESS -> moveWChess(board, root, side, hit, player);
        };
        if (!moved) {
            if (previousPlayer == null) root.remove(LAST_PLAYER);
            else root.store(LAST_PLAYER, UUIDUtil.CODEC, previousPlayer);
        }
        return moved ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static boolean moveGomoku(BoardRef board, CompoundTag root, Side side,
                                      @Nullable BlockHitResult hit, @Nullable Point aiPoint) {
        BlockEntityGomoku tile = (BlockEntityGomoku) board.tile();
        if (tile.getStatue() != Statue.IN_PROGRESS) return false;
        Point point = aiPoint;
        if (point == null) {
            int[] click = gomokuClick(board, hit);
            if (click == null) return false;
            point = new Point(click[0], click[1], side == Side.A ? Point.BLACK : Point.WHITE);
        }
        byte[][] data = tile.getChessData();
        if (point.x < 0 || point.y < 0 || point.x >= data.length
                || point.y >= data[point.x].length || data[point.x][point.y] != Point.EMPTY) return false;
        int expected = side == Side.A ? Point.BLACK : Point.WHITE;
        if (point.type != expected) point = new Point(point.x, point.y, expected);
        tile.setChessData(point.x, point.y, point.type);
        Statue result = MaidGomokuAI.getStatue(data, point);
        tile.setStatue(result);
        swing(board, root, side);
        playMoveSound(board);
        bumpRevision(board, root);
        tile.refresh();
        if (result == Statue.WIN) finish(board, root, side, false);
        else if (result == Statue.DRAW) finish(board, root, null, true);
        else {
            tile.setPlayerTurn(side == Side.B);
            scheduleNextTurn(board, root);
        }
        return true;
    }

    private static boolean moveCChess(BoardRef board, CompoundTag root, Side side,
                                      BlockHitResult hit, @Nullable Player player) {
        BlockEntityCChess tile = (BlockEntityCChess) board.tile();
        com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position position = tile.getChessData();
        int now = CChessUtil.getClickPosition(chessClick(board, hit));
        if (now < 0 || !com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position.IN_BOARD(now)) return false;
        byte[] squares = position.squares;
        int selected = Mth.clamp(tile.getSelectChessPoint(), 0, squares.length - 1);
        byte oldPiece = squares[selected];
        byte nowPiece = squares[now];
        boolean ownNow = side == Side.A ? CChessUtil.isRed(nowPiece) : CChessUtil.isBlack(nowPiece);
        boolean ownOld = side == Side.A ? CChessUtil.isRed(oldPiece) : CChessUtil.isBlack(oldPiece);
        if (oldPiece <= 0 || !ownOld) {
            if (ownNow) {
                tile.setSelectChessPoint(now);
                tile.refresh();
                playMoveSound(board);
                return true;
            }
            return false;
        }
        if (ownNow) {
            tile.setSelectChessPoint(now);
            tile.refresh();
            playMoveSound(board);
            return true;
        }
        int move = com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position.MOVE(selected, now);
        int movedPiece = position.squares[com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.SRC(move)];
        if (!position.legalMove(move) || !position.makeMove(move)) {
            if (player != null) player.sendSystemMessage(Component.translatable("message.touhou_little_maid.cchess.check"));
            return false;
        }
        if (position.captured()) position.setIrrev();
        tile.addChessCounter();
        tile.setSelectChessPoint(now);
        return afterCChessMove(board, root, side);
    }

    private static boolean afterCChessMove(BoardRef board, CompoundTag root, Side side) {
        BlockEntityCChess tile = (BlockEntityCChess) board.tile();
        var position = tile.getChessData();
        boolean mate = position.isMate();
        tile.setCheckmate(mate);
        if (!mate) {
            if (CChessUtil.reachMoveLimit(position)) tile.setMoveNumberLimit(true);
            else if (CChessUtil.isRepeat(position)) tile.setRepeat(true);
        }
        swing(board, root, side);
        playMoveSound(board);
        bumpRevision(board, root);
        tile.refresh();
        if (mate) finish(board, root, side, false);
        else if (tile.isMoveNumberLimit() || tile.isRepeat()) finish(board, root, null, true);
        else scheduleNextTurn(board, root);
        return true;
    }

    private static boolean moveWChess(BoardRef board, CompoundTag root, Side side,
                                      BlockHitResult hit, @Nullable Player player) {
        BlockEntityWChess tile = (BlockEntityWChess) board.tile();
        com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position position = tile.getChessData();
        int now = WChessUtil.getClickPosition(chessClick(board, hit));
        if (now < 0 || !com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.IN_BOARD(now)) return false;
        byte[] squares = position.squares;
        int selected = Mth.clamp(tile.getSelectChessPoint(), 0, squares.length - 1);
        byte oldPiece = squares[selected];
        byte nowPiece = squares[now];
        boolean ownNow = side == Side.A ? WChessUtil.isWhite(nowPiece) : WChessUtil.isBlack(nowPiece);
        boolean ownOld = side == Side.A ? WChessUtil.isWhite(oldPiece) : WChessUtil.isBlack(oldPiece);
        if (oldPiece <= 0 || !ownOld) {
            if (ownNow) {
                tile.setSelectChessPoint(now);
                tile.refresh();
                playMoveSound(board);
                return true;
            }
            return false;
        }
        if (ownNow) {
            tile.setSelectChessPoint(now);
            tile.refresh();
            playMoveSound(board);
            return true;
        }
        int move = com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.MOVE(selected, now);
        if (!position.legalMove(move) || !position.makeMove(move)) {
            if (player != null) player.sendSystemMessage(Component.translatable("message.touhou_little_maid.cchess.check"));
            return false;
        }
        if (position.captured()
                || com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.PIECE_TYPE(oldPiece)
                == com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.PIECE_PAWN) {
            position.setIrrev();
        }
        tile.addChessCounter();
        tile.setSelectChessPoint(now);
        return afterWChessMove(board, root, side);
    }

    private static boolean afterWChessMove(BoardRef board, CompoundTag root, Side side) {
        BlockEntityWChess tile = (BlockEntityWChess) board.tile();
        var position = tile.getChessData();
        boolean mate = position.isMate();
        tile.setCheckmate(mate);
        if (!mate) {
            if (WChessUtil.reachMoveLimit(position)) tile.setMoveNumberLimit(true);
            else if (WChessUtil.isRepeat(position)) tile.setRepeat(true);
        }
        swing(board, root, side);
        playMoveSound(board);
        bumpRevision(board, root);
        tile.refresh();
        if (mate) finish(board, root, side, false);
        else if (tile.isMoveNumberLimit() || tile.isRepeat()) finish(board, root, null, true);
        else scheduleNextTurn(board, root);
        return true;
    }

    private static void scheduleAiMove(BoardRef board, CompoundTag root, Side side) {
        BoardKey key = board.key();
        if (!AI_PENDING.add(key)) return;
        int revision = root.getIntOr(REVISION, 0);
        UUID maidId = participantUuid(root, side);
        AiSnapshot snapshot = createAiSnapshot(board, maidId);
        CompletableFuture.supplyAsync(() -> calculateAiMove(snapshot), Util.backgroundExecutor())
                .whenComplete((move, error) -> board.level().getServer().execute(() -> {
                    AI_PENDING.remove(key);
                    BoardRef current = findBoard(board.level(), board.pos());
                    if (error != null) {
                        CallResponseMod.LOGGER.error("Board-game AI failed at {}", board.pos(), error);
                        if (current != null) {
                            root(current, true).putLong(NEXT_AI, current.level().getGameTime() + 100L);
                            current.tile().setChanged();
                        }
                        return;
                    }
                    if (current == null) return;
                    CompoundTag currentRoot = root(current, false);
                    if (currentRoot == null || phase(currentRoot) != Phase.PLAYING
                            || currentRoot.getIntOr(REVISION, 0) != revision
                            || currentSide(current) != side
                            || !maidId.equals(participantUuid(currentRoot, side))) return;
                    applyAiMove(current, currentRoot, side, move);
                }));
    }

    private static AiSnapshot createAiSnapshot(BoardRef board, UUID maidId) {
        int wins = 0;
        Entity entity = board.level().getEntity(maidId);
        if (entity instanceof EntityMaid maid) wins = maid.getGameManager().getGomokuWinCount();
        return switch (board.type()) {
            case GOMOKU -> {
                BlockEntityGomoku tile = (BlockEntityGomoku) board.tile();
                byte[][] source = tile.getChessData();
                byte[][] copy = new byte[source.length][];
                for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
                yield new AiSnapshot(GameType.GOMOKU, copy, tile.getLatestChessPoint(), wins);
            }
            case CCHESS -> new AiSnapshot(GameType.CCHESS,
                    ((BlockEntityCChess) board.tile()).getChessData().toFen(), null, wins);
            case WCHESS -> new AiSnapshot(GameType.WCHESS,
                    ((BlockEntityWChess) board.tile()).getChessData().toFen(), null, wins);
        };
    }

    private static Object calculateAiMove(AiSnapshot snapshot) {
        return switch (snapshot.type()) {
            case GOMOKU -> {
                byte[][] data = (byte[][]) snapshot.boardData();
                Point last = snapshot.lastPoint();
                if (last == null || last == Point.NULL || last.type == Point.EMPTY) {
                    yield new Point(7, 7, Point.BLACK);
                }
                var service = MaidGomokuAI.getService(snapshot.maidWins());
                synchronized (service) {
                    yield service.getPoint(data, last);
                }
            }
            case CCHESS -> {
                var position = new com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position();
                position.fromFen((String) snapshot.boardData());
                yield new com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Search(position, 12).searchMain(1000);
            }
            case WCHESS -> {
                var position = new com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position();
                position.fromFen((String) snapshot.boardData());
                yield new com.github.tartaricacid.touhoulittlemaid.api.game.chess.Search(position, 12).searchMain(1000);
            }
        };
    }

    private static void applyAiMove(BoardRef board, CompoundTag root, Side side, Object move) {
        switch (board.type()) {
            case GOMOKU -> moveGomoku(board, root, side, null, (Point) move);
            case CCHESS -> {
                int encoded = (Integer) move;
                var tile = (BlockEntityCChess) board.tile();
                var position = tile.getChessData();
                if (position.legalMove(encoded) && position.makeMove(encoded)) {
                    if (position.captured()) position.setIrrev();
                    tile.addChessCounter();
                    tile.setSelectChessPoint(com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position.DST(encoded));
                    afterCChessMove(board, root, side);
                } else abort(board, true);
            }
            case WCHESS -> {
                int encoded = (Integer) move;
                var tile = (BlockEntityWChess) board.tile();
                var position = tile.getChessData();
                int movedPiece = position.squares[com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.SRC(encoded)];
                if (position.legalMove(encoded) && position.makeMove(encoded)) {
                    if (position.captured()
                            || com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.PIECE_TYPE(movedPiece)
                            == com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.PIECE_PAWN) position.setIrrev();
                    tile.addChessCounter();
                    tile.setSelectChessPoint(com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.DST(encoded));
                    afterWChessMove(board, root, side);
                } else abort(board, true);
            }
        }
    }

    private static void scheduleNextTurn(BoardRef board, CompoundTag root) {
        root.putLong(NEXT_AI, board.level().getGameTime() + thinkDelay(board, root));
        board.tile().setChanged();
    }

    private static long thinkDelay(BoardRef board, CompoundTag root) {
        boolean maidVsMaid = participantType(root, Side.A) == ParticipantType.MAID
                && participantType(root, Side.B) == ParticipantType.MAID;
        if (maidVsMaid) {
            return board.type() == GameType.GOMOKU
                    ? 6L + board.level().getRandom().nextInt(9)
                    : 8L + board.level().getRandom().nextInt(11);
        }
        return 15L + board.level().getRandom().nextInt(16);
    }

    @Nullable
    private static Side currentSide(BoardRef board) {
        return switch (board.type()) {
            case GOMOKU -> ((BlockEntityGomoku) board.tile()).isPlayerTurn() ? Side.A : Side.B;
            case CCHESS -> ((BlockEntityCChess) board.tile()).getChessData().sdPlayer == 0 ? Side.A : Side.B;
            case WCHESS -> ((BlockEntityWChess) board.tile()).getChessData().sdPlayer == 0 ? Side.A : Side.B;
        };
    }

    private static void finish(BoardRef board, CompoundTag root, @Nullable Side winner, boolean draw) {
        setPhase(board, root, Phase.FINISHED);
        AI_PENDING.remove(board.key());
        if (draw) {
            root.remove(WINNER_NAME);
            broadcast(board, Component.translatable("message.callresponse.game.draw"));
        } else if (winner != null) {
            Component name = participantName(board, root, winner);
            ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, name)
                    .resultOrPartial(error -> CallResponseMod.LOGGER.error("Failed to save board winner: {}", error))
                    .ifPresent(encoded -> root.put(WINNER_NAME, encoded));
            broadcast(board, Component.translatable("message.callresponse.board_game.winner", name));
            applyOriginalResultRewards(board, root, winner);
            Entity entity = participantEntity(board, root, winner);
            if (entity instanceof EntityMaid maid) maid.getGameManager().markStatue(true);
            Entity loser = participantEntity(board, root, winner.opposite());
            if (loser instanceof EntityMaid maid) maid.getGameManager().markStatue(false);
        }
        releaseAllSpectators(board, root);
        // 原版棋局结束后女仆会继续坐在棋盘边展示胜负动作，直到玩家刷新棋盘。
        // 不能在这里拆座椅，否则 GameRecordManager 会在下一 tick 清掉胜负状态，动画也无法出现。
        board.tile().refresh();
        ACTIVE.add(board.key());
    }

    /** 客户端棋盘渲染使用：名称在胜负确定时固化，不依赖结束后继续寻找实体。 */
    @Nullable
    public static Component getWinnerDisplayText(BlockEntityJoy tile) {
        CompoundTag root = tile.getPersistentData().getCompoundOrEmpty(ROOT);
        if (!Phase.FINISHED.name().equals(root.getStringOr(PHASE, "")) || !root.contains(WINNER_NAME)) {
            return null;
        }
        try {
            if (tile.getLevel() == null) return null;
            Component name = readWinnerName(root);
            if (name == null) return null;
            return Component.translatable("message.callresponse.board_game.winner", name)
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_PURPLE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 玩家与自己的女仆对弈时，沿用 TLM 原本的好感、棋力记录与进度奖励。 */
    private static void applyOriginalResultRewards(BoardRef board, CompoundTag root, Side winner) {
        if (participantType(root, winner) == ParticipantType.MAID) return;
        Entity winnerEntity = participantEntity(board, root, winner);
        Entity loserEntity = participantEntity(board, root, winner.opposite());
        ServerPlayer player = winnerEntity instanceof ServerPlayer directPlayer ? directPlayer
                : hasUuid(root, LAST_PLAYER) ? board.level().getServer().getPlayerList().getPlayer(uuid(root, LAST_PLAYER)) : null;
        if (player == null || !(loserEntity instanceof EntityMaid maid)
                || !maid.isOwnedBy(player)) return;

        switch (board.type()) {
            case GOMOKU -> {
                maid.getFavorabilityManager().apply(Type.GOMOKU_WIN);
                int rankBefore = MaidGomokuAI.getRank(maid);
                maid.getGameManager().increaseGomokuWinCount();
                if (rankBefore < MaidGomokuAI.getRank(maid)) {
                    NetworkHandler.sendToClientPlayer(
                            new SpawnParticlePackage(maid.getId(), SpawnParticlePackage.Type.RANK_UP), player);
                }
                InitTrigger.MAID_EVENT.get().trigger(player, TriggerType.WIN_GOMOKU);
            }
            case CCHESS -> {
                maid.getFavorabilityManager().apply(Type.CCHESS_WIN);
                InitTrigger.MAID_EVENT.get().trigger(player, TriggerType.WIN_CCHESS);
            }
            case WCHESS -> {
                maid.getFavorabilityManager().apply(Type.WCHESS_WIN);
                InitTrigger.MAID_EVENT.get().trigger(player, TriggerType.WIN_WCHESS);
            }
        }
    }

    private static Component participantName(BoardRef board, CompoundTag root, Side side) {
        Entity entity = participantEntity(board, root, side);
        if (entity instanceof EntityMaid maid) {
            Optional<com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo> info =
                    ServerCustomPackLoader.SERVER_MAID_MODELS.getInfo(maid.getModelId());
            if (info.isPresent()) {
                String raw = info.get().getName();
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    return Component.translatable(raw.substring(1, raw.length() - 1));
                }
                return Component.literal(raw);
            }
            return maid.getDisplayName();
        }
        if (participantType(root, side) == ParticipantType.EMPTY && hasUuid(root, LAST_PLAYER)) {
            ServerPlayer player = board.level().getServer().getPlayerList().getPlayer(uuid(root, LAST_PLAYER));
            if (player != null) return player.getDisplayName();
        }
        return entity == null ? Component.translatable("entity.touhou_little_maid.maid") : entity.getDisplayName();
    }

    @Nullable
    private static Component readWinnerName(CompoundTag root) {
        Tag encoded = root.get(WINNER_NAME);
        if (encoded == null) {
            return null;
        }
        if (encoded instanceof StringTag legacyJson) {
            try {
                return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(legacyJson.value()))
                        .resultOrPartial(error -> CallResponseMod.LOGGER.error("Failed to read legacy board winner: {}", error))
                        .orElse(null);
            } catch (RuntimeException exception) {
                return Component.literal(legacyJson.value());
            }
        }
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encoded)
                .resultOrPartial(error -> CallResponseMod.LOGGER.error("Failed to read board winner: {}", error))
                .orElse(null);
    }

    private static void reset(BoardRef board) {
        CompoundTag root = root(board, true);
        root.remove(WINNER_NAME);
        releaseAllSpectators(board, root);
        bumpRevision(board, root);
        // 兼容旧 2.0.9：玩家不再保留实体座位，只留下仍有效且仍坐在本棋盘座位上的女仆。
        removeLegacyPlayerParticipants(board, root);
        for (Side side : Side.values()) {
            Entity entity = participantEntity(board, root, side);
            if (entity instanceof EntityMaid maid && isRidingAssignedSeat(root, side, maid)) {
                maid.getGameManager().resetStatue();
            } else if (participantType(root, side) != ParticipantType.EMPTY) {
                releaseParticipantSeat(board, root, side);
                clearParticipant(root, side);
            }
        }
        switch (board.type()) {
            case GOMOKU -> ((BlockEntityGomoku) board.tile()).reset();
            case CCHESS -> ((BlockEntityCChess) board.tile()).reset();
            case WCHESS -> ((BlockEntityWChess) board.tile()).reset();
        }
        root.remove(LAST_PLAYER);
        setPhase(board, root, Phase.WAITING);
        updatePrimarySitId(board, root);
        board.tile().refresh();
        AI_PENDING.remove(board.key());
        if (hasMaidParticipant(root)) {
            ACTIVE.add(board.key());
            startIfReady(board, root);
        } else {
            board.tile().getPersistentData().remove(ROOT);
            board.tile().setSitId(Util.NIL_UUID);
            ACTIVE.remove(board.key());
        }
        board.level().playSound(null, board.pos(), InitSounds.GOMOKU_RESET.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void abort(BoardRef board, boolean resetBoard) {
        CompoundTag root = root(board, false);
        if (root != null) {
            releaseAllSpectators(board, root);
            releaseParticipantSeat(board, root, Side.A);
            releaseParticipantSeat(board, root, Side.B);
            board.tile().getPersistentData().remove(ROOT);
        }
        board.tile().setSitId(Util.NIL_UUID);
        if (resetBoard) {
            switch (board.type()) {
                case GOMOKU -> ((BlockEntityGomoku) board.tile()).reset();
                case CCHESS -> ((BlockEntityCChess) board.tile()).reset();
                case WCHESS -> ((BlockEntityWChess) board.tile()).reset();
            }
            board.tile().refresh();
        }
        ACTIVE.remove(board.key());
        AI_PENDING.remove(board.key());
    }

    /** 正式低优先级 Behavior 用：寻找最近的可围观棋局并申请稳定席位。 */
    public static boolean tryStartSpectating(ServerLevel level, EntityMaid maid) {
        if (!validSpectator(maid) || SPECTATING.containsKey(maid.getUUID())) return false;
        BoardKey exitLock = SPECTATOR_EXIT_LOCKS.get(maid.getUUID());
        if (exitLock != null) {
            if (exitLock.dimension().equals(level.dimension()) && level.isLoaded(exitLock.pos())
                    && exitLock.pos().distToCenterSqr(maid.position()) <= 12.25D) {
                return false;
            }
            SPECTATOR_EXIT_LOCKS.remove(maid.getUUID());
        }
        List<BoardRef> candidates = new ArrayList<>();
        // 旧实现对每只女仆每次启动 Behavior 都扫描 7x5x7 个方块；女仆较多时会直接拖死服务器 tick。
        // 正在进行的棋盘本来就已登记在 ACTIVE，围观搜索只遍历这个通常为空或很小的集合。
        for (BoardKey key : List.copyOf(ACTIVE)) {
            if (!key.dimension().equals(level.dimension()) || !level.isLoaded(key.pos())) continue;
            BoardRef board = findBoard(level, key.pos());
            CompoundTag boardRoot = board == null ? null : root(board, false);
            if (board != null && board.pos().distToCenterSqr(maid.position()) <= 9.0D
                    && boardRoot != null && phase(boardRoot) == Phase.PLAYING
                    && !candidates.contains(board)) candidates.add(board);
        }
        candidates.sort(Comparator.comparingDouble(board -> board.pos().distToCenterSqr(maid.position())));
        for (BoardRef board : candidates) {
            if (claimSpectator(board, maid)) return true;
        }
        return false;
    }

    public static boolean isSpectating(EntityMaid maid) {
        return SPECTATING.containsKey(maid.getUUID());
    }

    /** EntitySit Mixin 使用：只豁免当前棋局登记的真实围观座椅，其他娱乐座椅保持 TLM 原逻辑。 */
    public static boolean isActiveSpectatorSeat(EntitySit seat, EntityMaid maid) {
        BoardKey key = SPECTATING.get(maid.getUUID());
        if (key == null || !(maid.level() instanceof ServerLevel level)
                || !key.dimension().equals(level.dimension()) || !level.isLoaded(key.pos())) return false;
        BoardRef board = findBoard(level, key.pos());
        CompoundTag root = board == null ? null : root(board, false);
        CompoundTag entry = spectatorEntry(root, maid.getUUID());
        return root != null && phase(root) == Phase.PLAYING && entry != null
                && entry.getBooleanOr("Seated", false) && hasUuid(entry, "Seat")
                && uuid(entry, "Seat").equals(seat.getUUID());
    }

    public static void tickSpectator(EntityMaid maid) {
        BoardKey key = SPECTATING.get(maid.getUUID());
        if (key == null || !(maid.level() instanceof ServerLevel level)
                || !key.dimension().equals(level.dimension()) || !level.isLoaded(key.pos())) {
            releaseSpectator(maid);
            return;
        }
        BoardRef board = findBoard(level, key.pos());
        if (board == null) {
            releaseSpectator(maid);
            return;
        }
        CompoundTag root = root(board, false);
        if (root == null) {
            releaseSpectator(maid);
            return;
        }
        CompoundTag entry = spectatorEntry(root, maid.getUUID());
        if (phase(root) != Phase.PLAYING || entry == null || !validSpectator(maid)) {
            releaseSpectator(maid);
            return;
        }
        int slot = entry.getIntOr("Slot", 0);
        Vec3 target = spectatorPosition(board, slot);
        boolean ridingAssignedSeat = isRidingSpectatorSeat(entry, maid);
        if (ridingAssignedSeat && !entry.getBooleanOr("Seated", false)) {
            // 兼容修复前已经坐好的围观者：只补记状态，不重新创建座椅。
            entry.putBoolean("Seated", true);
            root.put(SPECTATORS, replaceSpectator(root.getListOrEmpty(SPECTATORS), entry));
            board.tile().setChanged();
        }
        if (entry.getBooleanOr("Seated", false)) {
            if (!ridingAssignedSeat) {
                // 已经坐稳后又站起，立即退出并空出席位；绝不沿用旧条目反复把她绑回去。
                removeSpectator(board, root, maid.getUUID(), true);
                return;
            }
        } else if (!ridingAssignedSeat) {
            if (maid.isPassenger()) {
                removeSpectator(board, root, maid.getUUID(), true);
                return;
            }
            if (maid.distanceToSqr(target) > 1.0D) {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new net.minecraft.world.entity.ai.memory.WalkTarget(
                                new net.minecraft.world.entity.ai.behavior.PositionTracker() {
                                    @Override public Vec3 currentPosition() { return target; }
                                    @Override public BlockPos currentBlockPosition() { return BlockPos.containing(target); }
                                    @Override public boolean isVisibleBy(LivingEntity entity) { return true; }
                                }, 0.5F, 0));
                return;
            }
            EntitySit seat = createSeat(board, target, facingToCenter(board, target));
            entry.store("Seat", UUIDUtil.CODEC, seat.getUUID());
            if (!maid.startRiding(seat, true, false)) {
                seat.discard();
                removeSpectator(board, root, maid.getUUID(), true);
                return;
            }
            entry.putBoolean("Seated", true);
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            root.put(SPECTATORS, replaceSpectator(root.getListOrEmpty(SPECTATORS), entry));
            board.tile().setChanged();
            return;
        }
        long now = level.getGameTime();
        if (!entry.getBooleanOr("JoinSpoken", false)) {
            if (now - root.getLongOr(LAST_SPEECH, 0L) >= SPECTATOR_SPEECH_GAP) {
                int join = maid.getRandom().nextInt(JOIN_KEYS.length);
                maid.getChatBubbleManager().addTextChatBubble(JOIN_KEYS[join]);
                entry.putBoolean("JoinSpoken", true);
                entry.putLong("NextComment", now + 500L + maid.getRandom().nextInt(201));
                root.putLong(LAST_SPEECH, now);
                root.put(SPECTATORS, replaceSpectator(root.getListOrEmpty(SPECTATORS), entry));
                board.tile().setChanged();
            }
            return;
        }
        if (now >= entry.getLongOr("NextComment", 0L) && now - root.getLongOr(LAST_SPEECH, 0L) >= SPECTATOR_SPEECH_GAP) {
            int last = entry.getIntOr("LastComment", 0);
            int next = maid.getRandom().nextInt(COMMENT_KEYS.length - 1);
            if (next >= last) next++;
            next %= COMMENT_KEYS.length;
            maid.getChatBubbleManager().addTextChatBubble(COMMENT_KEYS[next]);
            entry.putInt("LastComment", next);
            entry.putLong("NextComment", now + 500L + maid.getRandom().nextInt(201));
            root.putLong(LAST_SPEECH, now);
            root.put(SPECTATORS, replaceSpectator(root.getListOrEmpty(SPECTATORS), entry));
            board.tile().setChanged();
        }
    }

    public static void releaseSpectator(EntityMaid maid) {
        BoardKey key = SPECTATING.remove(maid.getUUID());
        if (key == null || !(maid.level() instanceof ServerLevel level) || !level.isLoaded(key.pos())) {
            return;
        }
        BoardRef board = findBoard(level, key.pos());
        if (board == null) return;
        CompoundTag root = root(board, false);
        if (root == null) return;
        removeSpectator(board, root, maid.getUUID());
    }

    private static boolean claimSpectator(BoardRef board, EntityMaid maid) {
        CompoundTag root = root(board, true);
        if (phase(root) != Phase.PLAYING || containsParticipant(root, maid.getUUID())) return false;
        ListTag list = root.getListOrEmpty(SPECTATORS);
        boolean[] occupied = new boolean[MAX_SPECTATORS];
        for (Tag tag : list) {
            CompoundTag entry = (CompoundTag) tag;
            int slot = entry.getIntOr("Slot", 0);
            if (0 <= slot && slot < occupied.length) occupied[slot] = true;
        }
        int slot = -1;
        for (int i = 0; i < occupied.length; i++) if (!occupied[i]) { slot = i; break; }
        if (slot < 0) return false;
        CompoundTag entry = new CompoundTag();
        entry.store("Maid", UUIDUtil.CODEC, maid.getUUID());
        entry.putInt("Slot", slot);
        entry.putInt("LastComment", -1);
        entry.putBoolean("JoinSpoken", false);
        entry.putBoolean("Seated", false);
        entry.putLong("NextComment", board.level().getGameTime() + 500L + maid.getRandom().nextInt(201));
        list.add(entry);
        root.put(SPECTATORS, list);
        board.tile().setChanged();
        ACTIVE.add(board.key());
        SPECTATING.put(maid.getUUID(), board.key());
        return true;
    }

    private static void validateSpectators(BoardRef board, CompoundTag root) {
        ListTag copy = root.getListOrEmpty(SPECTATORS).copy();
        for (Tag tag : copy) {
            CompoundTag entry = (CompoundTag) tag;
            UUID maidId = hasUuid(entry, "Maid") ? uuid(entry, "Maid") : Util.NIL_UUID;
            Entity entity = board.level().getEntity(maidId);
            if (!(entity instanceof EntityMaid maid) || !validSpectator(maid)) {
                removeSpectator(board, root, maidId);
            } else {
                BoardKey claimed = SPECTATING.putIfAbsent(maidId, board.key());
                if (claimed != null && !claimed.equals(board.key())) {
                    // 两个区块同时恢复时，只保留最先恢复的围观席，避免来回抢人。
                    removeSpectator(board, root, maidId);
                }
            }
        }
    }

    private static void removeSpectator(BoardRef board, CompoundTag root, UUID maidId) {
        removeSpectator(board, root, maidId, false);
    }

    private static void removeSpectator(BoardRef board, CompoundTag root, UUID maidId, boolean lockUntilLeaving) {
        ListTag old = root.getListOrEmpty(SPECTATORS);
        ListTag replacement = new ListTag();
        for (Tag tag : old) {
            CompoundTag entry = (CompoundTag) tag;
            if (hasUuid(entry, "Maid") && uuid(entry, "Maid").equals(maidId)) {
                Entity entity = board.level().getEntity(maidId);
                if (entity instanceof EntityMaid maid && isRidingSpectatorSeat(entry, maid)) maid.stopRiding();
                discardSeat(board.level(), entry, "Seat");
            } else replacement.add(entry.copy());
        }
        root.put(SPECTATORS, replacement);
        board.tile().setChanged();
        SPECTATING.remove(maidId, board.key());
        if (lockUntilLeaving) SPECTATOR_EXIT_LOCKS.put(maidId, board.key());
    }

    private static boolean isRidingSpectatorSeat(CompoundTag entry, EntityMaid maid) {
        return hasUuid(entry, "Seat") && maid.getVehicle() instanceof EntitySit
                && maid.getVehicle().getUUID().equals(uuid(entry, "Seat"));
    }

    private static void releaseAllSpectators(BoardRef board, CompoundTag root) {
        ListTag list = root.getListOrEmpty(SPECTATORS);
        for (Tag tag : list) {
            CompoundTag entry = (CompoundTag) tag;
            UUID maidId = hasUuid(entry, "Maid") ? uuid(entry, "Maid") : Util.NIL_UUID;
            Entity entity = board.level().getEntity(maidId);
            if (entity != null) entity.stopRiding();
            discardSeat(board.level(), entry, "Seat");
            SPECTATING.remove(maidId, board.key());
        }
        root.put(SPECTATORS, new ListTag());
    }

    /** 区块卸载专用：只忘掉 JVM 内索引，不写 NBT、不碰实体和方块实体。 */
    private static void forgetSpectatorIndexes(BoardRef board, CompoundTag root) {
        ListTag list = root.getListOrEmpty(SPECTATORS);
        for (Tag tag : list) {
            CompoundTag entry = (CompoundTag) tag;
            if (hasUuid(entry, "Maid")) SPECTATING.remove(uuid(entry, "Maid"), board.key());
        }
    }

    private static boolean validSpectator(EntityMaid maid) {
        // 围观只看基础身份和距离：不再被日程、工作、跟随、普通移动或战斗记忆挡住。
        return maid.isAlive() && maid.isTame() && (maid.getOwner() == null ? null : maid.getOwner().getUUID()) != null
                && !isParticipantInActiveBoard(maid.getUUID());
    }

    private static boolean validMaidParticipant(EntityMaid maid) {
        return maid.isAlive() && maid.isTame() && (maid.getOwner() == null ? null : maid.getOwner().getUUID()) != null
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !SPECTATING.containsKey(maid.getUUID());
    }

    private static boolean validParticipant(BoardRef board, CompoundTag root, Side side) {
        ParticipantType type = participantType(root, side);
        // 空侧就是可以由主人站在任意位置操作的一方，不需要实体座位。
        if (type == ParticipantType.EMPTY) return true;
        Entity entity = participantEntity(board, root, side);
        if (type == ParticipantType.PLAYER) return false;
        if (type == ParticipantType.MAID) {
            return entity instanceof EntityMaid maid && validMaidParticipant(maid)
                    && isRidingAssignedSeat(root, side, entity);
        }
        return false;
    }

    private static boolean isRidingAssignedSeat(CompoundTag root, Side side, @Nullable Entity entity) {
        CompoundTag tag = participant(root, side);
        return entity != null && hasUuid(tag, "Seat") && entity.getVehicle() instanceof EntitySit
                && entity.getVehicle().getUUID().equals(uuid(tag, "Seat"));
    }

    private static void assignParticipant(BoardRef board, CompoundTag root, Side side,
                                          Entity entity, ParticipantType type) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.name());
        tag.store("Uuid", UUIDUtil.CODEC, entity.getUUID());
        Vec3 pos = participantPosition(board, side);
        EntitySit seat = createSeat(board, pos, facingToCenter(board, pos));
        tag.store("Seat", UUIDUtil.CODEC, seat.getUUID());
        entity.stopRiding();
        entity.startRiding(seat, true, false);
        root.put(side == Side.A ? SIDE_A : SIDE_B, tag);
        updatePrimarySitId(board, root);
        board.tile().setChanged();
        ACTIVE.add(board.key());
    }

    private static void moveParticipant(BoardRef board, CompoundTag root, Side from, Side to) {
        CompoundTag source = participant(root, from).copy();
        Entity entity = participantEntity(board, root, from);
        releaseParticipantSeat(board, root, from);
        clearParticipant(root, from);
        if (entity != null) assignParticipant(board, root, to, entity,
                ParticipantType.valueOf(source.getStringOr("Type", "")));
    }

    private static void releaseParticipantSeat(BoardRef board, CompoundTag root, Side side) {
        CompoundTag tag = participant(root, side);
        Entity participant = participantEntity(board, root, side);
        if (participant != null) participant.stopRiding();
        discardSeat(board.level(), tag, "Seat");
    }

    private static void discardSeat(ServerLevel level, CompoundTag tag, String key) {
        if (!hasUuid(tag, key)) return;
        Entity seat = level.getEntity(uuid(tag, key));
        if (seat instanceof EntitySit) seat.discard();
    }

    private static EntitySit createSeat(BoardRef board, Vec3 position, float yaw) {
        EntitySit seat = new EntitySit(board.level(), position, Type.GOMOKU.getTypeName(), board.pos());
        seat.setYRot(yaw);
        board.level().addFreshEntity(seat);
        return seat;
    }

    private static Vec3 participantPosition(BoardRef board, Side side) {
        Direction direction = sideADirection(board);
        if (side == Side.B) direction = direction.getOpposite();
        double distance = board.type() == GameType.GOMOKU ? 1.5D : 2.0D;
        return Vec3.atLowerCornerWithOffset(board.pos(),
                0.5D + direction.getStepX() * distance, 0.1D,
                0.5D + direction.getStepZ() * distance);
    }

    private static Vec3 spectatorPosition(BoardRef board, int slot) {
        double[][] local = {{-2.0D, -2.0D}, {2.0D, -2.0D}, {2.0D, 2.0D}, {-2.0D, 2.0D}};
        double x = local[Mth.clamp(slot, 0, 3)][0];
        double z = local[Mth.clamp(slot, 0, 3)][1];
        Direction facing = boardFacing(board);
        for (int i = 0; i < facing.get2DDataValue(); i++) {
            double oldX = x;
            x = -z;
            z = oldX;
        }
        return Vec3.atLowerCornerWithOffset(board.pos(), 0.5D + x, 0.1D, 0.5D + z);
    }

    private static float facingToCenter(BoardRef board, Vec3 from) {
        Vec3 center = Vec3.atCenterOf(board.pos());
        return (float) (Mth.atan2(center.z - from.z, center.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static Side clickedSide(BoardRef board, Vec3 hit) {
        Direction a = sideADirection(board);
        Vec3 center = Vec3.atCenterOf(board.pos());
        double dot = (hit.x - center.x) * a.getStepX() + (hit.z - center.z) * a.getStepZ();
        return dot >= 0.0D ? Side.A : Side.B;
    }

    private static Direction sideADirection(BoardRef board) {
        Direction facing = boardFacing(board);
        // A 固定为原版玩家的黑/红/白侧，B 固定为原版女仆所在侧，保证老玩法的视觉方向不变。
        return board.type() == GameType.GOMOKU ? facing.getCounterClockWise() : facing;
    }

    private static Direction boardFacing(BoardRef board) {
        return switch (board.type()) {
            case GOMOKU -> board.state().getValue(BlockGomoku.FACING);
            case CCHESS, WCHESS -> board.state().getValue(BlockJoy.FACING);
        };
    }

    private static Vec3 chessClick(BoardRef board, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = board.level().getBlockState(clicked);
        GomokuPart part = part(clickedState);
        return hit.getLocation().subtract(clicked.getX(), clicked.getY(), clicked.getZ())
                .add(part.getPosX() - 0.5D, 0.0D, part.getPosY() - 0.5D)
                .yRot(boardFacing(board).toYRot() * Mth.DEG_TO_RAD);
    }

    @Nullable
    private static int[] gomokuClick(BoardRef board, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        GomokuPart part = part(board.level().getBlockState(clicked));
        Vec3 location = hit.getLocation().subtract(clicked.getX(), clicked.getY(), clicked.getZ());
        return switch (part) {
            case LEFT_UP -> gridData(location.x, location.z, 0.505, 0.505, 0.54, 0.54, 0, 0);
            case UP -> gridData(location.x, location.z, 0.037, 0.505, 0.08, 0.54, 4, 0);
            case RIGHT_UP -> gridData(location.x, location.z, -0.037, 0.505, -0.01, 0.54, 11, 0);
            case LEFT_CENTER -> gridData(location.x, location.z, 0.505, 0.037, 0.54, 0.07, 0, 4);
            case CENTER -> gridData(location.x, location.z, 0.037, 0.037, 0.08, 0.07, 4, 4);
            case RIGHT_CENTER -> gridData(location.x, location.z, -0.037, 0.037, -0.01, 0.07, 11, 4);
            case LEFT_DOWN -> gridData(location.x, location.z, 0.505, 0, 0.54, 0, 0, 11);
            case DOWN -> gridData(location.x, location.z, 0.037, 0, 0.08, 0, 4, 11);
            case RIGHT_DOWN -> gridData(location.x, location.z, -0.037, 0, -0.01, 0, 11, 11);
        };
    }

    @Nullable
    private static int[] gridData(double x, double y, double xOffset, double yOffset,
                                  double xStartOffset, double yStartOffset, int xIndexOffset, int yIndexOffset) {
        int xIndex = (int) ((x - xOffset) / 0.1316D);
        int yIndex = (int) ((y - yOffset) / 0.1316D);
        double xStart = xStartOffset + xIndex * 0.1316D;
        double yStart = yStartOffset + yIndex * 0.1316D;
        xIndex += xIndexOffset;
        yIndex += yIndexOffset;
        if (0 <= xIndex && xIndex <= 14 && 0 <= yIndex && yIndex <= 14
                && xStart < x && x < xStart + 0.07D && yStart < y && y < yStart + 0.07D) {
            return new int[]{xIndex, yIndex};
        }
        return null;
    }

    private static boolean isResetArea(BoardRef board, BlockHitResult hit) {
        if (board.type() == GameType.CCHESS) return CChessUtil.isClickResetArea(chessClick(board, hit));
        if (board.type() == GameType.WCHESS) return WChessUtil.isClickResetArea(chessClick(board, hit));
        BlockPos clicked = hit.getBlockPos();
        BlockState state = board.level().getBlockState(clicked);
        GomokuPart part = part(state);
        Vec3 location = hit.getLocation().subtract(clicked.getX(), clicked.getY(), clicked.getZ());
        Direction direction = boardFacing(board);
        if (direction.getAxis() == Direction.Axis.Z) {
            if (part == GomokuPart.RIGHT_UP) return 0.5625 <= location.x && location.x <= 0.875 && 0.6875 <= location.z && location.z <= 1;
            if (part == GomokuPart.LEFT_DOWN) return 0.125 <= location.x && location.x <= 0.4375 && 0 <= location.z && location.z <= 0.3125;
        } else {
            if (part == GomokuPart.LEFT_UP) return 0.6875 <= location.x && location.x <= 1 && 0.125 <= location.z && location.z <= 0.4375;
            if (part == GomokuPart.RIGHT_DOWN) return 0 <= location.x && location.x <= 0.3125 && 0.5625 <= location.z && location.z <= 0.875;
        }
        return false;
    }

    @Nullable
    private static BoardRef findBoard(ServerLevel level, BlockPos anyPos) {
        BlockState state = level.getBlockState(anyPos);
        GameType type = gameType(state);
        if (type == null) return null;
        GomokuPart part = part(state);
        BlockPos center = anyPos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        BlockEntity entity = level.getBlockEntity(center);
        if (!(entity instanceof BlockEntityJoy tile)) return null;
        BlockState centerState = level.getBlockState(center);
        return new BoardRef(level, center.immutable(), tile, centerState, type);
    }

    @Nullable
    private static BoardRef boardAtCenter(ServerLevel level, BlockPos center) {
        BlockState state = level.getBlockState(center);
        GameType type = gameType(state);
        if (type == null || !part(state).isCenter()) return null;
        BlockEntity entity = level.getBlockEntity(center);
        return entity instanceof BlockEntityJoy tile
                ? new BoardRef(level, center.immutable(), tile, state, type) : null;
    }

    /** ChunkEvent 专用：只使用事件已经加载好的方块实体，不向 ServerChunkCache 发起任何查询。 */
    @Nullable
    private static BoardRef boardFromLoadedBlockEntity(ServerLevel level, BlockEntity entity) {
        if (!(entity instanceof BlockEntityJoy tile)) return null;
        BlockState state = entity.getBlockState();
        GameType type = gameType(state);
        if (type == null || !part(state).isCenter()) return null;
        return new BoardRef(level, entity.getBlockPos().immutable(), tile, state, type);
    }

    private static boolean isParticipantInActiveBoard(UUID maidId) {
        for (BoardKey key : ACTIVE) {
            ServerLevel level = ServerLifecycleHooks.getCurrentServer() == null
                    ? null : ServerLifecycleHooks.getCurrentServer().getLevel(key.dimension());
            if (level == null || !level.isLoaded(key.pos())) continue;
            BoardRef board = findBoard(level, key.pos());
            CompoundTag root = board == null ? null : root(board, false);
            if (root != null && containsParticipant(root, maidId)) return true;
        }
        return false;
    }

    @Nullable
    private static GameType gameType(BlockState state) {
        if (state.getBlock() instanceof BlockGomoku) return GameType.GOMOKU;
        if (state.getBlock() instanceof BlockCChess) return GameType.CCHESS;
        if (state.getBlock() instanceof BlockWChess) return GameType.WCHESS;
        return null;
    }

    private static GomokuPart part(BlockState state) {
        if (state.getBlock() instanceof BlockGomoku) return state.getValue(BlockGomoku.PART);
        if (state.getBlock() instanceof BlockCChess) return state.getValue(BlockCChess.PART);
        return state.getValue(BlockWChess.PART);
    }

    @Nullable
    private static CompoundTag root(BoardRef board, boolean create) {
        CompoundTag data = board.tile().getPersistentData();
        if (!data.contains(ROOT)) {
            if (!create) return null;
            CompoundTag root = new CompoundTag();
            root.putString(PHASE, Phase.WAITING.name());
            root.put(SIDE_A, emptyParticipant());
            root.put(SIDE_B, emptyParticipant());
            root.put(SPECTATORS, new ListTag());
            data.put(ROOT, root);
        }
        return data.getCompoundOrEmpty(ROOT);
    }

    private static CompoundTag emptyParticipant() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", ParticipantType.EMPTY.name());
        return tag;
    }

    private static CompoundTag participant(CompoundTag root, Side side) {
        return root.getCompoundOrEmpty(side == Side.A ? SIDE_A : SIDE_B);
    }

    private static ParticipantType participantType(CompoundTag root, Side side) {
        try {
            return ParticipantType.valueOf(participant(root, side).getStringOr("Type", ""));
        } catch (IllegalArgumentException ignored) {
            return ParticipantType.EMPTY;
        }
    }

    private static UUID participantUuid(CompoundTag root, Side side) {
        CompoundTag tag = participant(root, side);
        return hasUuid(tag, "Uuid") ? uuid(tag, "Uuid") : Util.NIL_UUID;
    }

    private static boolean isParticipant(CompoundTag root, Side side, ParticipantType type, UUID uuid) {
        return participantType(root, side) == type && participantUuid(root, side).equals(uuid);
    }

    private static boolean containsParticipant(CompoundTag root, UUID uuid) {
        return participantUuid(root, Side.A).equals(uuid) || participantUuid(root, Side.B).equals(uuid);
    }

    @Nullable
    private static Entity participantEntity(BoardRef board, CompoundTag root, Side side) {
        UUID id = participantUuid(root, side);
        return id.equals(Util.NIL_UUID) ? null : board.level().getEntity(id);
    }

    private static void clearParticipant(CompoundTag root, Side side) {
        root.put(side == Side.A ? SIDE_A : SIDE_B, emptyParticipant());
    }

    private static Phase phase(CompoundTag root) {
        try {
            return Phase.valueOf(root.getStringOr(PHASE, ""));
        } catch (IllegalArgumentException ignored) {
            return Phase.WAITING;
        }
    }

    private static void setPhase(BoardRef board, CompoundTag root, Phase phase) {
        root.putString(PHASE, phase.name());
        board.tile().setChanged();
    }

    private static void bumpRevision(BoardRef board, CompoundTag root) {
        root.putInt(REVISION, root.getIntOr(REVISION, 0) + 1);
        board.tile().setChanged();
    }

    private static void importVanillaSeat(BoardRef board, CompoundTag root) {
        removeLegacyPlayerParticipants(board, root);
        if (participantType(root, Side.A) != ParticipantType.EMPTY
                || participantType(root, Side.B) != ParticipantType.EMPTY) return;
        Entity seat = board.level().getEntity(board.tile().getSitId());
        if (!(seat instanceof EntitySit) || !(seat.getFirstPassenger() instanceof EntityMaid maid)) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", ParticipantType.MAID.name());
        tag.store("Uuid", UUIDUtil.CODEC, maid.getUUID());
        tag.store("Seat", UUIDUtil.CODEC, seat.getUUID());
        root.put(SIDE_A, tag);
        ACTIVE.add(board.key());
        board.tile().setChanged();
    }

    /** 旧 2.0.9 曾让玩家骑 EntitySit；升级后立即释放，玩家只作为空侧的棋盘操作者。 */
    private static void removeLegacyPlayerParticipants(BoardRef board, CompoundTag root) {
        boolean changed = false;
        for (Side side : Side.values()) {
            if (participantType(root, side) != ParticipantType.PLAYER) continue;
            releaseParticipantSeat(board, root, side);
            clearParticipant(root, side);
            changed = true;
        }
        if (changed) {
            updatePrimarySitId(board, root);
            board.tile().setChanged();
        }
    }

    private static void updatePrimarySitId(BoardRef board, CompoundTag root) {
        UUID selected = Util.NIL_UUID;
        for (Side side : Side.values()) {
            CompoundTag participant = participant(root, side);
            if (participantType(root, side) == ParticipantType.MAID && hasUuid(participant, "Seat")) {
                selected = uuid(participant, "Seat");
                break;
            }
        }
        board.tile().setSitId(selected);
    }

    private static void swing(BoardRef board, CompoundTag root, Side side) {
        Entity entity = participantEntity(board, root, side);
        if (entity instanceof LivingEntity living) living.swing(InteractionHand.MAIN_HAND);
    }

    private static void playMoveSound(BoardRef board) {
        board.level().playSound(null, board.pos(), InitSounds.GOMOKU.get(), SoundSource.BLOCKS,
                1.0F, 0.8F + board.level().getRandom().nextFloat() * 0.4F);
    }

    private static void broadcast(BoardRef board, Component message) {
        for (ServerPlayer player : board.level().players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(board.pos())) <= 32.0D * 32.0D) {
                player.sendSystemMessage(message);
            }
        }
    }

    @Nullable
    private static CompoundTag spectatorEntry(@Nullable CompoundTag root, UUID maidId) {
        if (root == null) return null;
        for (Tag tag : root.getListOrEmpty(SPECTATORS)) {
            CompoundTag entry = (CompoundTag) tag;
            if (hasUuid(entry, "Maid") && uuid(entry, "Maid").equals(maidId)) return entry.copy();
        }
        return null;
    }

    private static ListTag replaceSpectator(ListTag source, CompoundTag replacement) {
        ListTag result = new ListTag();
        UUID id = uuid(replacement, "Maid");
        for (Tag tag : source) {
            CompoundTag entry = (CompoundTag) tag;
            result.add(hasUuid(entry, "Maid") && uuid(entry, "Maid").equals(id)
                    ? replacement.copy() : entry.copy());
        }
        return result;
    }

    private static boolean hasUuid(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).isPresent();
    }

    private static UUID uuid(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).orElse(Util.NIL_UUID);
    }
}
