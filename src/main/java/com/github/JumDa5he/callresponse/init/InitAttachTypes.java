package com.github.JumDa5he.callresponse.init;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

// AttachType支持自动同步到客户端，无需手动同步
public class InitAttachTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CallResponseMod.MOD_ID);
    public static final Supplier<AttachmentType<Float>> SYNCED_HUNGER = ATTACHMENT_TYPES.register("hunger", r ->
            AttachmentType.builder(h -> HungerData.DEFAULT_HUNGER)
            .serialize(Codec.FLOAT.fieldOf(HungerData.HUNGER_TAG))
            .sync(ByteBufCodecs.FLOAT)
            .build());
    public static final Supplier<AttachmentType<EmotionData.MaidEmotion>> SYNCED_EMOTION = ATTACHMENT_TYPES.register("emotion", r ->
            AttachmentType.builder(h -> EmotionData.MaidEmotion.DEFAULT)
                    .serialize(EmotionData.MaidEmotion.CODEC.fieldOf("value"))
                    .sync(EmotionData.MaidEmotion.STREAM_CODEC)
                    .build());
    public static final Supplier<AttachmentType<Boolean>> SYNCED_WANDERING_SPECIAL = ATTACHMENT_TYPES.register("wandering_special", r ->
            AttachmentType.builder(h -> false)
                    .serialize(Codec.BOOL.fieldOf("value"))
                    .sync(ByteBufCodecs.BOOL)
                    .build());
    public static final Supplier<AttachmentType<Boolean>> SYNCED_TRADING = ATTACHMENT_TYPES.register("trading", r ->
            AttachmentType.builder(h -> false)
                    .serialize(Codec.BOOL.fieldOf("value"))
                    .sync(ByteBufCodecs.BOOL)
                    .build());
    public static final Supplier<AttachmentType<CompoundTag>> PERSISTENT = ATTACHMENT_TYPES.register("persistent", r ->
            AttachmentType.builder(() -> new CompoundTag())
                    .serialize(CompoundTag.CODEC.fieldOf("value"))
                    .build());

    /**
     * 复刻旧版 Entity#getPersistentData 的"取或创建并存储"语义。
     * MC 26.1 / NeoForge 已移除 getPersistentData，改用 AttachmentType。
     */
    public static CompoundTag persistentData(Entity entity) {
        CompoundTag tag = entity.getExistingDataOrNull(PERSISTENT);
        if (tag == null) {
            tag = new CompoundTag();
            entity.setData(PERSISTENT, tag);
        }
        return tag;
    }

    public static void init(IEventBus bus){
        ATTACHMENT_TYPES.register(bus);
    }
}