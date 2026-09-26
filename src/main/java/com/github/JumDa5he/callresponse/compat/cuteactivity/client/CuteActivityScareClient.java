package com.github.JumDa5he.callresponse.compat.cuteactivity.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityScareS2CPacket;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 只增加 callresponse 的 scare 来源，不改合作模组的受击计时。 */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT)
public final class CuteActivityScareClient {
    private static final Map<UUID, Long> INTIMIDATION_END = new HashMap<>();
    private static final Set<UUID> REVENGE_MAIDS = new HashSet<>();
    private static ClientLevel currentLevel;
    private static long clientTicks;
    private static Method animBookIsExcluded;
    private static boolean animBookResolved;
    private static boolean animBookFailed;

    private CuteActivityScareClient() {}

    public static void accept(CuteActivityScareS2CPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        switchLevel(level);
        if (packet.revengeMaid()) {
            REVENGE_MAIDS.add(packet.maidId());
            INTIMIDATION_END.remove(packet.maidId());
        } else {
            REVENGE_MAIDS.remove(packet.maidId());
            if (packet.remainingTicks() > 0)
                INTIMIDATION_END.put(packet.maidId(), clientTicks + packet.remainingTicks());
            else INTIMIDATION_END.remove(packet.maidId());
        }
    }

    public static boolean isRevenge(EntityMaid maid) {
        switchLevel(Minecraft.getInstance().level);
        return BetrayalOutpostMaidData.isOutpostMaid(maid) || REVENGE_MAIDS.contains(maid.getUUID());
    }

    public static boolean isIntimidationScareActive(EntityMaid maid) {
        switchLevel(Minecraft.getInstance().level);
        if (isRevenge(maid) || !maid.isAlive() || maid.isRemoved()) return false;
        Long end = INTIMIDATION_END.get(maid.getUUID());
        if (end == null) return false;
        if (clientTicks >= end) {
            INTIMIDATION_END.remove(maid.getUUID());
            return false;
        }
        return isModelAllowed(maid);
    }

    private static boolean isModelAllowed(EntityMaid maid) {
        if (!animBookResolved) {
            animBookResolved = true;
            try {
                Class<?> book = Class.forName("cn.autoforged.maid_cute_activity.AnimBookData");
                animBookIsExcluded = book.getMethod("isExcluded", EntityMaid.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                warnAnimBook(ex);
            }
        }
        if (animBookIsExcluded == null) return false;
        try {
            return !Boolean.TRUE.equals(animBookIsExcluded.invoke(null, maid));
        } catch (ReflectiveOperationException | LinkageError ex) {
            warnAnimBook(ex);
            animBookIsExcluded = null;
            return false;
        }
    }

    private static void warnAnimBook(Throwable ex) {
        if (animBookFailed) return;
        animBookFailed = true;
        CallResponseMod.LOGGER.warn("Cute Activity animation-book API differs; intimidation scare visuals disabled", ex);
    }

    private static void switchLevel(ClientLevel level) {
        if (level == currentLevel) return;
        currentLevel = level;
        clientTicks = 0L;
        INTIMIDATION_END.clear();
        REVENGE_MAIDS.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        switchLevel(level);
        if (level == null || Minecraft.getInstance().isPaused()) return;
        clientTicks++;
        if (clientTicks % 100 == 0) INTIMIDATION_END.entrySet().removeIf(entry -> entry.getValue() <= clientTicks);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        switchLevel(null);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityMaid maid)) return;
        INTIMIDATION_END.remove(maid.getUUID());
        REVENGE_MAIDS.remove(maid.getUUID());
    }
}
