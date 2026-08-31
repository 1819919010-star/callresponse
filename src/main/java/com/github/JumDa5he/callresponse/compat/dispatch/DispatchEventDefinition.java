package com.github.JumDa5he.callresponse.compat.dispatch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;

public record DispatchEventDefinition(Category category, Component title, Component description,
                                      IntProvider duration, Emotion emotion, int weight,
                                      int cooldownMin, AdvancementRewards rewards) {
    public static final Codec<DispatchEventDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Category.CODEC.optionalFieldOf("category", Category.WORK).forGetter(DispatchEventDefinition::category),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(DispatchEventDefinition::title),
            ComponentSerialization.CODEC.fieldOf("description").forGetter(DispatchEventDefinition::description),
            IntProviders.CODEC.fieldOf("duration").forGetter(DispatchEventDefinition::duration),
            Emotion.CODEC.optionalFieldOf("emotion", new Emotion(0, 0, 0, 0)).forGetter(DispatchEventDefinition::emotion),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(DispatchEventDefinition::weight),
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(DispatchEventDefinition::cooldownMin),
            AdvancementRewards.CODEC.optionalFieldOf("rewards", AdvancementRewards.EMPTY).forGetter(DispatchEventDefinition::rewards)
    ).apply(instance, DispatchEventDefinition::new));

    public DispatchEventDefinition {
        weight = Math.max(1, weight);
        cooldownMin = Math.max(0, cooldownMin);
    }

    public enum Category {
        WORK, PLAY;

        public static final Codec<Category> CODEC = Codec.STRING.xmap(Category::parse, Category::serializedName);

        public static Category parse(String value) {
            return "play".equalsIgnoreCase(value) ? PLAY : WORK;
        }

        public String serializedName() {
            return name().toLowerCase();
        }
    }

    public record Emotion(int trust, int fear, int favor, int hunger) {
        public static final Codec<Emotion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("trust", 0).forGetter(Emotion::trust),
                Codec.INT.optionalFieldOf("fear", 0).forGetter(Emotion::fear),
                Codec.INT.optionalFieldOf("favor", 0).forGetter(Emotion::favor),
                Codec.INT.optionalFieldOf("hunger", 0).forGetter(Emotion::hunger)
        ).apply(instance, Emotion::new));
    }

    public long rollDurationMillis(RandomSource random) {
        return Math.clamp(duration.sample(random), 0, 10) * 60_000L;
    }
}
