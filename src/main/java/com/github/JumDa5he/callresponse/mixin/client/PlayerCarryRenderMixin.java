package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.playercarry.client.PlayerCarryRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps the real entity and its chosen renderer; only scopes a pose-stack transform. */
@Mixin(EntityRenderDispatcher.class)
public abstract class PlayerCarryRenderMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private <T extends Entity> void callresponse$heldPlayerPose(EntityRenderer<T> renderer, T entity,
            float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!(entity instanceof Player player) || !PlayerCarryRender.fallback(player)) {
            renderer.render(entity, yaw, partialTick, pose, buffers, light);
            return;
        }
        pose.pushPose();
        try {
            PlayerCarryRender.transform(player, pose, partialTick);
            renderer.render(entity, yaw, partialTick, pose, buffers, light);
        } finally { pose.popPose(); }
    }
}
