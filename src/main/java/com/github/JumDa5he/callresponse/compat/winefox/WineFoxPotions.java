package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.MapMaker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.UUID;

/** The two drinkable native potions. No alternate hunger, relationship or brewing system. */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class WineFoxPotions {
    public static final int MALICE_TICKS = 72000, SWEET_TICKS = 12000;
    static final double SWEET_ATTACK_BONUS = 0.20;
    public static final String MALICE_DATA = "CallResponseMalice";
    public static final String MAID_TIMERS = "CallResponsePotionTimers";
    public static final UUID MALICE_ATTACK_UUID = UUID.fromString("f77de2be-51e7-4dda-910e-2ef5aa3add01");
    public static final UUID MALICE_SPEED_UUID = UUID.fromString("f77de2be-51e7-4dda-910e-2ef5aa3add02");
    public static final UUID SWEET_ATTACK_UUID = UUID.fromString("f77de2be-51e7-4dda-910e-2ef5aa3add03");
    public static final UUID SWEET_REACH_UUID = UUID.fromString("f77de2be-51e7-4dda-910e-2ef5aa3add04");
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, CallResponseMod.MOD_ID);
    public static final RegistryObject<Potion> MALICE = POTIONS.register("malice", () -> new Potion(
            new MobEffectInstance(WineFoxEffects.CORRUPTED_HUNT.get(), MALICE_TICKS, 0)));
    public static final RegistryObject<Potion> DESSERT = POTIONS.register("dessert", () -> new Potion(
            new MobEffectInstance(WineFoxEffects.SWEETNESS.get(), SWEET_TICKS, 0)));
    private static final Map<EntityMaid, Timers> TIMERS = new MapMaker().weakKeys().makeMap();
    private static final Map<Player, Feed> LAST_FEED = new MapMaker().weakKeys().makeMap();
    private static final Map<LivingEntity, Boolean> REMOVAL_CHECKS = new MapMaker().weakKeys().makeMap();
    private static boolean brewingRegistered;

    public static void registerBrewing() {
        if (brewingRegistered) return;
        brewingRegistered = true;
        BrewingRecipeRegistry.addRecipe(new Recipe(ModItems.REMNANT_MEMORY.get(), MALICE));
        BrewingRecipeRegistry.addRecipe(new Recipe(Items.CAKE, DESSERT));
    }

    private record Recipe(Item material, RegistryObject<Potion> output) implements IBrewingRecipe {
        @Override public boolean isInput(ItemStack input) {
            return input.is(Items.POTION) && PotionUtils.getPotion(input) == Potions.AWKWARD;
        }
        @Override public boolean isIngredient(ItemStack ingredient) { return ingredient.is(material); }
        @Override public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            return isInput(input) && isIngredient(ingredient)
                    ? PotionUtils.setPotion(new ItemStack(Items.POTION), output.get()) : ItemStack.EMPTY;
        }
    }

    public static boolean isOrdinaryMaid(EntityMaid maid) {
        UUID owner = maid.getOwnerUUID();
        return maid.isTame() && owner != null && !owner.equals(new UUID(0, 0))
                && !BetrayalOutpostMaidData.isOutpostMaid(maid) && !WineFoxSurrender.isSurrendered(maid)
                && !WanderingMaidData.isSpecial(maid) && !WanderingCampData.active(maid);
    }
    public static boolean canReceiveSweetness(LivingEntity entity) {
        return entity instanceof Player || entity instanceof EntityMaid maid && isOrdinaryMaid(maid);
    }
    public static boolean hasSweetness(EntityMaid maid) {
        return isOrdinaryMaid(maid) && maid.hasEffect(WineFoxEffects.SWEETNESS.get());
    }
    public static boolean isCustomPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) return false;
        Potion potion = PotionUtils.getPotion(stack);
        return potion == MALICE.get() || potion == DESSERT.get();
    }
    public static boolean applyDrink(ItemStack stack, LivingEntity entity) {
        if (!isCustomPotion(stack)) return false;
        return PotionUtils.getPotion(stack) == MALICE.get() ? applyMalice(entity) : applyDessert(entity);
    }

    public static int getMaliceLevel(LivingEntity entity) {
        MobEffectInstance effect = entity.getEffect(WineFoxEffects.CORRUPTED_HUNT.get());
        if (effect == null) { clearMaliceLevel(entity); return 0; }
        CompoundTag data = entity.getPersistentData().getCompound(MALICE_DATA);
        return data.contains("Level", Tag.TAG_INT) ? Mth.clamp(data.getInt("Level"), 1, 255)
                : (int) Math.min(255L, Math.max(1L, (long) effect.getAmplifier() + 1L));
    }
    public static void ensureMaliceLevel(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || entity.getPersistentData().getCompound(MALICE_DATA).contains("Level", Tag.TAG_INT)) return;
        setLevel(entity, (int) Math.min(255L, Math.max(1L, (long) amplifier + 1L)));
    }
    private static void setLevel(LivingEntity entity, int level) {
        CompoundTag data = new CompoundTag();
        data.putInt("Level", Mth.clamp(level, 1, 255));
        entity.getPersistentData().put(MALICE_DATA, data);
    }
    public static void clearMaliceLevel(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().remove(MALICE_DATA);
        if (entity instanceof EntityMaid maid) {
            Timers timers = TIMERS.get(maid);
            if (timers != null) timers.fear = 0;
            maid.getPersistentData().getCompound(MAID_TIMERS).remove("FearTicks");
        }
    }

    public static boolean applyMalice(LivingEntity entity) {
        return applyMalice(entity, MALICE_TICKS);
    }

    /** Camp rewards share the potion's real level and exact-refresh path, then queue the normal check. */
    public static boolean applyMaliceReward(ServerPlayer player, int duration) {
        if (duration <= 0 || !applyMalice(player, duration)) return false;
        WineFoxPursuit.requestCheck(player);
        return true;
    }

    private static boolean applyMalice(LivingEntity entity, int duration) {
        if (!(entity.level() instanceof ServerLevel) || !entity.isAlive() || entity.isRemoved()
                || !(entity instanceof Player || entity instanceof EntityMaid maid && isOrdinaryMaid(maid))) return false;
        int next = Math.min(255, getMaliceLevel(entity) + 1);
        MobEffectInstance replacement = new MobEffectInstance(WineFoxEffects.CORRUPTED_HUNT.get(), duration, 0);
        entity.forceAddEffect(replacement, entity); // Exact refresh, without old stronger/hidden effect chains.
        if (entity.getEffect(WineFoxEffects.CORRUPTED_HUNT.get()) != replacement) return false;
        setLevel(entity, next);
        reconcile(entity);
        return true;
    }
    public static boolean applyDessert(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel) || !entity.isAlive() || entity.isRemoved() || !canReceiveSweetness(entity)) return false;
        MobEffectInstance replacement = new MobEffectInstance(WineFoxEffects.SWEETNESS.get(), SWEET_TICKS, 0);
        entity.forceAddEffect(replacement, entity);
        if (entity.getEffect(WineFoxEffects.SWEETNESS.get()) != replacement) return false;
        reconcile(entity);
        entity.setHealth(entity.getMaxHealth()); // One explicit drink/feed heal; never in the periodic tick.
        if (entity instanceof EntityMaid maid && HungerData.get(maid) < 15) HungerData.set(maid, 15);
        return true;
    }

    /** Imports command/external applications. Added fires before the new instance enters activeEffects. */
    @SubscribeEvent public static void effectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        MobEffectInstance incoming = event.getEffectInstance();
        if (incoming.getEffect() != WineFoxEffects.CORRUPTED_HUNT.get()) return;
        MobEffectInstance old = event.getOldEffectInstance();
        if (old != null) {
            // Added also fires for an update that vanilla rejects or only queues as a hidden effect.
            MobEffectInstance merged = new MobEffectInstance(old);
            if (!merged.update(incoming) || merged.getAmplifier() != incoming.getAmplifier()
                    || merged.getAmplifier() == old.getAmplifier() && merged.getDuration() == old.getDuration()) return;
        }
        setLevel(entity, (int) Math.min(255L, Math.max(1L, (long) incoming.getAmplifier() + 1L)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void effectRemoving(MobEffectEvent.Remove event) {
        if (!event.getEntity().level().isClientSide && (event.getEffect() == WineFoxEffects.CORRUPTED_HUNT.get()
                || event.getEffect() == WineFoxEffects.SWEETNESS.get())) REMOVAL_CHECKS.put(event.getEntity(), true);
    }
    @SubscribeEvent public static void effectExpiring(MobEffectEvent.Expired event) {
        if (!event.getEntity().level().isClientSide && event.getEffectInstance() != null
                && (event.getEffectInstance().getEffect() == WineFoxEffects.CORRUPTED_HUNT.get()
                || event.getEffectInstance().getEffect() == WineFoxEffects.SWEETNESS.get()))
            REMOVAL_CHECKS.put(event.getEntity(), true);
    }
    @SubscribeEvent public static void afterRemovals(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // removeAllEffects (milk) calls onEffectRemoved BEFORE removing its iterator entry.
        // Recheck after the call; a cancelled Remove must not destroy the still-active level.
        for (LivingEntity entity : REMOVAL_CHECKS.keySet().toArray(LivingEntity[]::new)) {
            REMOVAL_CHECKS.remove(entity);
            if (entity.level().getServer() == event.getServer()) reconcile(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void feed(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid) || !maid.isAlive() || !isOrdinaryMaid(maid)
                || !maid.isOwnedBy(event.getEntity()) || event.getEntity().isSpectator()
                || !isCustomPotion(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Feed last = LAST_FEED.get(player);
        long now = player.level().getGameTime();
        if (last != null && last.tick == now && last.maid.equals(maid.getUUID())) return;
        if (!player.canReach(maid, 1) || !applyDrink(event.getItemStack(), maid)) {
            event.setCancellationResult(InteractionResult.FAIL); return;
        }
        LAST_FEED.put(player, new Feed(now, maid.getUUID()));
        player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(event.getItemStack(), player, new ItemStack(Items.GLASS_BOTTLE)));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        maid.gameEvent(GameEvent.DRINK);
        player.level().playSound(null, maid.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, .8F, 1);
    }

    private static void ownModifier(LivingEntity entity, Attribute attribute, UUID id, double amount,
                                    AttributeModifier.Operation operation, boolean wanted) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(id);
        if (!wanted) { if (current != null) instance.removeModifier(id); return; }
        if (current != null && current.getAmount() == amount && current.getOperation() == operation) return;
        if (current != null) instance.removeModifier(id);
        instance.addPermanentModifier(new AttributeModifier(id, "callresponse.potion", amount, operation));
    }
    static void maliceModifiers(LivingEntity entity, boolean wanted) {
        if (entity.level().isClientSide) return;
        ownModifier(entity, Attributes.ATTACK_DAMAGE, MALICE_ATTACK_UUID, .2, AttributeModifier.Operation.MULTIPLY_TOTAL, wanted);
        ownModifier(entity, Attributes.ATTACK_SPEED, MALICE_SPEED_UUID, .2, AttributeModifier.Operation.MULTIPLY_TOTAL, wanted);
    }
    public static void reconcile(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        boolean malice = entity instanceof EntityMaid maid && isOrdinaryMaid(maid)
                && entity.hasEffect(WineFoxEffects.CORRUPTED_HUNT.get());
        boolean sweet = canReceiveSweetness(entity) && entity.hasEffect(WineFoxEffects.SWEETNESS.get());
        maliceModifiers(entity, malice);
        ownModifier(entity, Attributes.ATTACK_DAMAGE, SWEET_ATTACK_UUID, SWEET_ATTACK_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL, sweet);
        ownModifier(entity, ForgeMod.ENTITY_REACH.get(), SWEET_REACH_UUID, 2, AttributeModifier.Operation.ADDITION, sweet);
        MobEffectInstance effect = entity.getEffect(WineFoxEffects.CORRUPTED_HUNT.get());
        if (effect == null) clearMaliceLevel(entity); else ensureMaliceLevel(entity, effect.getAmplifier());
    }

    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel)
                || !maid.isAlive() || maid.isRemoved()) return;
        Timers timers = TIMERS.computeIfAbsent(maid, key -> new Timers(key.getPersistentData().getCompound(MAID_TIMERS)));
        long now = maid.level().getGameTime();
        if (timers.lastTick == now) return;
        timers.lastTick = now;
        boolean ordinary = isOrdinaryMaid(maid), sweet = ordinary && maid.hasEffect(WineFoxEffects.SWEETNESS.get());
        boolean malice = ordinary && maid.hasEffect(WineFoxEffects.CORRUPTED_HUNT.get());
        int flags = (ordinary ? 1 : 0) | (sweet ? 2 : 0) | (malice ? 4 : 0);
        if (flags != timers.flags) { reconcile(maid); timers.flags = flags; }
        if (sweet) {
            if (HungerData.get(maid) < 15) HungerData.set(maid, 15);
            if (++timers.favor >= 600) {
                timers.favor = 0;
                // The active TLM/addon manager owns its actual ceiling; level three is not a universal cap.
                maid.getFavorabilityManager().add(1);
            }
        } else timers.favor = 0;
        if (malice) {
            if (++timers.fear >= 1200) { timers.fear = 0; EmotionData.addFear(maid, maid.getOwnerUUID(), 1); }
        } else timers.fear = 0;
    }

    /** Existing maid save hook calls this after native save; ForgeData was copied earlier by Entity. */
    public static void writeMaidState(EntityMaid maid, CompoundTag saved) {
        if (maid.level().isClientSide) return;
        Timers timers = TIMERS.get(maid);
        if (timers != null) maid.getPersistentData().put(MAID_TIMERS, timers.save());
        CompoundTag forge = saved.getCompound("ForgeData");
        if (maid.getPersistentData().contains(MAID_TIMERS)) forge.put(MAID_TIMERS, maid.getPersistentData().getCompound(MAID_TIMERS).copy());
        if (maid.getPersistentData().contains(MALICE_DATA)) forge.put(MALICE_DATA, maid.getPersistentData().getCompound(MALICE_DATA).copy());
        else forge.remove(MALICE_DATA);
        saved.put("ForgeData", forge);
    }
    public static void readMaidState(EntityMaid maid, CompoundTag saved) {
        if (maid.level().isClientSide) return;
        TIMERS.remove(maid);
        reconcile(maid);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && !event.getLevel().isClientSide()) reconcile(entity);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            Timers timers = TIMERS.remove(maid);
            if (timers != null) maid.getPersistentData().put(MAID_TIMERS, timers.save());
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { TIMERS.clear(); LAST_FEED.clear(); REMOVAL_CHECKS.clear(); }

    private static final class Timers {
        int favor, fear, flags = -1;
        long lastTick;
        Timers(CompoundTag data) {
            favor = Mth.clamp(data.getInt("FavorTicks"), 0, 599);
            fear = Mth.clamp(data.getInt("FearTicks"), 0, 1199);
            lastTick = data.contains("LastTick", Tag.TAG_LONG) ? data.getLong("LastTick") : Long.MIN_VALUE;
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("FavorTicks", favor); tag.putInt("FearTicks", fear); tag.putLong("LastTick", lastTick);
            return tag;
        }
    }
    private record Feed(long tick, UUID maid) {}
    private WineFoxPotions() {}
}
