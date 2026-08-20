package com.github.JumDa5he.callresponse.compat.menu;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CallResponseMod.MOD_ID);

    public static final RegistryObject<MenuType<MaidStatusContainer>> MAID_STATUS = MENUS.register(
            "maid_status",
            () -> IForgeMenuType.create((windowId, inventory, data) ->
                    new MaidStatusContainer(windowId, inventory, data.readInt())));
    public static final RegistryObject<MenuType<RewardBoxMenu>> REWARD_BOX = MENUS.register(
            "reward_box", () -> IForgeMenuType.create((windowId, inventory, data) ->
                    new RewardBoxMenu(windowId, inventory)));

    private ModMenus() {
    }
}
