package com.github.JumDa5he.callresponse.compat.npc;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
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
    private static final String REVIVE_DEFAULTS_RESET = "ReviveDefaultsReset";
    private static final long REVIVE_EVENT_DELAY_TICKS = 60L;

    private MaidReviveEventData() {}

    public static void recordDeath(EntityMaid maid, boolean ownerKilled, long gameTime) {
        CompoundTag data = data(maid).copy();
        data.putString(LAST_DEATH_TYPE, ownerKilled ? DeathType.OWNER_KILLED.name() : DeathType.OTHER.name());
        data.putBoolean(REVIVE_EVENT_PENDING, true);
        data.putLong(LAST_DEATH_GAME_TIME, gameTime);
        data.remove(REVIVE_EVENT_READY_TIME);
        data.remove(REVIVE_DEFAULTS_RESET);
        maid.getPersistentData().put(ROOT, data);
    }

    public static void writeAdditionalSaveData(EntityMaid maid, CompoundTag entityTag) {
        CompoundTag data = data(maid);
        if (!data.isEmpty()) entityTag.put(ROOT, data.copy());
    }

    public static void readAdditionalSaveData(EntityMaid maid, CompoundTag entityTag) {
        if (!entityTag.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag loaded = entityTag.getCompound(ROOT).copy();
        if (loaded.getBoolean(REVIVE_EVENT_PENDING) && !loaded.contains(REVIVE_EVENT_READY_TIME, Tag.TAG_LONG)) {
            loaded.putLong(REVIVE_EVENT_READY_TIME, maid.level().getGameTime() + REVIVE_EVENT_DELAY_TICKS);
        }
        maid.getPersistentData().put(ROOT, loaded);
    }

    public static Optional<String> readyEventId(EntityMaid maid, long gameTime) {
        CompoundTag data = data(maid);
        if (!data.getBoolean(REVIVE_EVENT_PENDING)
                || !data.contains(REVIVE_EVENT_READY_TIME, Tag.TAG_LONG)
                || gameTime < data.getLong(REVIVE_EVENT_READY_TIME)) return Optional.empty();
        return switch (data.getString(LAST_DEATH_TYPE)) {
            case "OWNER_KILLED" -> Optional.of("revive_owner_killed");
            case "OTHER" -> Optional.of("revive_other");
            default -> Optional.empty();
        };
    }

    /** 只处理继承了死亡 NBT 且已经建立复活延迟的新实体，并保证每次复活只重置一次。 */
    public static void resetRevivedDefaultsOnce(EntityMaid maid) {
        CompoundTag data = data(maid).copy();
        if (!data.getBoolean(REVIVE_EVENT_PENDING)
                || !data.contains(REVIVE_EVENT_READY_TIME, Tag.TAG_LONG)
                || data.getBoolean(REVIVE_DEFAULTS_RESET)) return;

        EmotionData.resetToDefault(maid);
        HungerData.resetToDefault(maid);
        data.putBoolean(REVIVE_DEFAULTS_RESET, true);
        maid.getPersistentData().put(ROOT, data);
    }

    public static void consume(EntityMaid maid) {
        CompoundTag data = data(maid).copy();
        data.remove(LAST_DEATH_TYPE);
        data.remove(REVIVE_EVENT_PENDING);
        data.remove(LAST_DEATH_GAME_TIME);
        data.remove(REVIVE_EVENT_READY_TIME);
        data.remove(REVIVE_DEFAULTS_RESET);
        if (data.isEmpty()) maid.getPersistentData().remove(ROOT);
        else maid.getPersistentData().put(ROOT, data);
    }

    private static CompoundTag data(EntityMaid maid) {
        return maid.getPersistentData().getCompound(ROOT);
    }

    private enum DeathType { OWNER_KILLED, OTHER }
}
