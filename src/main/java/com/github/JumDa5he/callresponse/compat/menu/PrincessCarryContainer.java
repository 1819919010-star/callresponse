package com.github.JumDa5he.callresponse.compat.menu;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.world.entity.player.Inventory;

public final class PrincessCarryContainer extends TaskConfigContainer {
    public PrincessCarryContainer(int id, Inventory inventory, int entityId) {
        super(ModMenus.PRINCESS_CARRY.get(), id, inventory, entityId);
    }
}
