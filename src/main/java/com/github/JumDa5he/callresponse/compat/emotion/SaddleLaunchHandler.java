package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.api.event.saddle.SaddleEvent;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SaddleLaunchHandler {
    private static final double LAUNCH_POWER = 1;
    private static final int LAUNCH_DIALOGUE_COOLDOWN = 12000;
    private static final int WITNESS_RADIUS = 5;

    private static final Map<UUID, Long> lastDialogueTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastPickupTrustTime = new ConcurrentHashMap<>();

    private static final Set<UUID> blockClickedPlayers = new HashSet<>();

    private static final Set<UUID> launchedMaidIds = new HashSet<>();
    private static final Set<UUID> flyingMaidIds = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.getFirstPassenger() instanceof EntityMaid && event.getItemStack().is(Items.SADDLE)) {
            blockClickedPlayers.add(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInteractMaid(InteractMaidEvent event) {
        if (!event.getStack().is(Items.SADDLE)) return;
        if (!event.isCanceled()) return;
        Player player = event.getPlayer();
        if (!(player.getFirstPassenger() instanceof EntityMaid maid)) return;
        maid.setHomeModeEnable(true);
        maid.setInSittingPose(true);
        launchedMaidIds.add(maid.getUUID());
        if (player instanceof ServerPlayer serverPlayer) {
            UUID maidId = maid.getUUID();
            long now = maid.level().getGameTime();
            Long last = lastPickupTrustTime.get(maidId);
            if (last == null || now - last >= 12000) {
                EmotionData.addTrust(maid, serverPlayer.getUUID(), 1);
                lastPickupTrustTime.put(maidId, now);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.getFirstPassenger() instanceof EntityMaid maid)) return;
        if (!event.getItemStack().is(Items.SADDLE)) return;

        UUID pid = player.getUUID();
        boolean blockClick = blockClickedPlayers.remove(pid);
        if (blockClick) return;

        event.setCanceled(true);

        dropMaid(maid, player, 0);
    }

    public static void dropMaid(EntityMaid maid, Player player, float chargePercent){
        if(NeoForge.EVENT_BUS.post(new SaddleEvent.Launch.Pre(player, maid, chargePercent)).isCanceled())return;
        maid.stopRiding();
        maid.setPos(player.getX(), player.getY() + 0.5, player.getZ());

        Vec3 look = player.getLookAngle();
        final double power = LAUNCH_POWER + LAUNCH_POWER * chargePercent * 2;
        maid.setDeltaMovement(look.x * power, look.y * power + 0.8, look.z * power);
        if(NeoForge.EVENT_BUS.post(new SaddleEvent.Launch.AfterPush(player, maid, chargePercent)).isCanceled())return;

        maid.hurtMarked = true;
        maid.hasImpulse = true;

        maid.setHomeModeEnable(true);
        maid.setInSittingPose(true);
        launchedMaidIds.add(maid.getUUID());

        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!maid.isTame() || !player.getUUID().equals(maid.getOwnerUUID())) return;

        EmotionData.addFear(maid, player.getUUID(), 1);

        UUID maidId = maid.getUUID();
        long now = maid.level().getGameTime();

        Long lastTime = lastDialogueTime.get(maidId);
        if (lastTime != null && now - lastTime < LAUNCH_DIALOGUE_COOLDOWN) return;
        lastDialogueTime.put(maidId, now);

        String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
        String prompt = "主人把你像炮弹一样扔了出去！你在空中飞着。" + tendencyDesc + " 根据你当前的情感状态，说一段15字左右的话表达被主人扔出去的感受。";
        MaidResponder.processBroadcast(serverPlayer, Collections.singletonList(maid), prompt, false);

        AABB box = player.getBoundingBox().inflate(WITNESS_RADIUS);
        List<EntityMaid> witnesses = maid.level().getEntitiesOfClass(EntityMaid.class, box,
                m -> m != maid && m.isAlive() && m.isTame() && m.getOwner() != null);
        for (EntityMaid witness : witnesses) {
            UUID witnessId = witness.getUUID();
            Long witnessLast = lastDialogueTime.get(witnessId);
            if (witnessLast != null && now - witnessLast < LAUNCH_DIALOGUE_COOLDOWN) continue;
            lastDialogueTime.put(witnessId, now);

            String witnessTendency = EmotionData.getTendencyPromptSuffix(witness, witness.getOwnerUUID());
            String witnessName = maid.getCustomName() != null ? maid.getCustomName().getString() : "对方";
            String witnessPrompt = "你看到主人把" + witnessName + "扔了出去！" + witnessTendency + " 根据你当前的情感状态，说一段15字左右的话表达你看到这一幕的心情。";
            if (witness.getOwner() instanceof ServerPlayer witnessOwner) {
                MaidResponder.processBroadcast(witnessOwner, Collections.singletonList(witness), witnessPrompt, false);
            }
        }
        NeoForge.EVENT_BUS.post(new SaddleEvent.Launch.Post(player, maid, chargePercent));
    }

    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        UUID uuid = maid.getUUID();
        if (launchedMaidIds.remove(uuid)) {
            flyingMaidIds.add(uuid);
            return;
        }
        if (flyingMaidIds.contains(uuid) && !maid.isPassenger() &&
            (maid.onGround() || maid.isInWater() || maid.isInLava())) {
            maid.setHomeModeEnable(false);
            maid.setInSittingPose(false);
            flyingMaidIds.remove(uuid);
        }
    }
}