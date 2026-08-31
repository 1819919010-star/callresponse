package com.github.JumDa5he.callresponse.compat.api.event.hunger;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

public abstract class MaidEatEvent extends Event{
    private final EntityMaid maid;
    private final int originHunger;
    private int hunger;

    public MaidEatEvent(EntityMaid maid, int hunger){
        this.maid = maid;
        this.originHunger = hunger;
        this.hunger = hunger;
    }

    public EntityMaid getMaid() {
        return maid;
    }

    public int getOriginHunger() {
        return originHunger;
    }

    public int getHunger() {
        return hunger;
    }

    public void setHunger(int hunger) {
        this.hunger = hunger;
    }

    public static class Item extends MaidEatEvent{
        private final ItemStack stack;
        public Item(EntityMaid maid, ItemStack stack, int hunger) {
            super(maid, hunger);
            this.stack = stack;
        }

        public ItemStack getStack() {
            return stack;
        }
    }

    public static class Block extends MaidEatEvent{
        private final BlockPos blockPos;
        private final BlockState state;
        public Block(EntityMaid maid, BlockPos pos, BlockState state, int hunger) {
            super(maid, hunger);
            blockPos = pos;
            this.state = state;
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }

        public BlockState getState() {
            return state;
        }
    }
}
