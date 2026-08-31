package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.api.event.saddle.SaddleEvent;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SaddlePickupHandler {
    private static final int DIALOGUE_COOLDOWN = 12000;
    private static final int WITNESS_RADIUS = 5;

    private static final Map<UUID, Long> lastDialogueTime = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onMaidMount(EntityMountEvent event) {
        if (!event.isMounting()) return;
        if (!(event.getEntityMounting() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide()) return;
        if (!maid.isTame() || maid.getOwner() == null) return;

        if (!(event.getEntityBeingMounted() instanceof ServerPlayer player)) return;
        if (!player.getUUID().equals((maid.getOwner() == null ? null : maid.getOwner().getUUID()))) return;

        if(NeoForge.EVENT_BUS.post(new SaddleEvent.Pickup(player, maid)).isCanceled()){
            event.setCanceled(true);
            return;
        }

        UUID maidId = maid.getUUID();
        long now = maid.level().getGameTime();

        Long lastTime = lastDialogueTime.get(maidId);
        if (lastTime != null && now - lastTime < DIALOGUE_COOLDOWN) return;
        lastDialogueTime.put(maidId, now);

        EmotionData.addTrust(maid, player.getUUID(), 1);

        String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
        String prompt = "主人用马鞍抱起了你！你被主人公主抱抱在怀里。" + tendencyDesc + " 根据你当前的情感状态，说几句话话表达被主人抱着的感受。";
        MaidResponder.processBroadcast(player, Collections.singletonList(maid), prompt, false);

        AABB box = player.getBoundingBox().inflate(WITNESS_RADIUS);
        List<EntityMaid> witnesses = maid.level().getEntitiesOfClass(EntityMaid.class, box,
                m -> m != maid && m.isAlive() && m.isTame() && m.getOwner() != null);
        for (EntityMaid witness : witnesses) {
            UUID witnessId = witness.getUUID();
            Long witnessLast = lastDialogueTime.get(witnessId);
            if (witnessLast != null && now - witnessLast < DIALOGUE_COOLDOWN) continue;
            lastDialogueTime.put(witnessId, now);

            String witnessTendency = EmotionData.getTendencyPromptSuffix(witness, (witness.getOwner() == null ? null : witness.getOwner().getUUID()));
            String witnessName = maid.getCustomName() != null ? maid.getCustomName().getString() : "对方的";
            String witnessPrompt = "你看到主人公主抱抱起了" + witnessName + "。" + witnessTendency + " 根据你当前的情感状态，说一段话表达你看到这一幕的心情。";
            if (witness.getOwner() instanceof ServerPlayer witnessOwner) {
                MaidResponder.processBroadcast(witnessOwner, Collections.singletonList(witness), witnessPrompt, false);
            }
        }
    }
}
