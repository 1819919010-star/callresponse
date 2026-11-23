package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.cage.DarkIronCageBlockEntity;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.MaidBackpackHandler;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;

/** Builds a new ordinary maid without importing enemy NBT or consuming the source's real items. */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class WineFoxRehabilitation {
    private static final Map<EntityMaid, Integer> LAST_FAILURE_TICK = new WeakHashMap<>();
    private static final String PENDING_NOTICE = "CallResponseRehabilitationNoticeOwner";

    /** The cage timer and the operator command share the same all-or-retain replacement path. */
    @Nullable
    public static EntityMaid convert(EntityMaid prisoner, UUID owner, @Nullable DarkIronCageBlockEntity cage) {
        if (!(prisoner.level() instanceof ServerLevel level) || !prisoner.isAlive() || prisoner.isRemoved()
                || !WineFoxFaction.isRevenge(prisoner) || !WineFoxSurrender.isSurrendered(prisoner)
                || owner == null || cage != null && cage.occupant() != prisoner) return null;
        EntityMaid replacement = prepare(prisoner, level, owner);
        if (replacement == null) return null;
        boolean added;
        try {
            added = level.addFreshEntity(replacement) && replacement.isAlive() && !replacement.isRemoved()
                    && replacement.isTame() && owner.equals(replacement.getOwnerUUID());
        } catch (RuntimeException failure) {
            warnFailure(prisoner, failure);
            added = false;
        }
        if (!added || !prisoner.isAlive() || prisoner.isRemoved()
                || cage != null && !cage.replaceRehabilitatedOccupant(prisoner, replacement)) {
            replacement.discard();
            return null;
        }
        prisoner.discard();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) notifyOwner(player, replacement);
        else replacement.getPersistentData().putUUID(PENDING_NOTICE, owner);
        return replacement;
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof EntityMaid maid)
                || !player.getUUID().equals(maid.getOwnerUUID())) return;
        CompoundTag data = maid.getPersistentData();
        if (!data.hasUUID(PENDING_NOTICE)) return;
        UUID intendedOwner = data.getUUID(PENDING_NOTICE);
        data.remove(PENDING_NOTICE);
        if (player.getUUID().equals(intendedOwner)) notifyOwner(player, maid);
    }

    private static void notifyOwner(ServerPlayer player, EntityMaid maid) {
        player.sendSystemMessage(Component.translatable("message.callresponse.winefox.rehabilitated", modelName(maid)));
    }

    static Component modelName(EntityMaid maid) {
        String modelId = maid.getModelId();
        if (maid.isYsmModel()) {
            Component name = maid.getYsmModelName();
            if (!Component.empty().equals(name)) return name;
            if (!maid.getYsmModelId().isBlank()) return Component.literal(maid.getYsmModelId());
        }
        Component name = ServerCustomPackLoader.SERVER_MAID_MODELS.getInfo(modelId)
                .map(info -> info.getName()).filter(raw -> !raw.isBlank())
                .map(ParseI18n::parse).orElseGet(() -> Component.literal(modelId));
        if (name.getContents() instanceof TranslatableContents key)
            return Component.translatableWithFallback(key.getKey(), modelId);
        return name;
    }

    private static EntityMaid prepare(EntityMaid prisoner, ServerLevel level, UUID owner) {
        try {
            EntityMaid maid = EntityMaid.TYPE.create(level);
            if (maid == null) return null;
            maid.moveTo(prisoner.getX(), prisoner.getY(), prisoner.getZ(), prisoner.getYRot(), prisoner.getXRot());
            maid.setTame(true);
            maid.setOwnerUUID(owner); // Native owner fields work with an offline player UUID.
            maid.setPersistenceRequired();
            String model = prisoner.getModelId();
            if (ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet().contains(model)) maid.setModelId(model);
            if (prisoner.hasCustomName()) maid.setCustomName(prisoner.getCustomName().copy());
            maid.setCustomNameVisible(prisoner.isCustomNameVisible());
            maid.setMaidBackpackType(prisoner.getMaidBackpackType());
            for (EquipmentSlot slot : EquipmentSlot.values()) maid.setItemSlot(slot, prisoner.getItemBySlot(slot).copy());
            copyInventory(prisoner.getMaidInv(), maid.getMaidInv());
            copyInventory(prisoner.getMaidBauble(), maid.getMaidBauble());
            copyInventory(prisoner.getHideInv(), maid.getHideInv());
            copyInventory(prisoner.getTaskInv(), maid.getTaskInv());
            if (prisoner.getBackpackData() != null && maid.getBackpackData() != null) {
                CompoundTag backpack = new CompoundTag();
                prisoner.getBackpackData().save(backpack, prisoner);
                maid.getBackpackData().load(backpack.copy(), maid);
            }
            maid.setBackpackShowItem(maid.getMaidInv().getStackInSlot(MaidBackpackHandler.BACKPACK_ITEM_SLOT));
            maid.setFavorability(maid.getFavorabilityManager().getPointByLevel(1));
            maid.setHealth(maid.getMaxHealth());
            // Initialize native health first; the new Trust maximum is not a free heal.
            EmotionData.set(maid, owner, 30, 70);
            maid.refreshBrain(level);
            if (!maid.isTame() || !owner.equals(maid.getOwnerUUID()) || WineFoxFaction.isRevenge(maid)
                    || WineFoxSurrender.isSurrendered(maid)) throw new IllegalStateException("Native maid ownership initialization rejected");
            return maid;
        } catch (RuntimeException failure) {
            warnFailure(prisoner, failure);
            return null;
        }
    }

    private static void warnFailure(EntityMaid prisoner, RuntimeException failure) {
        int last = LAST_FAILURE_TICK.getOrDefault(prisoner, Integer.MIN_VALUE / 2);
        if (prisoner.tickCount - last >= 200) {
            LAST_FAILURE_TICK.put(prisoner, prisoner.tickCount);
            CallResponseMod.LOGGER.warn("Rehabilitation retained original maid {} after conversion failure", prisoner.getUUID(), failure);
        }
    }

    private static void copyInventory(IItemHandler from, IItemHandlerModifiable to) {
        for (int i = 0; i < from.getSlots(); i++) {
            if (i < to.getSlots()) to.setStackInSlot(i, from.getStackInSlot(i).copy());
            else if (!from.getStackInSlot(i).isEmpty())
                throw new IllegalStateException("Incompatible backpack capacity; original prisoner retained");
        }
    }
    private WineFoxRehabilitation() {}
}
