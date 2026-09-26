package com.github.JumDa5he.callresponse.compat.disguise.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.disguise.DisguiseAppearance;
import com.github.JumDa5he.callresponse.compat.disguise.DisguiseSyncPacket;
import com.github.tartaricacid.touhoulittlemaid.client.resource.models.MaidModels;
import com.github.tartaricacid.touhoulittlemaid.compat.ysm.YsmCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Draws an appearance-only proxy. The actual player entity and all gameplay data stay untouched. */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT)
public final class ClientDisguiseState {
    private static final Map<UUID, ClientEntry> ACTIVE = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private ClientDisguiseState() {}

    public static void accept(DisguiseSyncPacket packet) {
        if (packet.appearance() == null || packet.remainingTicks() <= 0) {
            ACTIVE.remove(packet.playerId());
        } else {
            ClientEntry previous = ACTIVE.get(packet.playerId());
            ClientEntry next = new ClientEntry(packet.appearance(), packet.remainingTicks(), packet.recognitionTicks());
            if (previous != null && previous.appearance.equals(next.appearance)) next.proxy = previous.proxy;
            ACTIVE.put(packet.playerId(), next);
        }
    }

    public static void appendTooltip(List<Component> lines) {
        Player self = Minecraft.getInstance().player;
        if (self == null) return;
        ClientEntry entry = ACTIVE.get(self.getUUID());
        if (entry == null) return;
        lines.add(Component.translatable("tooltip.callresponse.disguise.remaining", formatTime(entry.remaining)));
        if (entry.recognition > 0) {
            lines.add(Component.translatable("tooltip.callresponse.disguise.recognition_remaining",
                    formatTime(entry.recognition)));
        }
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        for (ClientEntry entry : ACTIVE.values()) {
            if (entry.remaining > 0) entry.remaining--;
            if (entry.recognition > 0) entry.recognition--;
        }
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().remaining <= 0);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        ClientEntry entry = ACTIVE.get(player.getUUID());
        if (entry == null || player.isSpectator() || !renderable(entry.appearance)) return;
        try {
            if (entry.proxy == null || entry.proxy.level() != player.level()) {
                entry.proxy = createProxy(player, entry.appearance);
            }
            copyRenderState(player, entry);
            EntityRenderer<? super EntityMaid> renderer = Minecraft.getInstance()
                    .getEntityRenderDispatcher().getRenderer(entry.proxy);
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            try {
                float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
                renderer.render(entry.proxy, bodyYaw, event.getPartialTick(), pose,
                        event.getMultiBufferSource(), event.getPackedLight());
            } finally {
                pose.popPose();
            }
            // HIGHEST runs before revivemaid's ordinary renderer, so only one player model is drawn.
            event.setCanceled(true);
        } catch (Throwable error) {
            if (WARNED.add(entry.appearance.modelId())) {
                CallResponseMod.LOGGER.warn("Disguise renderer unavailable; leaving the normal player renderer active", error);
            }
            entry.proxy = null;
        }
    }

    private static boolean renderable(DisguiseAppearance appearance) {
        if (appearance.ysmModel()) return YsmCompat.isInstalled() && !appearance.ysmModelId().isBlank();
        return MaidModels.getInstance().containsInfo(appearance.modelId());
    }

    private static EntityMaid createProxy(Player player, DisguiseAppearance appearance) {
        EntityMaid maid = new EntityMaid(player.level());
        maid.setModelId(appearance.modelId());
        if (appearance.ysmModel()) {
            maid.setYsmModel(appearance.ysmModelId(), appearance.ysmTexture(), appearance.ysmName());
            maid.setIsYsmModel(true);
        } else {
            maid.setIsYsmModel(false);
        }
        return maid;
    }

    private static void copyRenderState(Player player, ClientEntry entry) {
        EntityMaid maid = entry.proxy;
        maid.setPos(player.getX(), player.getY(), player.getZ());
        maid.xOld = player.xOld;
        maid.yOld = player.yOld;
        maid.zOld = player.zOld;
        maid.tickCount = player.tickCount;
        maid.setYRot(player.getYRot());
        maid.setXRot(player.getXRot());
        maid.yRotO = player.yRotO;
        maid.xRotO = player.xRotO;
        maid.yBodyRot = player.yBodyRot;
        maid.yBodyRotO = player.yBodyRotO;
        maid.yHeadRot = player.yHeadRot;
        maid.yHeadRotO = player.yHeadRotO;
        maid.swinging = player.swinging;
        maid.swingingArm = player.swingingArm;
        maid.swingTime = player.swingTime;
        maid.attackAnim = player.attackAnim;
        maid.oAttackAnim = player.oAttackAnim;
        maid.hurtTime = player.hurtTime;
        maid.hurtDuration = player.hurtDuration;
        maid.setDeltaMovement(player.getDeltaMovement());
        maid.setShiftKeyDown(player.isShiftKeyDown());
        maid.setSprinting(player.isSprinting());
        maid.setSwimming(player.isSwimming());
        maid.setInvisible(player.isInvisible());
        maid.setOnGround(player.onGround());
        maid.setLeftHanded(player.getMainArm() == HumanoidArm.LEFT);
        maid.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem());
        maid.setItemInHand(InteractionHand.OFF_HAND, player.getOffhandItem());
        if (player.isUsingItem()) maid.startUsingItem(player.getUsedItemHand());
        else maid.stopUsingItem();
        if (entry.lastAnimationTick != player.tickCount) {
            int ticks = Math.max(1, Math.min(4, player.tickCount - entry.lastAnimationTick));
            for (int i = 0; i < ticks; i++) maid.walkAnimation.update(player.walkAnimation.speed(), 1.0F);
            entry.lastAnimationTick = player.tickCount;
        }
        maid.setCustomName(player.getDisplayName());
        maid.setCustomNameVisible(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        WARNED.clear();
    }

    private static final class ClientEntry {
        private final DisguiseAppearance appearance;
        private int remaining;
        private int recognition;
        private EntityMaid proxy;
        private int lastAnimationTick;

        private ClientEntry(DisguiseAppearance appearance, int remaining, int recognition) {
            this.appearance = appearance;
            this.remaining = remaining;
            this.recognition = recognition;
        }
    }
}
