package com.github.JumDa5he.callresponse.compat.facility;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class FacilityCapacityToolItem extends Item {
    public FacilityCapacityToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        if (!FacilityCapacityManager.isSupported(level, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        int delta = context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? -1 : 1;
        int capacity = FacilityCapacityManager.adjustCapacity(level, context.getClickedPos(), delta);
        if (context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(
                    Component.translatable("message.callresponse.capacity_changed",
                            FacilityCapacityManager.getFacilityName(level, context.getClickedPos()), capacity));
        }
        return InteractionResult.SUCCESS;
    }
}
