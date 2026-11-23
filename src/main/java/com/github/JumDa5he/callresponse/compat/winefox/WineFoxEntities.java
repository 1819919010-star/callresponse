package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import java.util.function.Supplier;

@EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class WineFoxEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CallResponseMod.MOD_ID);
    public static final Supplier<EntityType<WineFoxEntity>> FOX = TYPES.register("corrupted_wine_fox",
            () -> EntityType.Builder.of(WineFoxEntity::new, MobCategory.MONSTER).fireImmune().sized(0.65F, 1.8F)
                    .clientTrackingRange(10).build("callresponse:corrupted_wine_fox"));
    public static final Supplier<EntityType<WineFoxSpike>> SPIKE = TYPES.register("wine_fox_spike",
            () -> EntityType.Builder.<WineFoxSpike>of(WineFoxSpike::new, MobCategory.MISC).sized(0.22F, 1.6F)
                    .clientTrackingRange(10).updateInterval(1).build("callresponse:wine_fox_spike"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(FOX.get(), WineFoxEntity.createAttributes().build());
    }
    private WineFoxEntities() {}
}
