package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntities;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxSpawnStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.Optional;

/** Keeps vanilla egg behavior while carrying only this synchronous use's actual player. */
public final class WineFoxSpawnEggItem extends DeferredSpawnEggItem {
    public WineFoxSpawnEggItem() { super(WineFoxEntities.FOX, 0x291233, 0xC99036, new Properties()); }

    @Override public InteractionResult useOn(UseOnContext context) {
        try (var ignored = WineFoxSpawnStats.eggSource(context.getPlayer())) { return super.useOn(context); }
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        try (var ignored = WineFoxSpawnStats.eggSource(player)) { return super.use(level, player, hand); }
    }
    @Override public Optional<Mob> spawnOffspringFromSpawnEgg(Player player, Mob parent,
            EntityType<? extends Mob> type, ServerLevel level, Vec3 pos, ItemStack stack) {
        try (var ignored = WineFoxSpawnStats.eggSource(player)) {
            return super.spawnOffspringFromSpawnEgg(player, parent, type, level, pos, stack);
        }
    }
}
