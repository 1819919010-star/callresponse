package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.compat.outpost.entity.OutpostEntities;
import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import net.minecraft.core.dispenser.DispenseItemBehavior;
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
import net.minecraftforge.common.ForgeSpawnEggItem;
import java.util.Optional;

/** Same real entity and spawn pipeline, with an optional persistent egg-only alliance. */
public final class RevengeMaidSpawnEggItem extends ForgeSpawnEggItem {
    private final boolean allied;

    public RevengeMaidSpawnEggItem(boolean allied) {
        super(OutpostEntities.REVENGE_MAID, 0xFFFFFF, 0xFFFFFF, new Properties());
        this.allied = allied;
    }

    private ItemStack prepare(ItemStack stack) {
        if (allied) {
            var entity = stack.getOrCreateTagElement("EntityTag");
            var data = entity.getCompound("ForgeData");
            data.putBoolean(RevengeMaidEntity.ALLIED_EGG, true);
            entity.put("ForgeData", data);
        }
        return stack;
    }

    @Override public ItemStack getDefaultInstance() { return prepare(super.getDefaultInstance()); }
    @Override public InteractionResult useOn(UseOnContext context) {
        prepare(context.getItemInHand());
        return super.useOn(context);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        prepare(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }
    @Override public Optional<Mob> spawnOffspringFromSpawnEgg(Player player, Mob parent,
            EntityType<? extends Mob> type, ServerLevel level, Vec3 pos, ItemStack stack) {
        var result = super.spawnOffspringFromSpawnEgg(player, parent, type, level, pos, prepare(stack));
        if (allied) result.filter(RevengeMaidEntity.class::isInstance)
                .ifPresent(maid -> maid.getPersistentData().putBoolean(RevengeMaidEntity.ALLIED_EGG, true));
        return result;
    }
    @Override protected DispenseItemBehavior createDispenseBehavior() {
        var vanilla = super.createDispenseBehavior();
        return (source, stack) -> vanilla.dispense(source, prepare(stack));
    }
}
