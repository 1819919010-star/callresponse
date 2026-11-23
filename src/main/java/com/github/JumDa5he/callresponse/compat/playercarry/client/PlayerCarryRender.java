package com.github.JumDa5he.callresponse.compat.playercarry.client;

import com.github.JumDa5he.callresponse.compat.playercarry.PlayerCarryManager;
import com.github.JumDa5he.callresponse.compat.client.ysm.YsmPrincessCarryBridge;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Visual-only whole-body fallback, shared by every real-player renderer. */
public final class PlayerCarryRender {
    public static boolean fallback(Player player) {
        return PlayerCarryManager.isHeld(player) && !(PlayerCarryManager.nativePlayerAnimation(player)
                && YsmPrincessCarryBridge.playerRuntimeSeen(player));
    }
    public static void transform(Player player, PoseStack pose, float partialTick) {
        if (!(player.getVehicle() instanceof EntityMaid holder)) return;
        float yaw = Mth.rotLerp(partialTick, holder.yBodyRotO, holder.yBodyRot);
        pose.translate(0, .95, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(0, -.9, 0);
    }
    private PlayerCarryRender() {}
}
