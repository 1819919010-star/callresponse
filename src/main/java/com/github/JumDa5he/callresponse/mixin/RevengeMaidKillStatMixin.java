package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replace ONLY the key of vanilla's one confirmed entity-kill statistic. */
@Mixin(Player.class)
public abstract class RevengeMaidKillStatMixin {
    @Redirect(method = "killedEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getType()Lnet/minecraft/world/entity/EntityType;"))
    private EntityType<?> callresponse$maidStatistic(LivingEntity killed) {
        return killed instanceof RevengeMaidEntity ? EntityMaid.TYPE : killed.getType();
    }
}
