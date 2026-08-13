package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.UUID;

/** Persistent and client-synchronised identity for maids currently offered by a wandering trader. */
public final class TradingMaidData {
    private static final String PREFIX = CallResponseMod.MOD_ID + ":trading_maid_";
    private static final String ACTIVE = PREFIX + "active";
    private static final String TRADER = PREFIX + "trader";
    private static final String PRICE = PREFIX + "price";
    private static final String PURCHASE_AUTHORIZED = PREFIX + "purchase_authorized";

    private TradingMaidData() {
    }

    public static void initialize(EntityMaid maid, UUID traderId, int price) {
        CompoundTag tag = maid.getPersistentData();
        tag.putBoolean(ACTIVE, true);
        tag.putUUID(TRADER, traderId);
        tag.putInt(PRICE, Math.max(1, price));
        maid.setData(InitAttachTypes.SYNCED_TRADING, true);
    }

    public static boolean isTrading(EntityMaid maid) {
        return maid.getData(InitAttachTypes.SYNCED_TRADING) || maid.getPersistentData().getBoolean(ACTIVE);
    }

    public static Optional<UUID> trader(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        return tag.hasUUID(TRADER) ? Optional.of(tag.getUUID(TRADER)) : Optional.empty();
    }

    public static int price(EntityMaid maid) {
        return Math.max(1, maid.getPersistentData().getInt(PRICE));
    }

    public static boolean purchaseAuthorized(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(PURCHASE_AUTHORIZED);
    }

    public static void setPurchaseAuthorized(EntityMaid maid, boolean value) {
        if (value) {
            maid.getPersistentData().putBoolean(PURCHASE_AUTHORIZED, true);
        } else {
            maid.getPersistentData().remove(PURCHASE_AUTHORIZED);
        }
    }

    public static void clear(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        tag.remove(ACTIVE);
        tag.remove(TRADER);
        tag.remove(PRICE);
        tag.remove(PURCHASE_AUTHORIZED);
        maid.setData(InitAttachTypes.SYNCED_TRADING, false);
    }
}
