package com.github.JumDa5he.callresponse.compat.wandering;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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
        CompoundTag tag = maid.getPersistentData();
        tag.putBoolean(SPECIAL, true);
        maid.setData(InitAttachTypes.SYNCED_WANDERING_SPECIAL, true);
        tag.putUUID(TARGET, target);
        tag.putInt(ARRIVALS, 0);
        setState(maid, WanderingMaidState.APPROACHING, gameTime);
    }

    public static boolean isSpecial(EntityMaid maid) {
        return maid.getData(InitAttachTypes.SYNCED_WANDERING_SPECIAL)
                || maid.getPersistentData().getBoolean(SPECIAL);
    }

    public static boolean mayAccept(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(ACCEPT_AUTHORIZED);
    }

    public static void setAcceptAuthorized(EntityMaid maid, boolean authorized) {
        if (authorized) {
            maid.getPersistentData().putBoolean(ACCEPT_AUTHORIZED, true);
        } else {
            maid.getPersistentData().remove(ACCEPT_AUTHORIZED);
        }
    }

    public static Optional<UUID> target(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        return tag.hasUUID(TARGET) ? Optional.of(tag.getUUID(TARGET)) : Optional.empty();
    }

    public static WanderingMaidState state(EntityMaid maid) {
        return WanderingMaidState.parse(maid.getPersistentData().getString(STATE));
    }

    public static void setState(EntityMaid maid, WanderingMaidState state, long gameTime) {
        CompoundTag tag = maid.getPersistentData();
        tag.putString(STATE, state.name());
        tag.putLong(STATE_SINCE, gameTime);
        tag.putLong(LAST_PROGRESS, gameTime);
        tag.putDouble(CLOSEST_DISTANCE, Double.MAX_VALUE);
    }

    public static int arrivals(EntityMaid maid) {
        return maid.getPersistentData().getInt(ARRIVALS);
    }

    public static int incrementArrivals(EntityMaid maid) {
        int value = arrivals(maid) + 1;
        maid.getPersistentData().putInt(ARRIVALS, value);
        return value;
    }

    public static long stateSince(EntityMaid maid) {
        return maid.getPersistentData().getLong(STATE_SINCE);
    }

    public static long lastProgress(EntityMaid maid) {
        return maid.getPersistentData().getLong(LAST_PROGRESS);
    }

    public static double closestDistance(EntityMaid maid) {
        return maid.getPersistentData().getDouble(CLOSEST_DISTANCE);
    }

    public static void recordProgress(EntityMaid maid, double distanceSqr, long gameTime) {
        CompoundTag tag = maid.getPersistentData();
        tag.putDouble(CLOSEST_DISTANCE, distanceSqr);
        tag.putLong(LAST_PROGRESS, gameTime);
    }

    public static void clear(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
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
