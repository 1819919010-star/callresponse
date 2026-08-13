package com.github.JumDa5he.callresponse.compat.menu;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CallResponseMod.MOD_ID);

    public static final Supplier<MenuType<MaidStatusContainer>> MAID_STATUS = MENUS.register(
            "maid_status",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) ->
                    new MaidStatusContainer(windowId, inventory, extraData.readInt())));

    private ModMenus() {
    }
}
