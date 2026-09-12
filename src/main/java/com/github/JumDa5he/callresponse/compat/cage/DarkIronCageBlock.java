package com.github.JumDa5he.callresponse.compat.cage;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.UnaryOperator;

/** 1×2 的实体铁笼；几何碰撞只保留边框，中间射线可以直接命中笼内实体。 */
public final class DarkIronCageBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    public static final EnumProperty<CageEnvironment> ENVIRONMENT =
            EnumProperty.create("environment", CageEnvironment.class);

    private static final VoxelShape LOWER_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 0, 0, 2, 16, 2), Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16), Block.box(14, 0, 14, 16, 16, 16));
    private static final VoxelShape UPPER_SHAPE = Shapes.or(
            Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 16, 2), Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16), Block.box(14, 0, 14, 16, 16, 16));
    /** 四面围墙加笼底；实体入笼后依靠真实碰撞活动，不再由方块实体锁死位置。 */
    private static final VoxelShape LOWER_COLLISION = Shapes.or(
            Block.box(0, 0, 0, 16, 1, 16),
            Block.box(0, 0, 0, 16, 16, 1), Block.box(0, 0, 15, 16, 16, 16),
            Block.box(0, 0, 0, 1, 16, 16), Block.box(15, 0, 0, 16, 16, 16));
    /** 顶板下沿位于笼底上方约 2.2 格，允许笼内实体小跳但无法越顶逃脱。 */
    private static final VoxelShape UPPER_COLLISION = Shapes.or(
            Block.box(0, 0, 0, 16, 16, 1), Block.box(0, 0, 15, 16, 16, 16),
            Block.box(0, 0, 0, 1, 16, 16), Block.box(15, 0, 0, 16, 16, 16),
            Block.box(0, 19.2, 0, 16, 20.2, 16));
    private static final VoxelShape LOWER_FLOOR_COLLISION = Block.box(0, 0, 0, 16, 1, 16);
    private static final VoxelShape UPPER_ROOF_COLLISION = Block.box(0, 19.2, 0, 16, 20.2, 16);

    public DarkIronCageBlock() {
        this(Properties.ofFullCopy(Blocks.IRON_BARS).strength(5.0F, 3_600_000.0F)
                .requiresCorrectToolForDrops().noOcclusion());
    }

    private DarkIronCageBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OCCUPIED, false)
                .setValue(ENVIRONMENT, CageEnvironment.EMPTY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DarkIronCageBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new DarkIronCageBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OCCUPIED, ENVIRONMENT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        // 大型囚犯的碰撞箱无法物理塞进不足一格宽的内部空间。只对当前囚犯本人
        // 放开侧壁，保留底/顶，再由笼子实体限制其中心；其他实体仍会被四壁挡住。
        if (context instanceof EntityCollisionContext entityContext) {
            DarkIronCageBlockEntity cage = cageEntity(level, pos, state);
            if (cage != null && cage.usesLogicalContainment(entityContext.getEntity())) {
                return state.getValue(HALF) == DoubleBlockHalf.LOWER
                        ? LOWER_FLOOR_COLLISION : Shapes.empty();
            }
        }
        // 外侧实体无法穿过完整的 1×2 围墙；空笼仍会在触碰围墙时主动把实体收容到中心。
        // 选取形状继续使用稀疏栏杆模型，因此栏杆缝隙之间仍可攻击笼内实体。
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_COLLISION : UPPER_COLLISION;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 玩家只允许主动潜行右键入笼，不再因靠近或碰撞被自动吸入。
        if (level.isClientSide || !(entity instanceof LivingEntity living)
                || living instanceof Player || living.isSpectator()) {
            return;
        }
        DarkIronCageBlockEntity cage = cageEntity(level, pos, state);
        if (cage != null && !cage.isOccupied() && cage.capture(living, null, CageOrigin.NORMAL)) {
            playClose(level, lowerPos(pos, state));
        }
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                           BlockPos pos, Player player, InteractionHand hand,
                                           BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        DarkIronCageBlockEntity cage = cageEntity(level, pos, state);
        if (cage == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 玩家潜行右键空笼时主动进入；捕获后由服务端持续约束真实位置。
        if (player.isShiftKeyDown() && !cage.isOccupied()) {
            if (cage.capture(player, player, CageOrigin.NORMAL)) {
                playClose(level, lowerPos(pos, state));
                return ItemInteractionResult.CONSUME;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(Items.MILK_BUCKET)) {
            setEnvironment(level, lowerPos(pos, state), CageEnvironment.EMPTY);
            consumeContainerItem(player, hand, stack, new ItemStack(Items.BUCKET));
            return ItemInteractionResult.CONSUME;
        }

        CageEnvironment selected = bucketEnvironmentFor(stack);
        if (selected != null) {
            setEnvironment(level, lowerPos(pos, state), selected);
            consumeContainerItem(player, hand, stack, new ItemStack(Items.BUCKET));
            return ItemInteractionResult.CONSUME;
        }
        selected = simpleEnvironmentFor(stack);
        if (selected != null) {
            setEnvironment(level, lowerPos(pos, state), selected);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.CONSUME;
        }
        if (stack.is(Items.FLINT_AND_STEEL)) {
            setEnvironment(level, lowerPos(pos, state), CageEnvironment.FIRE);
            stack.hurtAndBreak(1, player,
                    hand == InteractionHand.MAIN_HAND
                            ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                            : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            return ItemInteractionResult.CONSUME;
        }
        if (stack.is(Items.BUCKET) && state.getValue(ENVIRONMENT) != CageEnvironment.EMPTY
                && state.getValue(ENVIRONMENT) != CageEnvironment.FIRE) {
            ItemStack filled = bucketFor(state.getValue(ENVIRONMENT));
            setEnvironment(level, lowerPos(pos, state), CageEnvironment.EMPTY);
            consumeContainerItem(player, hand, stack, filled);
            return ItemInteractionResult.CONSUME;
        }
        if (stack.is(Items.LEAD)) {
            if (cage.isOccupied()) {
                LivingEntity occupant = cage.occupant();
                if (occupant instanceof Mob) {
                    cage.release(player);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    playOpen(level, lowerPos(pos, state));
                    return ItemInteractionResult.CONSUME;
                }
            } else {
                Mob tethered = findTetheredMob(level, player, lowerPos(pos, state));
                if (tethered != null && cage.capture(tethered, player, CageOrigin.NORMAL)) {
                    playClose(level, lowerPos(pos, state));
                    return ItemInteractionResult.CONSUME;
                }
            }
        }
        if (stack.getItem() instanceof PickaxeItem && cage.occupant() instanceof Player occupant
                && occupant.getUUID().equals(player.getUUID())) {
            cage.release(null);
            playOpen(level, lowerPos(pos, state));
            return ItemInteractionResult.CONSUME;
        }
        if (stack.isEmpty()) {
            player.displayClientMessage(cage.isOccupied() ? cage.information()
                    : net.minecraft.network.chat.Component.translatable("message.callresponse.cage.info.empty"), false);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            BlockPos lower = lowerPos(pos, state);
            if (level.getBlockEntity(lower) instanceof DarkIronCageBlockEntity cage) {
                cage.release(null);
            }
            BlockPos other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this) && otherState.getValue(HALF) != state.getValue(HALF)) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return createTickerHelper(type, ModBlocks.DARK_IRON_CAGE_ENTITY.get(),
                (world, pos, blockState, cage) -> DarkIronCageBlockEntity.serverTick(
                        (ServerLevel) world, pos, blockState, cage));
    }

    public static void placeComplete(ServerLevel level, BlockPos lower, Direction facing, CageOrigin origin) {
        BlockState base = ModBlocks.DARK_IRON_CAGE.get().defaultBlockState()
                .setValue(FACING, facing).setValue(HALF, DoubleBlockHalf.LOWER);
        level.setBlock(lower, base, Block.UPDATE_ALL);
        level.setBlock(lower.above(), base.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (level.getBlockEntity(lower) instanceof DarkIronCageBlockEntity cage) cage.setOrigin(origin);
    }

    /** 手持笼子抓取实体与结构生成共用的完整 1×2 空间检查。 */
    public static boolean canPlaceComplete(Level level, BlockPos lower) {
        return lower.getY() < level.getMaxBuildHeight() - 1
                && level.getBlockState(lower).canBeReplaced()
                && level.getBlockState(lower.above()).canBeReplaced()
                && level.getBlockState(lower.below()).isFaceSturdy(level, lower.below(), Direction.UP);
    }

    public static void updateBothHalves(Level level, BlockPos lower, UnaryOperator<BlockState> update) {
        for (BlockPos target : new BlockPos[]{lower, lower.above()}) {
            BlockState state = level.getBlockState(target);
            if (state.is(ModBlocks.DARK_IRON_CAGE.get())) {
                level.setBlock(target, update.apply(state), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void setEnvironment(Level level, BlockPos lower, CageEnvironment environment) {
        if (level.getBlockEntity(lower) instanceof DarkIronCageBlockEntity cage) {
            cage.onEnvironmentChanged(environment);
        }
        updateBothHalves(level, lower, state -> state.setValue(ENVIRONMENT, environment));
    }

    private static @Nullable DarkIronCageBlockEntity cageEntity(BlockGetter level, BlockPos pos, BlockState state) {
        BlockEntity entity = level.getBlockEntity(lowerPos(pos, state));
        return entity instanceof DarkIronCageBlockEntity cage ? cage : null;
    }

    private static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    private static @Nullable CageEnvironment bucketEnvironmentFor(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return CageEnvironment.WATER;
        if (stack.is(Items.LAVA_BUCKET)) return CageEnvironment.LAVA;
        if (stack.is(Items.POWDER_SNOW_BUCKET)) return CageEnvironment.POWDER_SNOW;
        return null;
    }

    private static @Nullable CageEnvironment simpleEnvironmentFor(ItemStack stack) {
        if (stack.is(Items.CACTUS)) return CageEnvironment.CACTUS;
        if (stack.is(Items.LIGHTNING_ROD)) return CageEnvironment.LIGHTNING;
        if (stack.is(Items.GOLDEN_APPLE)) return CageEnvironment.GOLDEN_APPLE;
        return null;
    }

    private static ItemStack bucketFor(CageEnvironment environment) {
        return switch (environment) {
            case WATER -> new ItemStack(Items.WATER_BUCKET);
            case LAVA -> new ItemStack(Items.LAVA_BUCKET);
            case POWDER_SNOW -> new ItemStack(Items.POWDER_SNOW_BUCKET);
            default -> ItemStack.EMPTY;
        };
    }

    private static void consumeContainerItem(Player player, InteractionHand hand, ItemStack held, ItemStack result) {
        if (player.getAbilities().instabuild) return;
        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, result);
        } else if (!result.isEmpty() && !player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }

    private static @Nullable Mob findTetheredMob(Level level, Player player, BlockPos pos) {
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(10.0D),
                        mob -> mob.isAlive() && mob.getLeashHolder() == player)
                .stream().min(Comparator.comparingDouble(mob -> mob.distanceToSqr(pos.getCenter()))).orElse(null);
    }

    private static void playClose(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void playOpen(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
