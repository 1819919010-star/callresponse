package com.github.JumDa5he.callresponse.compat.outpost.entity;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import java.util.function.Supplier;

@EventBusSubscriber(modid = CallResponseMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class OutpostEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CallResponseMod.MOD_ID);
    public static final Supplier<EntityType<RevengeMaidEntity>> REVENGE_MAID = TYPES.register("revenge_maid",
            () -> EntityType.Builder.of(RevengeMaidEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.5F).clientTrackingRange(10).build("callresponse:revenge_maid"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(REVENGE_MAID.get(), EntityMaid.createAttributes().build());
    }
    private OutpostEntities() {}
}
