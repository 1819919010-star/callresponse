package com.github.JumDa5he.callresponse.compat.sign;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignText;

/**
 * 女仆身上挂着的“游行示众”告示牌数据。
 * <p>用原版 {@link SignText} 存文本，编辑器与渲染都能直接复用原版逻辑。
 * 示众牌强制发光，所以渲染时不看光照等级。
 * <p>{@code item} 记录当初挂上去的是哪种告示牌，取下时原样还给玩家。
 * <p>{@code scareOnlookers} 为 false 时，旁边看到这块牌子的女仆不会被威慑（GLY 的彩蛋牌子用它）。
 */
public record MaidSignData(SignText text, boolean enabled, Item item, boolean scareOnlookers) {
    /** 没有挂告示牌。 */
    public static final MaidSignData EMPTY = new MaidSignData(new SignText(), false, Items.OAK_SIGN, true);

    public static final Codec<MaidSignData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SignText.DIRECT_CODEC.fieldOf("text").forGetter(MaidSignData::text),
            Codec.BOOL.fieldOf("enabled").forGetter(MaidSignData::enabled),
            // 老存档没有这个字段，回退成橡木告示牌即可
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item", Items.OAK_SIGN).forGetter(MaidSignData::item),
            // 老存档没有这个字段时按“会威慑旁边女仆”处理，保持原来的表现
            Codec.BOOL.optionalFieldOf("scare_onlookers", true).forGetter(MaidSignData::scareOnlookers)
    ).apply(instance, MaidSignData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidSignData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** 新建一块强制发光的牌子，并记住它的物品种类。 */
    public static MaidSignData glowing(SignText text, Item item) {
        return glowing(text, item, true);
    }

    /** 同上，{@code scareOnlookers} 决定旁边看到这块牌子的女仆会不会被威慑。 */
    public static MaidSignData glowing(SignText text, Item item, boolean scareOnlookers) {
        return new MaidSignData(text.setHasGlowingText(true), true, item, scareOnlookers);
    }
}
