package com.github.JumDa5he.callresponse.compat.gift;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆送礼：玩家每天起床时，身边的女仆会走到玩家面前把礼物盒交给他；
 * 玩家可以捡起来拆开，也可以拿着盒子对女仆使用来退回。
 */
public final class MaidGiftManager {
    /** 起床时能收到礼物的距离 */
    private static final int WAKE_RADIUS = 16;
    /** 低于这个饥饿值女仆就没心思准备礼物（只挡真正饿坏的，别影响测试） */
    private static final float MIN_HUNGER = 20.0F;
    /** 同一女仆两次送礼之间至少要隔的游戏刻，用来防止同一次起床被重复触发（睡觉本身就要 100 刻以上） */
    private static final long GIFT_COOLDOWN_TICKS = 40L;
    /** 地上的礼物盒多久没人捡就收回（4 分钟，早于物品实体的 5 分钟消失） */
    private static final long GIFT_EXPIRE_TICKS = 4800L;

    /** 走到离玩家多近就把盒子递出去（2 格） */
    private static final double DELIVER_DISTANCE_SQR = 4.0D;
    /** 走不过去的兜底：离这么近就直接塞给玩家（10 格） */
    private static final double DELIVER_FALLBACK_SQR = 100.0D;
    /** 最多走多久，超过就放弃（20 秒） */
    private static final long DELIVERY_TIMEOUT_TICKS = 400L;
    private static final double WALK_SPEED = 0.7D;

    private static final Map<UUID, PendingGift> PENDING_GIFTS = new HashMap<>();
    private static final List<DeliveryJob> DELIVERIES = new ArrayList<>();
    private static int tickCounter;

    private record PendingGift(ItemEntity entity, EntityMaid maid, long expireAt) {
    }

    private record DeliveryJob(EntityMaid maid, ServerPlayer player, long deadline) {
    }

    // ===== 起床送礼 =====

