package com.github.tartaricacid.callresponse.compat.task;

import com.github.tartaricacid.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LazyMaidTask implements IMaidTask {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "lazy");
    private static final ItemStack ICON = BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("touhou_little_maid:maid_bed")
    ).getDefaultInstance();

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return ICON;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return List.of();
    }
}
