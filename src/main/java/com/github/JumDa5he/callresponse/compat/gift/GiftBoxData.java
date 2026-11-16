package com.github.JumDa5he.callresponse.compat.gift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 礼物盒里携带的数据：谁送的、打算送什么。
 *
 * @param kind     礼物种类
 * @param sender   送礼女仆的 UUID 字符串，空串表示来源不明
 * @param maidName 送礼女仆的显示名，用于 tooltip 和 AI 提示词
 * @param modelId  送礼女仆的模型 id，拆出「手办」时用来复刻同一个模型
 */
public record GiftBoxData(GiftKind kind, String sender, String maidName, String modelId) {
    public static final GiftBoxData EMPTY = new GiftBoxData(GiftKind.STALE, "", "", "");

    public static final Codec<GiftBoxData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GiftKind.CODEC.fieldOf("kind").forGetter(GiftBoxData::kind),
            Codec.STRING.optionalFieldOf("sender", "").forGetter(GiftBoxData::sender),
            Codec.STRING.optionalFieldOf("maid_name", "").forGetter(GiftBoxData::maidName),
            Codec.STRING.optionalFieldOf("model_id", "").forGetter(GiftBoxData::modelId)
    ).apply(instance, GiftBoxData::new));

    public static final StreamCodec<ByteBuf, GiftBoxData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    @Nullable
    public UUID senderUuid() {
        if (sender == null || sender.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(sender);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static GiftBoxData of(GiftKind kind, @Nullable UUID sender, String maidName, String modelId) {
        return new GiftBoxData(kind, sender == null ? "" : sender.toString(), maidName == null ? "" : maidName,
                modelId == null ? "" : modelId);
    }
}
