package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.JumDa5he.callresponse.compat.bauble.BaubleDetector;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class NoEatAwareMaidMeal implements IMaidMeal {
    private final IMaidMeal delegate;

    public NoEatAwareMaidMeal(IMaidMeal delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean canMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand) {
        return !BaubleDetector.hasNoEat(maid) && delegate.canMaidEat(maid, stack, hand);
    }

    @Override
    public void onMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand) {
        delegate.onMaidEat(maid, stack, hand);
    }
}