    @SubscribeEvent
    public void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        List<EntityMaid> maids = level.getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(WAKE_RADIUS),
                maid -> maid.isAlive() && maid.isTame() && player.getUUID().equals(maid.getOwnerUUID()));
        CallResponseMod.LOGGER.info("[礼物] 起床事件：{}，附近可送礼女仆 {} 只，游戏刻 {}",
                player.getName().getString(), maids.size(), now);

        for (EntityMaid maid : maids) {
            long last = maid.getData(InitAttachTypes.MAID_LAST_GIFT_TICK);
            if (last >= 0 && now - last < GIFT_COOLDOWN_TICKS) {
                CallResponseMod.LOGGER.debug("[礼物] {} 上次送礼在 {} 刻，本次跳过", maid.getUUID(), last);
                continue;
            }
            float hunger = HungerData.get(maid);
            if (hunger < MIN_HUNGER) {
                CallResponseMod.LOGGER.debug("[礼物] {} 饥饿值 {} 太低，本次跳过", maid.getUUID(), hunger);
                continue;
            }
            maid.setData(InitAttachTypes.MAID_LAST_GIFT_TICK, now);
            startDelivery(maid, player, level);
        }
    }

    /** 让女仆先走到玩家面前再掏盒子。 */
    private static void startDelivery(EntityMaid maid, ServerPlayer player, ServerLevel level) {
        for (DeliveryJob job : DELIVERIES) {
            if (job.maid() == maid) {
                return;
            }
        }
        // 已经贴脸了就直接递；路被别的流程占着也别打架，就地给
        if (maid.distanceToSqr(player) <= DELIVER_DISTANCE_SQR || MaidMovementControl.controlsPath(maid)) {
            deliver(maid, player, level);
            return;
        }
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.GIFT_DELIVERY,
                EnumSet.of(MaidMovementControl.Field.PATH));
        DELIVERIES.add(new DeliveryJob(maid, player, level.getGameTime() + DELIVERY_TIMEOUT_TICKS));
    }

    private static void tickDeliveries(ServerLevel level) {
        if (DELIVERIES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<DeliveryJob> iterator = DELIVERIES.iterator();
        while (iterator.hasNext()) {
            DeliveryJob job = iterator.next();
            EntityMaid maid = job.maid();
            ServerPlayer player = job.player();
            if (maid.level() != level) {
                continue;
            }
            if (!maid.isAlive() || maid.isRemoved() || !player.isAlive() || player.isRemoved()
                    || player.level() != level) {
                iterator.remove();
                endControl(maid);
                continue;
            }

            double distance = maid.distanceToSqr(player);
            if (distance <= DELIVER_DISTANCE_SQR) {
                iterator.remove();
                endControl(maid);
                deliver(maid, player, level);
                continue;
            }
            if (now >= job.deadline()) {
                iterator.remove();
                endControl(maid);
                if (distance <= DELIVER_FALLBACK_SQR) {
                    deliver(maid, player, level);
                } else {
                    say(maid, "bubble.callresponse.gift.far.", 1);
                }
                continue;
            }

            maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(player, false), 0.7F, 1));
            maid.getNavigation().moveTo(player, WALK_SPEED);
            maid.getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
    }

    private static void endControl(EntityMaid maid) {
        if (maid.isAlive()) {
            MaidMovementControl.clearNavigation(maid);
            MaidMovementControl.end(maid, MaidMovementControl.Reason.GIFT_DELIVERY);
        }
    }

    private static void deliver(EntityMaid maid, ServerPlayer player, ServerLevel level) {
        GiftBoxData data = GiftFactory.roll(maid, player.getUUID());
        ItemStack box = GiftFactory.createBox(data);

        // 盒子从女仆手上出现，落到她和玩家之间
        ItemEntity entity = new ItemEntity(level,
                maid.getX(), maid.getY() + 0.6, maid.getZ(), box);
        entity.setPickUpDelay(10);
        level.addFreshEntity(entity);
        PENDING_GIFTS.put(entity.getUUID(), new PendingGift(entity, maid, level.getGameTime() + GIFT_EXPIRE_TICKS));

        level.playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 1.4F);
        String maidName = maid.getCustomName() != null ? maid.getCustomName().getString() : "女仆";
        CallResponseMod.LOGGER.info("[礼物] {} 送出了 {}（{}）", maidName, data.kind().getSerializedName(),
                entity.blockPosition());
        say(maid, "bubble.callresponse.gift.delivered.", 3);
        player.displayClientMessage(Component.translatable("message.callresponse.gift.delivered", maidName), true);
    }

    // ===== 退回礼物 =====

    @SubscribeEvent
    public void onMaidInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!stack.is(ModItems.GIFT_BOX.get())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        stack.shrink(1);

        // 恐惧侧会把盒子推回来当作“松了口气”，好感侧则是被拒绝
        EmotionData.EmotionValues values = EmotionData.get(maid, player.getUUID());
        if (values.fear() >= 50) {
            EmotionData.addFear(maid, player.getUUID(), -2);
            say(maid, "bubble.callresponse.gift.returned_relieved.", 2);
        } else {
            EmotionData.addTrust(maid, player.getUUID(), -2);
            EmotionData.addFear(maid, player.getUUID(), 1);
            say(maid, "bubble.callresponse.gift.returned.", 2);
        }

        ((ServerLevel) player.level()).playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                InitSounds.MAID_HURT.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
    }

    /** 让女仆头顶冒一句固定台词。 */
    private static void say(EntityMaid maid, String prefix, int variants) {
        maid.getChatBubbleManager().addTextChatBubble(prefix + (1 + maid.getRandom().nextInt(variants)));
    }

    // ===== 每刻：走路送货 + 陷阱延迟任务；每秒：盒子超时收回 =====

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickDeliveries(level);
            GiftTraps.tick(level);
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        expireGifts();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        PENDING_GIFTS.clear();
        DELIVERIES.clear();
        GiftTraps.clearAll();
    }

    private static void expireGifts() {
        if (PENDING_GIFTS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PendingGift>> iterator = PENDING_GIFTS.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingGift gift = iterator.next().getValue();
            ItemEntity entity = gift.entity();
            if (entity == null || !entity.isAlive()) {
                // 被捡走（或已消失），视为收下
                iterator.remove();
                continue;
            }
            if (entity.level().getGameTime() < gift.expireAt()) {
                continue;
            }
            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE,
                        entity.getX(), entity.getY() + 0.3, entity.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
            }
            entity.discard();
            iterator.remove();
            // 礼物被晾在地上没人要：女仆会有点失落
            EntityMaid maid = gift.maid();
            if (maid != null && maid.isAlive() && maid.getOwnerUUID() != null) {
                EmotionData.addTrust(maid, maid.getOwnerUUID(), -1);
                say(maid, "bubble.callresponse.gift.expired.", 2);
            }
        }
    }
}
