package com.github.JumDa5he.callresponse.compat.sign.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 只存在于客户端的“假告示牌”方块实体。
 * <p>它的唯一用途是把女仆身上的牌子数据喂给原版的 {@code SignRenderer} 和
 * {@code AbstractSignEditScreen}，从而直接复用原版的渲染、排版与输入逻辑。
 * <p>它不会进入世界，所以这里把原版需要真实 {@code level} 的方法就地短路，
 * 避免空指针，也避免往区块里发无意义的方块更新。
 */
public class MaidSignBlockEntity extends SignBlockEntity {
    /**
     * 假方块用的底板状态。用朝南的墙牌，这样原版 {@code translateSign} 里的 Y 旋转是 0，
     * 牌子在本地空间里的朝向完全由渲染层的常量控制。
     */
    private static final BlockState SIGN_STATE =
            Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH);

    private static final Map<WoodType, BlockState> WALL_SIGN_STATES = new ConcurrentHashMap<>();
    private BlockState state;
    private SignText text;

    public MaidSignBlockEntity(SignText text) {
        this(SIGN_STATE, text);
    }

    public MaidSignBlockEntity(Item signItem, SignText text) {
        this(wallSignState(signItem), text);
    }

    private MaidSignBlockEntity(BlockState state, SignText text) {
        super(BlockPos.ZERO, state);
        this.state = state;
        this.text = text;
    }

    public void setSignState(BlockState state) {
        this.state = state;
    }

    public static BlockState wallSignState(Item signItem) {
        WoodType wood = signItem instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof SignBlock signBlock ? signBlock.type() : WoodType.OAK;
        return WALL_SIGN_STATES.computeIfAbsent(wood, MaidSignBlockEntity::findWallSignState);
    }

    private static BlockState findWallSignState(WoodType wood) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof WallSignBlock wallSign && wallSign.type() == wood) {
                return wallSign.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH);
            }
        }
        return SIGN_STATE;
    }

    public void setSignText(SignText text) {
        this.text = text;
    }

    @Override
    public BlockState getBlockState() {
        return this.state;
    }

    @Override
    public SignText getText(boolean isFrontText) {
        return this.text;
    }

    @Override
    public SignText getFrontText() {
        return this.text;
    }

    @Override
    public SignText getBackText() {
        return this.text;
    }

    @Override
    public boolean setText(SignText text, boolean isFrontText) {
        this.text = text;
        return true;
    }

    @Override
    public boolean playerIsTooFarAwayToEdit(UUID playerId) {
        return false;
    }
}
