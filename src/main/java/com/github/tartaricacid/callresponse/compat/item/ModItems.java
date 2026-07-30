package com.github.tartaricacid.callresponse.compat.item;

import com.github.tartaricacid.callresponse.CallResponseMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, CallResponseMod.MOD_ID);

    public static final Supplier<Item> EMOTION_BOOK = ITEMS.register("emotion_book",
            () -> new Item(new Item.Properties().stacksTo(1)));
}
