package com.github.tartaricacid.callresponse.init;

import com.github.tartaricacid.callresponse.CallResponseMod;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.callresponse.compat.hunger.HungerData;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

// AttachType支持自动同步到客户端，无需手动同步
public class InitAttachTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CallResponseMod.MOD_ID);
    public static final Supplier<AttachmentType<Float>> SYNCED_HUNGER = ATTACHMENT_TYPES.register("hunger", r ->
            AttachmentType.builder(h -> HungerData.DEFAULT_HUNGER)
            .serialize(Codec.FLOAT.fieldOf(HungerData.HUNGER_TAG).codec())
            .sync(ByteBufCodecs.FLOAT)
            .build());
    public static final Supplier<AttachmentType<EmotionData.MaidEmotion>> SYNCED_EMOTION = ATTACHMENT_TYPES.register("emotion", r ->
            AttachmentType.builder(h -> EmotionData.MaidEmotion.DEFAULT)
                    .serialize(EmotionData.MaidEmotion.CODEC)
                    .sync(EmotionData.MaidEmotion.STREAM_CODEC)
                    .build());

    public static void init(IEventBus bus){
        ATTACHMENT_TYPES.register(bus);
    }
}
