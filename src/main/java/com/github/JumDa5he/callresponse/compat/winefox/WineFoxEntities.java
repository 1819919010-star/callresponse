package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WineFoxEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CallResponseMod.MOD_ID);
    public static final RegistryObject<EntityType<WineFoxEntity>> FOX = TYPES.register("corrupted_wine_fox",
            () -> EntityType.Builder.of(WineFoxEntity::new, MobCategory.MONSTER).fireImmune().sized(0.65F, 1.8F)
                    .clientTrackingRange(10).build("callresponse:corrupted_wine_fox"));
    public static final RegistryObject<EntityType<WineFoxSpike>> SPIKE = TYPES.register("wine_fox_spike",
            () -> EntityType.Builder.<WineFoxSpike>of(WineFoxSpike::new, MobCategory.MISC).sized(0.22F, 1.6F)
                    .clientTrackingRange(10).updateInterval(1).build("callresponse:wine_fox_spike"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(FOX.get(), WineFoxEntity.createAttributes().build());
    }
    private WineFoxEntities() {}
}
