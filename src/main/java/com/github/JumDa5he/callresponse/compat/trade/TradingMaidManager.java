package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidSavedData;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import com.github.JumDa5he.callresponse.mixin.accessor.EntityMaidTameInvoker;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemMaidBed;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Wandering-trader maid market. The GUI is only a view; every action is validated again here. */
public final class TradingMaidManager {
    private static final String DEFAULT_MODEL = "geckolib_winefox";
    private static final String TRADER_INITIALIZED = "callresponse:maid_market_initialized";
    private static final String ITEM_OFFERS_INITIALIZED = "callresponse:item_offers_initialized";
    private static final String SECOND_WAVE_SPAWNED = "callresponse:maid_market_second_wave_spawned";
    private static final String PURCHASE_MOVING = "callresponse:trading_maid_purchase_moving";
    private static final int MIN_PRICE = 60;
    private static final int MAX_PRICE = 128;
    private static final int TRADER_SCAN_RADIUS = 96;
    private static final int TRADE_RADIUS = 12;
    private static final int SELL_RADIUS = 32;
    private static final long PURCHASE_MOVE_TIMEOUT = 20L * 60L;

    private int tickCounter;

    public static boolean blocksNormalInteraction(EntityMaid maid) {
        return TradingMaidData.isTrading(maid);
    }

