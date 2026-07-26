package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionForgettingManager {

    // ===== 配置参数 =====
    private static final int TRUST_THRESHOLD = 10;
    private static final int FEAR_THRESHOLD = 10;
    private static final int DURATION_THRESHOLD = 200;       // 10 秒
    private static final int POST_DIALOGUE_WAIT = 200;       // 对话后 10 秒

    // ===== NBT 键名 =====
    private static final String KEY_FORGET_TIMER = "forgetTimer";
    private static final String KEY_FORGET_COUNTDOWN = "forgetCountdown";
    private static final String KEY_FORGET_TRIGGERED = "forgetTriggered";

    // ===== 内存缓存 =====
    private static final ConcurrentHashMap<UUID, Boolean> pendingTeleport = new ConcurrentHashMap<>();

    // ===== 定时检查世界中的女仆 =====
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        if (!maid.isTame() || maid.getOwner() == null) return;
                        processMaid(maid, player);
                    });
        }
    }

    // ===== 处理单个女仆 =====
    private void processMaid(EntityMaid maid, ServerPlayer player) {
        UUID maidId = maid.getUUID();
        CompoundTag data = maid.getPersistentData();

        EmotionData.EmotionValues values = EmotionData.get(maid, player);
        int trust = values.trust();
        int fear = values.fear();
        boolean inForgetCondition = (trust <= TRUST_THRESHOLD && fear <= FEAR_THRESHOLD);

        if (!inForgetCondition) {
            if (data.contains(KEY_FORGET_TIMER) || data.contains(KEY_FORGET_COUNTDOWN) || data.getBoolean(KEY_FORGET_TRIGGERED)) {
                data.remove(KEY_FORGET_TIMER);
                data.remove(KEY_FORGET_COUNTDOWN);
                data.remove(KEY_FORGET_TRIGGERED);
                pendingTeleport.remove(maidId);
            }
            return;
        }

        if (data.contains(KEY_FORGET_COUNTDOWN)) {
            int remaining = data.getInt(KEY_FORGET_COUNTDOWN) - 1;
            if (remaining <= 0) {
                performForgetting(maid);
                data.remove(KEY_FORGET_COUNTDOWN);
                data.remove(KEY_FORGET_TRIGGERED);
                data.remove(KEY_FORGET_TIMER);
                pendingTeleport.remove(maidId);
            } else {
                data.putInt(KEY_FORGET_COUNTDOWN, remaining);
            }
            return;
        }

        if (data.getBoolean(KEY_FORGET_TRIGGERED)) {
            return;
        }

        int timer = data.getInt(KEY_FORGET_TIMER) + 1;
        data.putInt(KEY_FORGET_TIMER, timer);

        if (timer >= DURATION_THRESHOLD) {
            triggerForgetting(maid, player);
        }
    }

    // ===== 触发淡忘 =====
    private void triggerForgetting(EntityMaid maid, ServerPlayer player) {
        UUID maidId = maid.getUUID();
        CompoundTag data = maid.getPersistentData();

        if (data.getBoolean(KEY_FORGET_TRIGGERED)) return;
        if (data.getInt(KEY_FORGET_TIMER) < DURATION_THRESHOLD) return;

        data.remove(KEY_FORGET_TIMER);
        data.putBoolean(KEY_FORGET_TRIGGERED, true);

        // ★ 使用女仆本名
        String maidName = maid.getName().getString();

        ServerLevel targetLevel = (ServerLevel) player.level();
        ServerLevel currentLevel = (ServerLevel) maid.level();

        EntityMaid finalMaid = maid;
        if (!currentLevel.dimension().equals(targetLevel.dimension())) {
            CompoundTag nbt = new CompoundTag();
            maid.saveWithoutId(nbt);
            maid.discard();
            EntityMaid newMaid = InitEntities.MAID.get().create(targetLevel);
            if (newMaid != null) {
                newMaid.load(nbt);
                Vec3 pos = player.position();
                newMaid.setPos(pos.x, pos.y, pos.z);
                newMaid.setYRot(player.getYRot());
                newMaid.setXRot(player.getXRot());
                targetLevel.addFreshEntity(newMaid);
                finalMaid = newMaid;
            } else {
                data.remove(KEY_FORGET_TRIGGERED);
                return;
            }
        } else {
            Vec3 pos = player.position();
            maid.setPos(pos.x, pos.y, pos.z);
            maid.setYRot(player.getYRot());
            maid.setXRot(player.getXRot());
        }

        if (finalMaid.isInSittingPose()) {
            finalMaid.setInSittingPose(false);
        }

        finalMaid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        finalMaid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        finalMaid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

        player.sendSystemMessage(Component.literal(
                "§e" + maidName + " 突然出现在你面前，眼中带着一种说不清的平静..."
        ));

        String prompt = "你被主人遗忘了太久，久到信任消散了，恐惧也磨平了。你不再愤怒，也不再害怕，心里只剩下一种空空荡荡的平静。曾经你是那么在意主人，在意到每一分情绪的起落都是因为他——但时间真的能冲淡一切。现在你看着主人，感觉像是在看一个熟悉的陌生人。请用一段平静而释然的话，约30字左右，告诉主人你曾经多么快乐，但你受不了永远在等待的生活。没有眼泪，没有控诉，只有淡淡的释然和一句安静的告别。就像秋叶落地那样自然。";
        MaidResponder.processBroadcast(player, Collections.singletonList(finalMaid), prompt, false);

        finalMaid.getPersistentData().putInt(KEY_FORGET_COUNTDOWN, POST_DIALOGUE_WAIT);

        MaidResponder.debug(player, "§e[淡忘] " + maidName + " 将在 10 秒后消失...");
    }

    // ===== 执行淡忘（女仆消失） =====
    private void performForgetting(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        Vec3 pos = maid.position();
        Level level = maid.level();
        // ★ 使用女仆本名
        String maidName = maid.getName().getString();

        ItemStack cake = new ItemStack(Items.CAKE);
        ItemEntity itemEntity = new ItemEntity(
                level,
                pos.x,
                pos.y + 0.5,
                pos.z,
                cake
        );
        itemEntity.setDeltaMovement(
                (maid.getRandom().nextDouble() - 0.5) * 0.1,
                0.1,
                (maid.getRandom().nextDouble() - 0.5) * 0.1
        );
        level.addFreshEntity(itemEntity);

        maid.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.5f, 1.0f);
        maid.discard();

        pendingTeleport.remove(maidId);

        if (maid.getOwner() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal(
                    "§c" + maidName + " 消失了... 只留下了一块蛋糕。"
            ));
        }
    }

    // ===== 重置（调试用） =====
    public static void resetForgetting(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.remove(KEY_FORGET_TIMER);
        data.remove(KEY_FORGET_COUNTDOWN);
        data.remove(KEY_FORGET_TRIGGERED);
        pendingTeleport.remove(maid.getUUID());
    }
}