package com.github.JumDa5he.callresponse.compat.bauble;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MaidConflictBaubleHandler {

    private static final int CONFLICT_DAMAGE_INTERVAL = 10;
    private static final int CONFLICT_DIALOGUE_COOLDOWN = 600;
    private static final Map<UUID, Long> LAST_DIALOGUE_TICKS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onMaidTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;
        if (maid.tickCount % CONFLICT_DAMAGE_INTERVAL != 0) return;
        if (!BaubleDetector.hasNoEat(maid) || !BaubleDetector.hasMoreEat(maid)) return;

        // 无来源伤害，绕过一切保护与减免
        maid.hurt(maid.damageSources().genericKill(), 10.0F);

        // AI 对话（带冷却，避免刷爆 LLM 请求）
        long gameTime = maid.level().getGameTime();
        UUID maidId = maid.getUUID();
        if (LAST_DIALOGUE_TICKS.getOrDefault(maidId, 0L) + CONFLICT_DIALOGUE_COOLDOWN > gameTime) {
            return;
        }
        LAST_DIALOGUE_TICKS.put(maidId, gameTime);
        if (maid.getOwner() instanceof ServerPlayer player) {
            String command = "你体内又两股力量在疯狂撕扯着你，一个让你什么都不能吃，一个让你疯狂地想吃，"
                    + "你感到头晕目眩并不断受到伤害。请按照目前的情感自然发泄一下。";
            MaidResponder.processBroadcast(player, Collections.singletonList(maid), command, false);
        }
    }
}
