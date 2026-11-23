package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntity;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxSpawnStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Captures the command's actual execution entity, never a nearby player or global command state. */
@Mixin(SummonCommand.class)
public abstract class WineFoxSummonStatsMixin {
    @Redirect(method = "createEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean callresponse$initializeWineFox(ServerLevel level, Entity entity,
            CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomize) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer server ? server : null;
        entity.getSelfAndPassengers().filter(WineFoxEntity.class::isInstance)
                .map(WineFoxEntity.class::cast).forEach(fox -> WineFoxSpawnStats.initializeSpawn(fox, player));
        return level.tryAddFreshEntityWithPassengers(entity);
    }
}
