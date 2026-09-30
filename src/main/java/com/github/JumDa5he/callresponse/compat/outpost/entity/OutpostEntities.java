package com.github.JumDa5he.callresponse.compat.outpost.entity;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OutpostEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CallResponseMod.MOD_ID);
    public static final RegistryObject<EntityType<RevengeMaidEntity>> REVENGE_MAID = TYPES.register("revenge_maid",
            () -> EntityType.Builder.of(RevengeMaidEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.5F).clientTrackingRange(10).build("callresponse:revenge_maid"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(REVENGE_MAID.get(), EntityMaid.createAttributes().build());
    }
    private OutpostEntities() {}
}
