package com.github.JumDa5he.callresponse.compat.npc;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/** 只保存复活事件所需的附加标记，不参与女仆实体本身的创建与恢复。 */
public final class MaidReviveEventData {
    private static final String ROOT = "CallResponseData";
    private static final String LAST_DEATH_TYPE = "LastDeathType";
    private static final String REVIVE_EVENT_PENDING = "ReviveEventPending";
    private static final String LAST_DEATH_GAME_TIME = "LastDeathGameTime";
    private static final String REVIVE_EVENT_READY_TIME = "ReviveEventReadyGameTime";
    private static final long REVIVE_EVENT_DELAY_TICKS = 60L;

    private MaidReviveEventData() {
    }

    public static void recordDeath(EntityMaid maid, boolean ownerKilled, long gameTime) {
        CompoundTag data = data(maid).copy();
        data.putString(LAST_DEATH_TYPE, ownerKilled ? DeathType.OWNER_KILLED.name() : DeathType.OTHER.name());
        data.putBoolean(REVIVE_EVENT_PENDING, true);
        data.putLong(LAST_DEATH_GAME_TIME, gameTime);
        data.remove(REVIVE_EVENT_READY_TIME);
        maid.getPersistentData().put(ROOT, data);
    }

    /** 显式写入女仆附加 NBT，确保 TLM 胶片仅调用 readAdditionalSaveData 时也能继承。 */
    public static void writeAdditionalSaveData(EntityMaid maid, CompoundTag entityTag) {
        CompoundTag data = data(maid);
        if (!data.isEmpty()) entityTag.put(ROOT, data.copy());
    }

    public static void readAdditionalSaveData(EntityMaid maid, CompoundTag entityTag) {
        if (!entityTag.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag loaded = entityTag.getCompound(ROOT).copy();
        if (loaded.getBoolean(REVIVE_EVENT_PENDING)
                && !loaded.contains(REVIVE_EVENT_READY_TIME, Tag.TAG_LONG)) {
            loaded.putLong(REVIVE_EVENT_READY_TIME,
                    maid.level().getGameTime() + REVIVE_EVENT_DELAY_TICKS);
        }
        maid.getPersistentData().put(ROOT, loaded);
    }

    public static Optional<String> readyEventId(EntityMaid maid, long gameTime) {
        CompoundTag data = data(maid);
        if (!data.getBoolean(REVIVE_EVENT_PENDING)) return Optional.empty();
        if (!data.contains(REVIVE_EVENT_READY_TIME, Tag.TAG_LONG)) return Optional.empty();
        if (gameTime < data.getLong(REVIVE_EVENT_READY_TIME)) return Optional.empty();
        return switch (data.getString(LAST_DEATH_TYPE)) {
            case "OWNER_KILLED" -> Optional.of("revive_owner_killed");
            case "OTHER" -> Optional.of("revive_other");
            default -> Optional.empty();
        };
    }

    /** 事件进入 current 或 pending 后立刻清掉标记，区块重载不会再次生成。 */
    public static void consume(EntityMaid maid) {
        CompoundTag data = data(maid).copy();
        data.remove(LAST_DEATH_TYPE);
        data.remove(REVIVE_EVENT_PENDING);
        data.remove(LAST_DEATH_GAME_TIME);
        data.remove(REVIVE_EVENT_READY_TIME);
        if (data.isEmpty()) maid.getPersistentData().remove(ROOT);
        else maid.getPersistentData().put(ROOT, data);
    }

    private static CompoundTag data(EntityMaid maid) {
        return maid.getPersistentData().getCompound(ROOT);
    }

    private enum DeathType {
        OWNER_KILLED,
        OTHER
    }
}
