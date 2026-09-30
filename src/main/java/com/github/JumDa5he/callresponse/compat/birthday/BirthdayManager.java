package com.github.JumDa5he.callresponse.compat.birthday;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Comparator;
import java.util.List;

/**
 * 生日庆祝的服务端逻辑。
 *
 * <p>周期性检查在线玩家：如果玩家设置过生日且今天正是那一天（当天还没庆祝过），
 * 就让附近属于自己的女仆为他庆祝；没有设置生日的玩家不会受到任何影响。</p>
 */
public final class BirthdayManager {
    /** 检查间隔，100 tick = 5 秒。 */
    private static final int CHECK_INTERVAL_TICKS = 100;
    /** 参与庆祝的女仆半径（格）。 */
    private static final double MAID_RADIUS = 64.0D;
    /** 生日礼物战利品表，保底一个蛋糕 + 随机礼物，可被数据包覆盖。 */
    public static final ResourceKey<LootTable> BIRTHDAY_GIFT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "birthday_gift"));

    private static final String[] BUBBLE_KEYS = {
            "bubble.callresponse.birthday.1",
            "bubble.callresponse.birthday.2",
            "bubble.callresponse.birthday.3",
            "bubble.callresponse.birthday.4"
    };

    /** 最近一次同步给客户端的服务端日期，用来在跨天时刷新客户端的生日判断。 */
    private LocalDate lastSyncedDate;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        BirthdayData data = BirthdayData.get(server.overworld());
        syncDateIfChanged(server, data, today);
        boolean changed = false;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MonthDay birthday = data.birthday(player.getUUID());
            if (birthday == null || !matchesToday(birthday, today)) {
                continue;
            }
            if (data.lastCelebratedYear(player.getUUID()) == today.getYear()) {
                continue;
            }
            // 先落盘标记，避免同一年重复触发。
            data.markCelebrated(player.getUUID(), today.getYear());
            changed = true;
            try {
                celebrate(player, today);
            } catch (Exception exception) {
                CallResponseMod.LOGGER.error("[生日] 庆祝流程出错", exception);
            }
        }

        if (changed) {
            data.setDirty();
        }
    }

    /** 玩家上线时补一次同步，让客户端马上就能判断自己的生日。 */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        BirthdaySyncS2CPacket.sync(player, BirthdayData.get(player.getServer().overworld()), LocalDate.now());
    }

    /** 服务端日期跨天时重新同步给所有在线玩家，避免客户端缓存过夜失效。 */
    private void syncDateIfChanged(MinecraftServer server, BirthdayData data, LocalDate today) {
        if (today.equals(lastSyncedDate)) {
            return;
        }
        lastSyncedDate = today;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BirthdaySyncS2CPacket.sync(player, data, today);
        }
    }

    /** 2 月 29 日出生者在非闰年顺延到 3 月 1 日庆祝。 */
    private static boolean matchesToday(MonthDay birthday, LocalDate today) {
        return BirthdayDates.matches(birthday.getMonthValue(), birthday.getDayOfMonth(), today);
    }

    /** 立即让玩家身边属于他的女仆庆祝。也可用于手动触发/测试。 */
    public static void celebrate(ServerPlayer player, LocalDate date) {
        ServerLevel level = player.serverLevel();
        List<EntityMaid> maids = level.getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(MAID_RADIUS),
                maid -> maid.isTame() && player.getUUID().equals(maid.getOwnerUUID()));

        sendBirthdayTitle(player);
        level.playSound(null, player.getX(), player.getY() + 0.5D, player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);

        // 礼物与身边是否有女仆无关，避免玩家恰好把女仆留在远处就收不到祝福。
        giveGift(player, level);

        if (maids.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.callresponse.birthday.no_maid",
                    date.getMonthValue(), date.getDayOfMonth()));
            return;
        }

        player.sendSystemMessage(Component.translatable("message.callresponse.birthday.count",
                date.getMonthValue(), date.getDayOfMonth(), maids.size()));
        for (EntityMaid maid : maids) {
            celebrateMaid(maid, player);
        }

        triggerGreeting(player, maids, date);
    }

    /** 按战利品表发放生日礼物：固定一份蛋糕，再随机若干小礼物。 */
    private static void giveGift(ServerPlayer player, ServerLevel level) {
        LootTable table;
        try {
            table = level.getServer().reloadableRegistries().getLootTable(BIRTHDAY_GIFT_TABLE);
        } catch (Exception exception) {
            CallResponseMod.LOGGER.error("[生日] 读取生日礼物战利品表失败", exception);
            return;
        }
        if (table == null || table == LootTable.EMPTY) {
            CallResponseMod.LOGGER.warn("[生日] 战利品表 {} 缺失，本次不发放礼物",
                    BIRTHDAY_GIFT_TABLE.location());
            return;
        }

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .create(LootContextParamSets.ADVANCEMENT_REWARD);
        List<ItemStack> gifts = table.getRandomItems(params);
        if (gifts.isEmpty()) {
            return;
        }
        for (ItemStack gift : gifts) {
            // 先进背包，装不下才掉在脚边
            ItemHandlerHelper.giveItemToPlayer(player, gift);
        }
        player.sendSystemMessage(Component.translatable("message.callresponse.birthday.gift", gifts.size()));
        level.playSound(null, player.getX(), player.getY() + 0.5D, player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static void sendBirthdayTitle(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("message.callresponse.birthday.title")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("message.callresponse.birthday.subtitle", player.getName())));
    }

    private static void celebrateMaid(EntityMaid maid, ServerPlayer player) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        double x = maid.getX();
        double y = maid.getY();
        double z = maid.getZ();
        double top = y + maid.getBbHeight();

        level.sendParticles(ParticleTypes.HEART, x, top + 0.2D, z,
                6, 0.4D, 0.25D, 0.4D, 0.01D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + maid.getBbHeight() * 0.6D, z,
                12, 0.5D, 0.4D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.FIREWORK, x, top + 0.6D, z,
                10, 0.3D, 0.3D, 0.3D, 0.05D);

        maid.playSound(SoundEvents.PLAYER_LEVELUP, 0.8F, 1.35F);
        // 让女仆轻快地跳一下，做出庆祝动作。
        maid.setDeltaMovement(maid.getDeltaMovement().x, 0.42D, maid.getDeltaMovement().z);
        maid.hasImpulse = true;

        String key = BUBBLE_KEYS[maid.getRandom().nextInt(BUBBLE_KEYS.length)];
        maid.getChatBubbleManager().addChatBubble(
                TextChatBubbleData.type2(Component.translatable(key, player.getName())));
    }

    /**
     * 让最近的一只女仆真的开口说祝福语：AI 可用时提示词由 AI 撰写，
     * 否则退回内置提示词；AI 完全不可用时保持上面的气泡与粒子庆祝。
     */
    private static void triggerGreeting(ServerPlayer player, List<EntityMaid> maids, LocalDate date) {
        EntityMaid nearest = maids.stream()
                .min(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
                .orElse(null);
        if (nearest == null) {
            return;
        }
        try {
            BirthdayGreetingPrompter.greet(player, nearest, date);
        } catch (Exception exception) {
            CallResponseMod.LOGGER.error("[生日] 触发 AI 祝福失败", exception);
        }
    }
}
