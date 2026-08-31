package com.github.JumDa5he.callresponse.compat.wandering;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.UUID;

public final class WanderingMaidData {
    private static final String PREFIX = CallResponseMod.MOD_ID + ":wandering_maid_";
    private static final String SPECIAL = PREFIX + "special";
    private static final String STATE = PREFIX + "state";
    private static final String TARGET = PREFIX + "target";
    private static final String ARRIVALS = PREFIX + "arrivals";
    private static final String STATE_SINCE = PREFIX + "state_since";
    private static final String LAST_PROGRESS = PREFIX + "last_progress";
    private static final String CLOSEST_DISTANCE = PREFIX + "closest_distance";
    private static final String ACCEPT_AUTHORIZED = PREFIX + "accept_authorized";

    private WanderingMaidData() {
    }

    public static void initialize(EntityMaid maid, UUID target, long gameTime) {
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        tag.putBoolean(SPECIAL, true);
        maid.setData(InitAttachTypes.SYNCED_WANDERING_SPECIAL, true);
        tag.store(TARGET, UUIDUtil.CODEC, target);
        tag.putInt(ARRIVALS, 0);
        setState(maid, WanderingMaidState.APPROACHING, gameTime);
    }

    public static boolean isSpecial(EntityMaid maid) {
        return maid.getData(InitAttachTypes.SYNCED_WANDERING_SPECIAL)
                || InitAttachTypes.persistentData(maid).getBoolean(SPECIAL).orElse(false);
    }

    public static boolean mayAccept(EntityMaid maid) {
        return InitAttachTypes.persistentData(maid).getBoolean(ACCEPT_AUTHORIZED).orElse(false);
    }

    public static void setAcceptAuthorized(EntityMaid maid, boolean authorized) {
        if (authorized) {
            InitAttachTypes.persistentData(maid).putBoolean(ACCEPT_AUTHORIZED, true);
        } else {
            InitAttachTypes.persistentData(maid).remove(ACCEPT_AUTHORIZED);
        }
    }

    public static Optional<UUID> target(EntityMaid maid) {
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        return tag.read(TARGET, UUIDUtil.CODEC);
    }

    public static WanderingMaidState state(EntityMaid maid) {
        return WanderingMaidState.parse(InitAttachTypes.persistentData(maid).getString(STATE).orElse(""));
    }

    public static void setState(EntityMaid maid, WanderingMaidState state, long gameTime) {
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        tag.putString(STATE, state.name());
        tag.putLong(STATE_SINCE, gameTime);
        tag.putLong(LAST_PROGRESS, gameTime);
        tag.putDouble(CLOSEST_DISTANCE, Double.MAX_VALUE);
    }

    public static int arrivals(EntityMaid maid) {
        return InitAttachTypes.persistentData(maid).getInt(ARRIVALS).orElse(0);
    }

    public static int incrementArrivals(EntityMaid maid) {
        int value = arrivals(maid) + 1;
        InitAttachTypes.persistentData(maid).putInt(ARRIVALS, value);
        return value;
    }

    public static long stateSince(EntityMaid maid) {
        return InitAttachTypes.persistentData(maid).getLong(STATE_SINCE).orElse(0L);
    }

    public static long lastProgress(EntityMaid maid) {
        return InitAttachTypes.persistentData(maid).getLong(LAST_PROGRESS).orElse(0L);
    }

    public static double closestDistance(EntityMaid maid) {
        return InitAttachTypes.persistentData(maid).getDouble(CLOSEST_DISTANCE).orElse(0.0);
    }

    public static void recordProgress(EntityMaid maid, double distanceSqr, long gameTime) {
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        tag.putDouble(CLOSEST_DISTANCE, distanceSqr);
        tag.putLong(LAST_PROGRESS, gameTime);
    }

    public static void clear(EntityMaid maid) {
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        maid.setData(InitAttachTypes.SYNCED_WANDERING_SPECIAL, false);
        tag.remove(SPECIAL);
        tag.remove(STATE);
        tag.remove(TARGET);
        tag.remove(ARRIVALS);
        tag.remove(STATE_SINCE);
        tag.remove(LAST_PROGRESS);
        tag.remove(CLOSEST_DISTANCE);
        tag.remove(ACCEPT_AUTHORIZED);
    }
}
