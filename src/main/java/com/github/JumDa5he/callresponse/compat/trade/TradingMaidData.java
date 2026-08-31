package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.UUIDUtil;
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
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        tag.putBoolean(ACTIVE, true);
        tag.store(TRADER, UUIDUtil.CODEC, traderId);
        tag.putInt(PRICE, Math.max(1, price));
        maid.setData(InitAttachTypes.SYNCED_TRADING, true);
    }

    public static boolean isTrading(EntityMaid maid) {
        return maid.getData(InitAttachTypes.SYNCED_TRADING) || InitAttachTypes.persistentData(maid).getBoolean(ACTIVE).orElse(false);
    }

    public static Optional<UUID> trader(EntityMaid maid) {
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        return tag.read(TRADER, UUIDUtil.CODEC);
    }

    public static int price(EntityMaid maid) {
        return Math.max(1, InitAttachTypes.persistentData(maid).getInt(PRICE).orElse(0));
    }

    public static boolean purchaseAuthorized(EntityMaid maid) {
        return InitAttachTypes.persistentData(maid).getBoolean(PURCHASE_AUTHORIZED).orElse(false);
    }

    public static void setPurchaseAuthorized(EntityMaid maid, boolean value) {
        if (value) {
            InitAttachTypes.persistentData(maid).putBoolean(PURCHASE_AUTHORIZED, true);
        } else {
            InitAttachTypes.persistentData(maid).remove(PURCHASE_AUTHORIZED);
        }
    }

    public static void clear(EntityMaid maid) {
        CompoundTag tag = InitAttachTypes.persistentData(maid);
        tag.remove(ACTIVE);
        tag.remove(TRADER);
        tag.remove(PRICE);
        tag.remove(PURCHASE_AUTHORIZED);
        maid.setData(InitAttachTypes.SYNCED_TRADING, false);
    }
}
