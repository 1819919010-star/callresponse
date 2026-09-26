package com.github.JumDa5he.callresponse.compat.sign;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆看到别的女仆被游行示众时会被威慑。
 * <p>复用威压的状态：暂停自主 AI + 受惊表现，所以表现和玩家威压完全一致，
 * 区别只是来源不是玩家。同一主人的女仆还会因此更怕主人（立威）。
 */
public class MaidSignDeterrence {
    /** 扫描间隔，2 秒一次。 */
    private static final int SCAN_INTERVAL = 40;
    /** 能“看到”的距离。 */
    private static final double SIGHT_RADIUS = 12.0D;
    /** 单次威慑持续 3 秒；只要还在看，就会被不断续上。 */
    private static final int DETERRENCE_TICKS = 60;
    /** 中断超过 5 秒再看到，才算新的一次围观。 */
    private static final int ENCOUNTER_GAP = 100;
    /** 同一主人被“立威”时增加的恐惧。 */
    private static final int FEAR_AMOUNT = 3;
    private static final int PRUNE_INTERVAL = 1200;
    private static final long PRUNE_AGE = 24000L;

    private static final Map<UUID, Long> LAST_SEEN = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % SCAN_INTERVAL != 0L) {
            return;
        }
        if (now % PRUNE_INTERVAL == 0L) {
            LAST_SEEN.entrySet().removeIf(entry -> now - entry.getValue() > PRUNE_AGE);
        }
        for (ServerLevel level : server.getAllLevels()) {
            scanLevel(level, now);
        }
    }

    private static void scanLevel(ServerLevel level, long now) {
        List<EntityMaid> paraded = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityMaid maid && MaidSignManager.hasSign(maid)) {
                paraded.add(maid);
            }
        }
        for (EntityMaid source : paraded) {
            if (!source.isAlive() || source.isRemoved()) {
                continue;
            }
            List<EntityMaid> witnesses = level.getEntitiesOfClass(EntityMaid.class,
                    source.getBoundingBox().inflate(SIGHT_RADIUS),
                    witness -> isValidWitness(source, witness));
            for (EntityMaid witness : witnesses) {
                deter(now, source, witness);
            }
        }
    }

    private static boolean isValidWitness(EntityMaid source, EntityMaid witness) {
        return witness != source && witness.isAlive() && !witness.isRemoved()
                && !MaidSignManager.hasSign(witness)
                // 谈话事件里正在说话的女仆先不打断，事件本身有超时保护
                && !TalkEventManager.isParticipant(witness)
                && witness.distanceToSqr(source) <= SIGHT_RADIUS * SIGHT_RADIUS
                && witness.hasLineOfSight(source);
    }

    private static void deter(long now, EntityMaid source, EntityMaid witness) {
        IntimidationManager.intimidateByParade(witness, DETERRENCE_TICKS);
        UUID witnessId = witness.getUUID();
        long last = LAST_SEEN.getOrDefault(witnessId, Long.MIN_VALUE);
        LAST_SEEN.put(witnessId, now);
        // 同一次围观只反应一次，走开再回来看才会重新触发
        if (last != Long.MIN_VALUE && now - last < ENCOUNTER_GAP) {
            return;
        }
        witness.getChatBubbleManager().addTextChatBubble(
                "bubble.callresponse.maid_sign.witness." + (1 + witness.getRandom().nextInt(2)));
        UUID ownerId = witness.getOwnerUUID();
        if (ownerId != null && ownerId.equals(source.getOwnerUUID())) {
            EmotionData.addFear(witness, ownerId, FEAR_AMOUNT);
        }
    }
}
