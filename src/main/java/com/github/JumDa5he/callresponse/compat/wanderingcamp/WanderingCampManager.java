package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import com.github.JumDa5he.callresponse.mixin.accessor.EntityMaidTameInvoker;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataAttachment;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.event.MaidMealRegConfigEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BigBackpack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.util.UUID;

public final class WanderingCampManager {
    public static final int PANIC_TICKS = 200, CONSIDER_TICKS = 100, RETRY_TICKS = 1200;
    public static final int RADIUS = 8, SIT_TICKS = 1200;
    public static final int[] SIT_TIMES = {1000, 11000};
    public static final int NIGHT_START = 12500, NIGHT_END = 23500;

    /** Shared technical anchor and the sleeping surface are deliberately separate coordinates. */
    public static void generate(ServerLevel level, BlockPos marker, BlockPos head, Direction facing, boolean forceGly) {
        BlockPos foot = head.relative(facing.getOpposite());
        String id = level.dimension().location() + "@" + head.asLong();
        var ledger = WanderingCampSavedData.get(level);
        CompoundTag entry = ledger.entry(id);
        if (entry.getBoolean("Spawned") || entry.getInt("Attempts") >= 3) {
            return;
        }
        if (!entry.contains("Gly")) {
            entry.putBoolean("Gly", forceGly || level.getRandom().nextInt(100) == 0);
            entry.putString("Type", "wandering"); entry.putLong("Anchor", marker.asLong());
            entry.putLong("Head", head.asLong()); entry.putInt("Facing", facing.get2DDataValue());
            // Known template coordinates, not a search for containers/markers in the world.
            net.minecraft.world.level.block.Rotation rotation = switch (facing) {
                case EAST -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
                case SOUTH -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
                case WEST -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
                default -> net.minecraft.world.level.block.Rotation.NONE;
            };
            var containers = new net.minecraft.nbt.ListTag();
            for (BlockPos offset : java.util.List.of(new BlockPos(-1,0,0), new BlockPos(2,0,2))) {
                CompoundTag container = new CompoundTag();
                container.putLong("Pos", head.offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                        .transform(offset, net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO)).asLong());
                container.putString("Table", offset.getX() == -1 ? "callresponse:chests/wandering_maid_camp"
                        : "callresponse:chests/wandering_maid_camp_supplies");
                containers.add(container);
            }
            entry.put("Containers", containers); ledger.setDirty();
        }
        WanderingCampStory.select(level, ledger, entry, head.relative(facing.getCounterClockWise()));
        if (!level.getBlockState(head).is(Blocks.YELLOW_CARPET) || !level.getBlockState(foot).is(Blocks.YELLOW_CARPET)) {
            fail(level, ledger, entry, id, "registered bedding missing");
            return;
        }
        if (entry.hasUUID("Maid") && level.getEntity(entry.getUUID("Maid")) != null) {
            entry.putBoolean("Spawned", true); ledger.setDirty();
            if (level.getEntity(entry.getUUID("Maid")) instanceof EntityMaid existing) {
                WanderingCampStory.residentAdded(level, ledger, id, entry, existing);
            }
            // Closing a reservation occupied by an unrelated UUID claimant grants no GLY book.
            if (!entry.getBoolean("GlySpawned")) WanderingCampStory.finishOrdinary(level, ledger, entry);
            return;
        }
        EntityMaid maid = EntityMaid.TYPE.create(level);
        if (maid == null) { fail(level, ledger, entry, id, "entity creation failed"); return; }
        // Reserve and persist one identity; a failed spawn retries this UUID, not a fresh maid.
        UUID uuid = entry.hasUUID("Maid") ? entry.getUUID("Maid") : UUID.randomUUID();
        entry.putUUID("Maid", uuid); ledger.setDirty();
        maid.setUUID(uuid);
        maid.moveTo(head.getX() + .5, head.getY() + 1.0 / 16, head.getZ() + .5, facing.toYRot(), 0);
        if (!WanderingCampBrain.safeStand(maid, head) || !level.noCollision(maid)) {
            fail(level, ledger, entry, id, "unsafe spawn point"); return;
        }
        maid.finalizeSpawn(level, level.getCurrentDifficultyAt(head), MobSpawnType.STRUCTURE, null);
        maid.setTame(false, false); maid.setOwnerUUID(null); maid.setPersistenceRequired();
        var favorability = maid.getFavorabilityManager();
        favorability.add(favorability.getPointByLevel(2) - maid.getFavorability());
        maid.setHealth(maid.getMaxHealth());
        BackpackManager.findBackpack(BigBackpack.ID).ifPresent(maid::setMaidBackpackType);
        maid.getConfigManager().setShowBackpack(false);
        ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), new ItemStack(Items.SWEET_BERRIES, 30), false);
        ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), new ItemStack(Items.BREAD, 10), false);
        maid.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        maid.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        maid.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        maid.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        Player player = level.getNearestPlayer(head.getX()+.5, head.getY(), head.getZ()+.5, -1, p -> !p.isSpectator());
        // The existing shared roster accepts null and falls back to its existing available-model pool.
        try { maid.setModelId(WanderingMaidManager.selectSharedModel(level, player == null ? null : player.getUUID())); }
        catch (RuntimeException ex) {
            maid.setModelId(WanderingMaidManager.DEFAULT_MODEL);
            CallResponseMod.LOGGER.warn("[Wandering camp] Roster unavailable at {}; using existing fallback", head, ex);
        }
        CompoundTag d = new CompoundTag();
        d.putBoolean("Active", true); d.putString("Camp", id);
        d.putLong("Head", head.asLong()); d.putInt("Facing", facing.get2DDataValue());
        d.putBoolean("Gly", entry.getBoolean("Gly"));
        d.putLong("SitDay0", Long.MIN_VALUE); d.putLong("SitDay1", Long.MIN_VALUE);
        maid.getPersistentData().put(WanderingCampData.KEY, d);
        if (d.getBoolean("Gly")) WanderingCampGly.initialize(maid);
        WanderingCampData.sync(maid);
        maid.refreshBrain(level);
        if (!level.addFreshEntity(maid)) { fail(level, ledger, entry, id, "world rejected entity"); return; }
        entry.putBoolean("Spawned", true); ledger.setDirty();
        WanderingCampStory.residentAdded(level, ledger, id, entry, maid);
    }

    @SubscribeEvent
    public void onPendingStoryBooks(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0)
            WanderingCampSavedData.get(level).retryStoryBooks(level);
    }

    private static void fail(ServerLevel level, WanderingCampSavedData ledger, CompoundTag e, String id, String reason) {
        int attempts = e.getInt("Attempts") + 1;
        e.putInt("Attempts", attempts); ledger.setDirty();
        if (attempts >= 3) WanderingCampStory.finishOrdinary(level, ledger, e);
        CallResponseMod.LOGGER.warn("[Wandering camp] {}: {} (attempt {}/3)", id, reason, attempts);
    }

    public static boolean canAdopt(EntityMaid maid, Player player) {
        return player != null && maid.isAlive() && !maid.isTame() && maid.getOwnerUUID() == null && player.isAlive() && !player.isSpectator()
                && player.level() == maid.level() && player.distanceToSqr(maid) <= 64
                && maid.getTamedItem().test(new ItemStack(Items.CAKE))
                && (player.getData(InitDataAttachment.MAID_NUM).canAdd() || player.isCreative());
    }

    public static InteractionResult invite(EntityMaid maid, Player player, InteractionHand hand) {
        if (maid.level().isClientSide) return InteractionResult.SUCCESS;
        return inviteInternal(maid, player, hand);
    }
    private static InteractionResult inviteInternal(EntityMaid maid, Player player, InteractionHand hand) {
        if (((CampMaid) maid).callresponse$campTaming()) return InteractionResult.CONSUME;
        CompoundTag d = WanderingCampData.data(maid);
        long now = maid.level().getGameTime();
        if (hasInvitation(d) && !validInvitation(d, now)) interrupt(maid);
        if (hasInvitation(d) || now < d.getLong("PanicUntil") || !canAdopt(maid, player)) return InteractionResult.CONSUME;
        if (now < d.getLong("RetryAfter")) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.callresponse.wandering_camp.retry_wait", (d.getLong("RetryAfter") - now + 19) / 20), true);
            return InteractionResult.CONSUME;
        }
        ItemStack cake = player.getItemInHand(hand);
        if (!cake.is(Items.CAKE)) return InteractionResult.PASS;
        cake.shrink(1); // Only an actually gifted cake increases this player's probability, including test mode.
        WanderingCampData.credit(maid, player.getUUID(), 10);
        d.putUUID("Inviter", player.getUUID());
        d.putLong("ConsiderUntil", now + CONSIDER_TICKS);
        d.putBoolean("Accept", maid.getRandom().nextInt(100) < WanderingCampData.chance(maid, player.getUUID()));
        try {
            WanderingCampBrain.release(maid, true);
            WanderingCampData.sync(maid);
        } catch (RuntimeException failure) {
            CallResponseMod.LOGGER.warn("Camp invitation start interrupted for {}", maid.getUUID(), failure);
            interrupt(maid);
            return InteractionResult.CONSUME;
        }
        invitationLine(maid, "consider");
        return InteractionResult.CONSUME;
    }

    /** A pending player transaction must not depend on WALK_TARGET ownership or a running Brain. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onInvitationTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide || !WanderingCampData.active(maid)
                || ((CampMaid) maid).callresponse$campTaming()
                || !hasInvitation(WanderingCampData.data(maid))) return;
        if (!validInvitation(WanderingCampData.data(maid), maid.level().getGameTime()) || event.isCanceled() || !maid.isAlive() || WanderingCampBrain.blocked(maid)
                || maid.level().getGameTime() < WanderingCampData.data(maid).getLong("PanicUntil")) {
            interrupt(maid); // Respect pauses and safety limits, but never leave a permanent pending lock.
            return;
        }
        finishInvitation(maid);
    }

    /** Reuse TLM's actual heal-food blacklist, including its tag and resolved regex entries. */
    public static boolean unwantedFood(ItemStack stack) {
        return IMaidMeal.isBlockList(stack,
                MaidConfig.MAID_HEAL_MEALS_BLOCK_LIST.get())
                || IMaidMeal.isBlockList(stack,
                MaidMealRegConfigEvent.HEAL_MEAL_REGEX);
    }

    private static boolean hasInvitation(CompoundTag d) {
        return d.contains("Inviter") || d.contains("ConsiderUntil") || d.contains("Accept");
    }

    private static boolean validInvitation(CompoundTag d, long now) {
        return d.hasUUID("Inviter") && d.contains("ConsiderUntil", CompoundTag.TAG_LONG)
                && d.contains("Accept", CompoundTag.TAG_BYTE)
                && d.getLong("ConsiderUntil") <= now + CONSIDER_TICKS;
    }

    public static void finishInvitation(EntityMaid maid) {
        if (maid.level().isClientSide || ((CampMaid) maid).callresponse$campTaming()) return; // Native event re-entry shares this transaction.
        CompoundTag d = WanderingCampData.data(maid);
        if (!hasInvitation(d)) return;
        long now = maid.level().getGameTime();
        if (!validInvitation(d, now)) { interrupt(maid); return; }
        Player nearby = maid.level().getPlayerByUUID(d.getUUID("Inviter"));
        ServerPlayer player = nearby instanceof ServerPlayer p ? p : null;
        if (!canAdopt(maid, player)) { interrupt(maid); return; }
        if (now < d.getLong("ConsiderUntil")) return;
        CampMaid bridge = (CampMaid) maid;
        try {
            if (d.getBoolean("Accept")) {
                bridge.callresponse$campTaming(true);
                // The cake was already gifted; only native TLM owns tame/count/event processing.
                ((EntityMaidTameInvoker) maid).callresponse$invokeTameMaid(new ItemStack(Items.CAKE), player);
            }
        } catch (RuntimeException failure) {
            CallResponseMod.LOGGER.warn("Camp native taming interrupted for {}", maid.getUUID(), failure);
        } finally {
            bridge.callresponse$campTaming(false);
            // A handler may throw after native taming has committed. Never tame or award twice.
            boolean success = maid.isTame() && player.getUUID().equals(maid.getOwnerUUID());
            endInvitation(maid, success ? InvitationEnd.ACCEPT : InvitationEnd.REFUSE, player);
        }
    }

    private enum InvitationEnd { ACCEPT, REFUSE, INTERRUPT }

    /** Commit all transaction state before optional dialogue; repeated calls are no-ops. */
    private static void endInvitation(EntityMaid maid, InvitationEnd end, ServerPlayer player) {
        if (maid.level().isClientSide || ((CampMaid) maid).callresponse$campTaming()) return;
        CompoundTag d = WanderingCampData.data(maid);
        if (!hasInvitation(d)) return;
        d.remove("Inviter"); d.remove("ConsiderUntil"); d.remove("Accept");
        if (end != InvitationEnd.ACCEPT) d.putLong("RetryAfter", maid.level().getGameTime() + RETRY_TICKS);
        d.remove("SitPendingUntil");
        try { WanderingCampBrain.release(maid, true); }
        catch (RuntimeException failure) {
            CallResponseMod.LOGGER.warn("Camp invitation control release failed for {}", maid.getUUID(), failure);
        }
        if (end == InvitationEnd.ACCEPT) {
            d.putBoolean("Active", false);
            WanderingBehaviorAuthority.unbind(maid.getBrain());
            maid.clearRestriction();
            try { maid.refreshBrain((ServerLevel) maid.level()); }
            catch (RuntimeException failure) {
                CallResponseMod.LOGGER.warn("Adopted camp maid Brain refresh failed for {}", maid.getUUID(), failure);
            }
        }
        try { WanderingCampData.sync(maid); }
        catch (RuntimeException failure) {
            CallResponseMod.LOGGER.warn("Camp invitation sync failed for {}", maid.getUUID(), failure);
        }
        if (end == InvitationEnd.ACCEPT) {
            // Claim before delivery, and do not let a failing bubble skip the existing one-shot reward.
            if (WanderingCampSavedData.get((ServerLevel) maid.level()).claim(d.getString("Camp"))) {
                try { rewards(maid, "adoption").forEach(stack -> ItemHandlerHelper.giveItemToPlayer(player, stack)); }
                catch (RuntimeException failure) {
                    CallResponseMod.LOGGER.warn("Camp adoption reward delivery failed for {}", maid.getUUID(), failure);
                }
            }
            invitationLine(maid, "accept");
        } else if (end == InvitationEnd.REFUSE) invitationLine(maid, "refuse");
    }

    private static void invitationLine(EntityMaid maid, String pool) {
        try { say(maid, pool, 3); }
        catch (RuntimeException failure) {
            CallResponseMod.LOGGER.warn("Camp invitation {} dialogue failed after state settlement for {}", pool, maid.getUUID(), failure);
        }
    }

    public static void interrupt(EntityMaid maid) {
        endInvitation(maid, InvitationEnd.INTERRUPT, null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof EntityMaid maid)
                || !WanderingCampData.active(maid)) return;
        WanderingCampData.sync(maid);
        // NeoForge persistent data can be restored after makeBrain; refresh only this staged maid once on joining.
        WanderingCampBrain.release(maid, true);
        WanderingCampData.data(maid).remove("SitPendingUntil");
        maid.refreshBrain(level);
        if (event.loadedFromDisk()) {
            var ledger = WanderingCampSavedData.get(level);
            CompoundTag entry = ledger.entry(WanderingCampData.data(maid).getString("Camp"));
            entry.putBoolean("Spawned", true); entry.putUUID("Maid", maid.getUUID()); ledger.setDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof EntityMaid maid)
                || maid.level().isClientSide || !WanderingCampData.active(maid)) return;
        CompoundTag d = WanderingCampData.data(maid);
        WanderingCampGly.hurt(maid);
        long now = maid.level().getGameTime();
        boolean hurtLineDue = now >= d.getLong("PanicUntil");
        d.putLong("PanicUntil", now + PANIC_TICKS);
        Entity attacker = event.getSource().getEntity();
        if (attacker == null && ((CampMaid)maid).callresponse$campDamageAttacker() != null)
            attacker = ((ServerLevel)maid.level()).getEntity(((CampMaid)maid).callresponse$campDamageAttacker());
        // Retain the existing quiet-window cooldown, but don't accuse an attacker for fire/fall damage.
        if (hurtLineDue) {
            if (attacker != null) say(maid, "panic", 6);
            else if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) say(maid, "hazard", 2);
            else WanderingBehaviorAuthority.speak(maid, () -> maid.getChatBubbleManager()
                    .addTextChatBubble("bubble.callresponse.wandering_camp.hazard.1"));
        }
        d.putLong("Danger", (attacker == null ? maid.blockPosition() : attacker.blockPosition()).asLong());
        if (attacker != null) d.putUUID("Attacker", attacker.getUUID()); else d.remove("Attacker");
        interrupt(maid);
        WanderingCampBrain.release(maid, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)
                || !WanderingCampData.active(maid) || event.isCanceled()) return;
        if (!WanderingCampSavedData.get(level).claim(WanderingCampData.data(maid).getString("Camp"))) return;
        for (ItemStack stack : rewards(maid, "death"))
            event.getDrops().add(new ItemEntity(level, maid.getX(), maid.getY(), maid.getZ(), stack));
    }

    private static java.util.List<ItemStack> rewards(EntityMaid maid, String kind) {
        ServerLevel level = (ServerLevel) maid.level();
        var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, maid.position())
                .create(LootContextParamSets.CHEST);
        return level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("callresponse", "wandering_camp/" + kind)))
                .getRandomItems(params);
    }

    public static void say(EntityMaid maid, String pool, int size) {
        WanderingBehaviorAuthority.speak(maid, () -> maid.getChatBubbleManager().addTextChatBubble(
                "bubble.callresponse.wandering_camp." + pool + "." + maid.getRandom().nextInt(size)));
    }

    /** At the committed equipment stage, before removal; never run on a canceled death. */
    public static void deathDialogue(EntityMaid maid, net.minecraft.world.damagesource.DamageSource source) {
        if (!(maid.level() instanceof ServerLevel level) || !WanderingCampData.active(maid)
                || !((CampMaid)maid).callresponse$campDeathCommitted()) return;
        CompoundTag d = WanderingCampData.data(maid);
        if (d.getBoolean("DeathDialogueSent")) return;
        d.putBoolean("DeathDialogueSent", true);
        WanderingBehaviorAuthority.speak(maid, () -> {
            if (d.getBoolean("Gly")) { WanderingCampGly.death(maid); return; }
            Entity killer = source.getEntity();
            if (killer == null && ((CampMaid)maid).callresponse$campDamageAttacker() != null)
                killer = level.getEntity(((CampMaid)maid).callresponse$campDamageAttacker());
            int[] choices = killer instanceof LivingEntity && killer != maid
                    ? new int[]{0,1,2,3,4,5} : new int[]{1,4,5};
            var message = net.minecraft.network.chat.Component.translatable("message.callresponse.wandering_camp.death.format",
                    maid.getName(), net.minecraft.network.chat.Component.translatable(
                            "message.callresponse.wandering_camp.death." + choices[maid.getRandom().nextInt(choices.length)]));
            for (ServerPlayer player : level.players()) if (player.distanceToSqr(maid) <= 32 * 32)
                player.sendSystemMessage(message);
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOutgoingAttack(LivingIncomingDamageEvent event) {
        if (WanderingBehaviorAuthority.blocksDamage(event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSleepingLocation(net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent event) {
        CampSleepPlacement.checkLocation(event);
    }

    @SubscribeEvent
    public void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide()
                && WanderingCampData.active(maid)) {
            WanderingCampBrain.release(maid, true);
            WanderingCampData.data(maid).remove("SitPendingUntil");
            WanderingBehaviorAuthority.unbind(maid.getBrain());
        }
    }
}