    private static boolean enabled() {
        return EmotionPassiveConfig.WANDERING_TRADER_MAID_TRADE_ENABLED.get();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (enabled()) {
                initialiseNearbyTraders(level);
                tickPurchasedMaids(level);
                enforceTradingMaids(level);
            } else {
                cleanDisabledMarket(level);
            }
        }
    }

    private static void initialiseNearbyTraders(ServerLevel level) {
        Set<UUID> handled = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB area = player.getBoundingBox().inflate(TRADER_SCAN_RADIUS);
            for (WanderingTrader trader : level.getEntitiesOfClass(WanderingTrader.class, area,
                    candidate -> candidate.isAlive() && handled.add(candidate.getUUID()))) {
                initialiseTrader(level, trader, player);
            }
        }
    }

    private static void initialiseTrader(ServerLevel level, WanderingTrader trader, ServerPlayer player) {
        ensureCallResponseOffers(trader);
        CompoundTag traderData = trader.getPersistentData();
        if (traderData.getBoolean(TRADER_INITIALIZED)) {
            return;
        }
        traderData.putBoolean(TRADER_INITIALIZED, true);
        int count = 1 + level.getRandom().nextInt(3);
        for (int i = 0; i < count; i++) {
            spawnStockMaid(level, trader, player);
        }
    }

    private static void ensureCallResponseOffers(WanderingTrader trader) {
        if (trader.getPersistentData().getBoolean(ITEM_OFFERS_INITIALIZED)) {
            return;
        }
        addOfferIfMissing(trader, ModItems.NO_EAT_BAUBLE, 24);
        addOfferIfMissing(trader, ModItems.MORE_EAT_BAUBLE, 24);
        addOfferIfMissing(trader, ModItems.HUNT_ORDER, 32);
        addOfferIfMissing(trader, ModItems.WANDERING_MAID_BOOK, 12);
        addOfferIfMissing(trader, ModItems.MAID_SEED, 35 + trader.getRandom().nextInt(11));
        addOfferIfMissing(trader, InitItems.SHRINE, 40 + trader.getRandom().nextInt(21));
        int bedPrice = 4 + trader.getRandom().nextInt(5);
        List<DyeColor> bedColors = List.of(DyeColor.WHITE, DyeColor.BLACK, DyeColor.YELLOW,
                DyeColor.BLUE, DyeColor.GREEN, DyeColor.PURPLE);
        DyeColor first = bedColors.get(trader.getRandom().nextInt(bedColors.size()));
        DyeColor second;
        do {
            second = bedColors.get(trader.getRandom().nextInt(bedColors.size()));
        } while (second == first);
        addBedOfferIfMissing(trader, first, bedPrice);
        addBedOfferIfMissing(trader, second, bedPrice);
        addOfferIfMissing(trader, InitItems.SMART_SLAB_EMPTY, 8 + trader.getRandom().nextInt(9));
        addOfferIfMissing(trader, InitItems.ULTRAMARINE_ORB_ELIXIR, 40);
        addOfferIfMissing(trader, InitItems.EXPLOSION_PROTECT_BAUBLE, 20);
        addOfferIfMissing(trader, InitItems.FIRE_PROTECT_BAUBLE, 18);
        addOfferIfMissing(trader, InitItems.PROJECTILE_PROTECT_BAUBLE, 20);
        addOfferIfMissing(trader, InitItems.MAGIC_PROTECT_BAUBLE, 24);
        addOfferIfMissing(trader, InitItems.FALL_PROTECT_BAUBLE, 14);
        addOfferIfMissing(trader, InitItems.DROWN_PROTECT_BAUBLE, 16);
        addOfferIfMissing(trader, InitItems.NIMBLE_FABRIC, 24);
        addOfferIfMissing(trader, InitItems.ITEM_MAGNET_BAUBLE, 28);
        addOfferIfMissing(trader, InitItems.MUTE_BAUBLE, 12);
        addOfferIfMissing(trader, InitItems.WIRELESS_IO, 32);
        trader.getPersistentData().putBoolean(ITEM_OFFERS_INITIALIZED, true);
    }

    private static void addOfferIfMissing(WanderingTrader trader, Supplier<? extends Item> item, int price) {
        if (trader.getOffers().stream().anyMatch(offer -> offer.getResult().is(item.get()))) {
            return;
        }
        trader.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, price),
                new ItemStack(item.get()), 8, 1, 0.05F));
    }

    private static void addBedOfferIfMissing(WanderingTrader trader, DyeColor color, int price) {
        if (trader.getOffers().stream().anyMatch(offer -> offer.getResult().is(InitItems.MAID_BED.get())
                && ItemMaidBed.getColor(offer.getResult()) == color)) {
            return;
        }
        ItemStack bed = new ItemStack(InitItems.MAID_BED.get());
        ItemMaidBed.setColor(color, bed);
        trader.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, price), bed, 8, 1, 0.05F));
    }

    private static boolean isInjectedOffer(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        return result.is(ModItems.NO_EAT_BAUBLE.get()) || result.is(ModItems.MORE_EAT_BAUBLE.get())
                || result.is(ModItems.HUNT_ORDER.get()) || result.is(ModItems.WANDERING_MAID_BOOK.get())
                || result.is(ModItems.MAID_SEED.get())
                || result.is(InitItems.SHRINE.get())
                || result.is(InitItems.MAID_BED.get())
                || result.is(InitItems.SMART_SLAB_EMPTY.get())
                || result.is(InitItems.ULTRAMARINE_ORB_ELIXIR.get())
                || result.is(InitItems.EXPLOSION_PROTECT_BAUBLE.get())
                || result.is(InitItems.FIRE_PROTECT_BAUBLE.get())
                || result.is(InitItems.PROJECTILE_PROTECT_BAUBLE.get())
                || result.is(InitItems.MAGIC_PROTECT_BAUBLE.get())
                || result.is(InitItems.FALL_PROTECT_BAUBLE.get())
                || result.is(InitItems.DROWN_PROTECT_BAUBLE.get())
                || result.is(InitItems.NIMBLE_FABRIC.get())
                || result.is(InitItems.ITEM_MAGNET_BAUBLE.get())
                || result.is(InitItems.MUTE_BAUBLE.get())
                || result.is(InitItems.WIRELESS_IO.get());
    }

    private static void cleanDisabledMarket(ServerLevel level) {
        Set<UUID> handledTraders = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB area = player.getBoundingBox().inflate(TRADER_SCAN_RADIUS);
            for (WanderingTrader trader : level.getEntitiesOfClass(WanderingTrader.class, area,
                    candidate -> handledTraders.add(candidate.getUUID()))) {
                trader.getOffers().removeIf(TradingMaidManager::isInjectedOffer);
                trader.getPersistentData().remove(ITEM_OFFERS_INITIALIZED);
                trader.getPersistentData().remove(TRADER_INITIALIZED);
                trader.getPersistentData().remove(SECOND_WAVE_SPAWNED);
            }
            List<EntityMaid> marketMaids = new ArrayList<>(level.getEntitiesOfClass(EntityMaid.class, area,
                    TradingMaidData::isTrading));
            marketMaids.forEach(Entity::discard);
        }
    }

    private static void spawnStockMaid(ServerLevel level, WanderingTrader trader, ServerPlayer skinOwner) {
        EntityMaid maid = EntityMaid.TYPE.create(level);
        if (maid == null) {
            return;
        }
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        double distance = 2.0 + level.getRandom().nextDouble() * 2.0;
        BlockPos pos = BlockPos.containing(trader.getX() + Math.cos(angle) * distance,
                trader.getY(), trader.getZ() + Math.sin(angle) * distance);
        maid.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        maid.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        maid.setModelId(selectSharedModel(level, skinOwner.getUUID()));
        maid.setPersistenceRequired();
        prepareForSale(maid, trader, randomPrice(level));
        if (level.addFreshEntity(maid)) {
            maid.setLeashedTo(trader, true);
        }
    }

    private static String selectSharedModel(ServerLevel level, UUID playerId) {
        WanderingMaidSavedData savedData = WanderingMaidSavedData.get(level.getServer().overworld());
        Set<String> available = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet();
        List<String> pool = savedData.skinPool(playerId).stream().filter(available::contains).toList();
        if (!pool.isEmpty()) {
            return pool.get(level.getRandom().nextInt(pool.size()));
        }
        return available.contains(DEFAULT_MODEL) ? DEFAULT_MODEL : available.stream().findFirst().orElse(DEFAULT_MODEL);
    }

    private static int randomPrice(ServerLevel level) {
        return MIN_PRICE + level.getRandom().nextInt(MAX_PRICE - MIN_PRICE + 1);
    }

    private static void prepareForSale(EntityMaid maid, WanderingTrader trader, int price) {
        maid.stopUsingItem();
        maid.setTarget(null);
        maid.setBegging(false);
        maid.setInSittingPose(false);
        maid.setTame(false, false);
        maid.setOwnerUUID(null);
        TradingMaidData.initialize(maid, trader.getUUID(), price);
    }

    private static void enforceTradingMaids(ServerLevel level) {
        Set<UUID> handled = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class,
                    player.getBoundingBox().inflate(TRADER_SCAN_RADIUS),
                    candidate -> TradingMaidData.isTrading(candidate) && handled.add(candidate.getUUID()))) {
                maid.setTame(false, false);
                maid.setOwnerUUID(null);
                maid.setTarget(null);
                maid.setBegging(false);
                Entity linkedTrader = TradingMaidData.trader(maid).map(level::getEntity).orElse(null);
                if (!(linkedTrader instanceof WanderingTrader trader) || !trader.isAlive()) {
                    maid.discard();
                }
            }
        }
    }

    private static void tickPurchasedMaids(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class,
                    player.getBoundingBox().inflate(64), m -> m.getPersistentData().hasUUID(PURCHASE_MOVING))) {
                UUID ownerId = maid.getPersistentData().getUUID(PURCHASE_MOVING);
                Player owner = level.getPlayerByUUID(ownerId);
                long deadline = MaidMovementControl.getDeadline(maid, MaidMovementControl.Reason.PURCHASE_MOVING);
                if (owner == null || !maid.isAlive() || !maid.isTame()
                        || !ownerId.equals(maid.getOwnerUUID()) || level.getGameTime() >= deadline) {
                    maid.setBegging(false);
                    MaidMovementControl.clearNavigation(maid);
                    MaidMovementControl.end(maid, MaidMovementControl.Reason.PURCHASE_MOVING);
                    maid.getPersistentData().remove(PURCHASE_MOVING);
                    continue;
                }
                if (maid.distanceToSqr(owner) > 2.25) {
                    maid.setBegging(true);
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, owner, 0.6F, 1);
                } else {
                    maid.setBegging(false);
                    maid.getNavigation().stop();
                    MaidMovementControl.end(maid, MaidMovementControl.Reason.PURCHASE_MOVING);
                    maid.setInSittingPose(true);
                    maid.getPersistentData().remove(PURCHASE_MOVING);
                    maid.getChatBubbleManager().addTextChatBubble("bubble.callresponse.trade.greeting");
                }
            }
        }
    }

    @SubscribeEvent
    public void onInteractMaid(InteractMaidEvent event) {
        if (TradingMaidData.isTrading(event.getMaid())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDirectMaidInteraction(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof EntityMaid maid && TradingMaidData.isTrading(maid)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Lets vanilla open the merchant screen and only sends a short-lived marker for the extra button. */
    @SubscribeEvent
    public void onTraderInteraction(PlayerInteractEvent.EntityInteract event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof WanderingTrader trader)
                || !(trader.level() instanceof ServerLevel level)) {
            return;
        }
        // Do this before vanilla sends MerchantOffers, so even a freshly spawned trader has the full scroll list.
        initialiseTrader(level, trader, player);
        PacketDistributor.sendToPlayer(player, new EnableTradingMaidButtonS2CPacket(trader.getId()));
    }

    public static void sendTradingScreen(ServerPlayer player, int traderId) {
        WanderingTrader trader = validTrader(player, traderId);
        if (trader == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<OpenTradingMaidScreenS2CPacket.MaidInfo> buys = level.getEntitiesOfClass(EntityMaid.class,
                        trader.getBoundingBox().inflate(TRADE_RADIUS), maid -> maid.isAlive()
                                && TradingMaidData.isTrading(maid)
                                && TradingMaidData.trader(maid).filter(trader.getUUID()::equals).isPresent())
                .stream().sorted(Comparator.comparingInt(TradingMaidData::price))
                .map(TradingMaidManager::toBuyInfo).toList();
        List<OpenTradingMaidScreenS2CPacket.MaidInfo> sells = level.getEntitiesOfClass(EntityMaid.class,
                        player.getBoundingBox().inflate(SELL_RADIUS), maid -> maid.isAlive() && maid.isOwnedBy(player)
                                && !TradingMaidData.isTrading(maid) && !WanderingMaidData.isSpecial(maid))
                .stream().sorted(Comparator.comparing(maid -> maid.getDisplayName().getString()))
                .map(TradingMaidManager::toSellInfo).toList();
        PacketDistributor.sendToPlayer(player,
                new OpenTradingMaidScreenS2CPacket(traderId, buys, sells));
    }

    private static OpenTradingMaidScreenS2CPacket.MaidInfo toBuyInfo(EntityMaid maid) {
        return maidInfo(maid, TradingMaidData.price(maid));
    }

    private static OpenTradingMaidScreenS2CPacket.MaidInfo toSellInfo(EntityMaid maid) {
        return maidInfo(maid, favorabilityToolCount(maid));
    }

    private static OpenTradingMaidScreenS2CPacket.MaidInfo maidInfo(EntityMaid maid, int value) {
        return new OpenTradingMaidScreenS2CPacket.MaidInfo(maid.getId(), maid.getUUID(), maid.getDisplayName(),
                maid.getModelId(), maid.hasCustomName(), value);
    }

    public static void handleTradingAction(ServerPlayer player, int traderId, UUID maidId,
                                           TradingMaidActionC2SPacket.Action action) {
        WanderingTrader trader = validTrader(player, traderId);
        if (trader == null) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(maidId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            sendTradingScreen(player, traderId);
            return;
        }
        if (action == TradingMaidActionC2SPacket.Action.BUY) {
            buySelectedMaid(player, trader, maid);
        } else {
            sellSelectedMaid(player, trader, maid);
        }
        sendTradingScreen(player, traderId);
    }

    private static WanderingTrader validTrader(ServerPlayer player, int traderId) {
        if (!enabled()) {
            return null;
        }
        Entity entity = player.serverLevel().getEntity(traderId);
        if (!(entity instanceof WanderingTrader trader) || !trader.isAlive()
                || player.distanceToSqr(trader) > TRADE_RADIUS * TRADE_RADIUS) {
            return null;
        }
        return trader;
    }

    private static void buySelectedMaid(ServerPlayer buyer, WanderingTrader trader, EntityMaid maid) {
        if (!TradingMaidData.isTrading(maid)
                || TradingMaidData.trader(maid).filter(trader.getUUID()::equals).isEmpty()
                || maid.distanceToSqr(trader) > TRADE_RADIUS * TRADE_RADIUS) {
            return;
        }
        int price = TradingMaidData.price(maid);
        List<EntityMaid> witnesses = nearbyOwnedWitnesses(buyer, maid);
        if (!buyer.isCreative() && buyer.getInventory().countItem(Items.EMERALD) < price) {
            buyer.displayClientMessage(Component.translatable("message.callresponse.trade.not_enough", price), true);
            return;
        }
        TradingMaidData.setPurchaseAuthorized(maid, true);
        InteractionResult result;
        try {
            result = ((EntityMaidTameInvoker) maid).callresponse$invokeTameMaid(new ItemStack(Items.CAKE), buyer);
        } finally {
            TradingMaidData.setPurchaseAuthorized(maid, false);
        }
        if (!result.consumesAction() || !maid.isOwnedBy(buyer)) {
            buyer.sendSystemMessage(Component.translatable("message.callresponse.trade.tame_failed"));
            return;
        }
        if (!buyer.isCreative()) {
            buyer.getInventory().clearOrCountMatchingItems(stack -> stack.is(Items.EMERALD), price,
                    buyer.inventoryMenu.getCraftSlots());
        }
        maid.dropLeash(true, false);
        TradingMaidData.clear(maid);
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.PURCHASE_MOVING,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
        maid.setInSittingPose(false);
        maid.setBegging(true);
        maid.getPersistentData().putUUID(PURCHASE_MOVING, buyer.getUUID());
        MaidMovementControl.setDeadline(maid, MaidMovementControl.Reason.PURCHASE_MOVING,
                maid.level().getGameTime() + PURCHASE_MOVE_TIMEOUT);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, buyer, 0.6F, 1);
        buyer.sendSystemMessage(Component.translatable("message.callresponse.trade.bought", price));
        maid.getChatBubbleManager().addTextChatBubble("bubble.callresponse.trade.new_owner");
        notifyPurchaseWitnesses(buyer, witnesses);
        refreshStockOnceIfEmpty(buyer, trader);
    }

    private static void sellSelectedMaid(ServerPlayer seller, WanderingTrader trader, EntityMaid maid) {
        if (!maid.isOwnedBy(seller) || TradingMaidData.isTrading(maid) || WanderingMaidData.isSpecial(maid)
                || maid.distanceToSqr(seller) > SELL_RADIUS * SELL_RADIUS) {
            return;
        }
        List<EntityMaid> witnesses = nearbyOwnedWitnesses(seller, maid);
        int favorabilityTools = favorabilityToolCount(maid);
        dropAllMaidItems(maid);
        maid.dropLeash(true, false);
        maid.teleportTo(trader.getX() + 1.25, trader.getY(), trader.getZ() + 1.25);
        prepareForSale(maid, trader, randomPrice(seller.serverLevel()));
        maid.setLeashedTo(trader, true);
        giveSaleReward(seller, seller.serverLevel(), favorabilityTools);
        seller.sendSystemMessage(Component.translatable("message.callresponse.trade.sold"));
        maid.getChatBubbleManager().addTextChatBubble("bubble.callresponse.trade.sold");
        notifySaleWitnesses(seller, witnesses);
    }

    private static int favorabilityToolCount(EntityMaid maid) {
        return Math.max(1, maid.getFavorability() / 64 + 1);
    }

    private static void giveSaleReward(ServerPlayer player, ServerLevel level, int favorabilityTools) {
        if (favorabilityTools > 0) {
            give(player, new ItemStack(ModItems.DISPOSABLE_FAVORABILITY_TOOL_ADD.get(), favorabilityTools));
        }
        give(player, new ItemStack(Items.NETHERITE_INGOT, 1 + level.getRandom().nextInt(3)));
        give(player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3 + level.getRandom().nextInt(6)));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    private static void refreshStockOnceIfEmpty(ServerPlayer buyer, WanderingTrader trader) {
        ServerLevel level = buyer.serverLevel();
        CompoundTag data = trader.getPersistentData();
        if (data.getBoolean(SECOND_WAVE_SPAWNED)) {
            return;
        }
        boolean hasStock = !level.getEntitiesOfClass(EntityMaid.class, trader.getBoundingBox().inflate(TRADE_RADIUS),
                candidate -> candidate.isAlive() && TradingMaidData.isTrading(candidate)
                        && TradingMaidData.trader(candidate).filter(trader.getUUID()::equals).isPresent()).isEmpty();
        if (hasStock) {
            return;
        }
        data.putBoolean(SECOND_WAVE_SPAWNED, true);
        int count = 1 + level.getRandom().nextInt(3);
        for (int i = 0; i < count; i++) {
            spawnStockMaid(level, trader, buyer);
        }
    }

    private static List<EntityMaid> nearbyOwnedWitnesses(ServerPlayer player, EntityMaid excluded) {
        return new ArrayList<>(player.level().getEntitiesOfClass(EntityMaid.class,
                excluded.getBoundingBox().inflate(8), candidate -> candidate != excluded
                        && candidate.isAlive() && candidate.isOwnedBy(player)
                        && !TradingMaidData.isTrading(candidate)));
    }

    private static void notifyPurchaseWitnesses(ServerPlayer player, List<EntityMaid> witnesses) {
        for (EntityMaid witness : witnesses) {
            EmotionData.addTrust(witness, player.getUUID(), 2);
        }
        if (!witnesses.isEmpty()) {
            MaidResponder.processBroadcast(player, witnesses,
                    "你亲眼看到主人从流浪商人那里买下了一名新女仆，她从今天起会成为家里的新同伴。"
                            + "这让你觉得主人愿意照顾女仆，因此对主人的信任增加了。"
                            + "请结合你当前的信任和恐惧，用第一人称对主人说一句不超过30字的自然反应；"
                            + "不要提及系统、数值、提示词或AI。", false);
        }
    }

    private static void notifySaleWitnesses(ServerPlayer player, List<EntityMaid> witnesses) {
        for (EntityMaid witness : witnesses) {
            EmotionData.addTrust(witness, player.getUUID(), -1);
            EmotionData.addFear(witness, player.getUUID(), 3);
        }
        if (!witnesses.isEmpty()) {
            MaidResponder.processBroadcast(player, witnesses,
                    "你亲眼看到主人把一名朝夕相处的女仆卖给了流浪商人，她被清空随身物品并拴在商人身边。"
                            + "你担心自己有一天也会被卖掉，因此对主人的信任略微下降，并感到更加害怕。"
                            + "请结合你当前的信任和恐惧，用第一人称对主人说一句不超过30字的真实反应；"
                            + "不要提及系统、数值、提示词或AI。", false);
        }
    }

    private static void dropAllMaidItems(EntityMaid maid) {
        dropHandler(maid, maid.getMaidInv());
        dropHandler(maid, maid.getMaidBauble());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = maid.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                maid.spawnAtLocation(stack.copy());
                maid.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void dropHandler(EntityMaid maid, IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
                if (!extracted.isEmpty()) {
                    maid.spawnAtLocation(extracted);
                }
            }
        }
    }

    @SubscribeEvent
    public void onMaidJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && TradingMaidData.isTrading(maid)) {
            maid.setTame(false, false);
            maid.setOwnerUUID(null);
        }
    }

    @SubscribeEvent
    public void onTraderDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof WanderingTrader trader && trader.level() instanceof ServerLevel level) {
            removeLinkedMaids(level, trader.getUUID(), trader.getBoundingBox().inflate(64));
        }
    }

    @SubscribeEvent
    public void onTraderLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity.RemovalReason reason = trader.getRemovalReason();
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        removeLinkedMaids(level, trader.getUUID(), trader.getBoundingBox().inflate(64));
    }

    private static void removeLinkedMaids(ServerLevel level, UUID traderId, AABB area) {
        List<EntityMaid> linked = new ArrayList<>(level.getEntitiesOfClass(EntityMaid.class, area,
                maid -> TradingMaidData.isTrading(maid)
                        && TradingMaidData.trader(maid).filter(traderId::equals).isPresent()));
        linked.forEach(Entity::discard);
    }
}
