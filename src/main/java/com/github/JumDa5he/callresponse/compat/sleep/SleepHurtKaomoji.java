package com.github.JumDa5he.callresponse.compat.sleep;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashSet;
import java.util.Set;

/** Recognizes only TLM's data-pack hurt kaomoji through its public wire serializer. */
final class SleepHurtKaomoji {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(
            "touhou_little_maid", "chat_bubble/kaomoji.json");
    private static Set<String> hurt = Set.of(""); // TLM's documented empty-data fallback

    static void reload(ResourceManager resources) {
        Set<String> values = new HashSet<>(); values.add("");
        // TLM 1.5.3 merges this file from every server data pack rather than replacing it.
        for (var resource : resources.getResourceStack(FILE)) {
            try (var reader = resource.openAsReader()) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("hurt")) for (var entry : root.getAsJsonArray("hurt")) values.add(entry.getAsString());
            } catch (Exception failure) {
                CallResponseMod.LOGGER.warn("Cannot read TLM hurt kaomoji for sleep wake coexistence: {}", resource.sourcePackId(), failure);
            }
        }
        hurt = Set.copyOf(values);
    }

    static boolean matches(IChatBubbleData data) {
        if (!(data instanceof TextChatBubbleData) || data.priority() != IChatBubbleData.DEFAULT_PRIORITY
                || data.existTick() != IChatBubbleData.DEFAULT_EXIST_TICK) return false;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new TextChatBubbleData.TextChatSerializer().writeToBuff(buffer, data);
            Component text = buffer.readJsonWithCodec(net.minecraft.network.chat.ComponentSerialization.CODEC);
            return buffer.readResourceLocation().equals(IChatBubbleData.TYPE_2)
                    && hurt.contains(text.getString()) && text.equals(Component.literal(text.getString()));
        } finally { buffer.release(); }
    }
}
