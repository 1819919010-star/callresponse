package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.mixin.accessor.AdvancementRewardsAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;

public record DispatchEventDefinition(Category category, Component title, Component description,
                                      IntProvider duration, Emotion emotion, int weight,
                                      int cooldownMin, AdvancementRewards rewards) {
    /** 1.20.1 没有 ComponentSerialization，用组件 JSON 字符串做中转。 */
    private static final Codec<Component> COMPONENT_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> (Component) Component.Serializer.fromJson(dynamic.convert(JsonOps.INSTANCE).getValue()),
            component -> new Dynamic<>(JsonOps.INSTANCE,
                    JsonParser.parseString(Component.Serializer.toJson(component))));

    /** 1.20.1 的 AdvancementRewards 没有 Codec，且 serializeToJson 在 function 为 null 时崩溃，自写安全序列化。 */
    private static final Codec<AdvancementRewards> REWARDS_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> AdvancementRewards.deserialize(dynamic.convert(JsonOps.INSTANCE).getValue().getAsJsonObject()),
            rewards -> new Dynamic<>(JsonOps.INSTANCE, rewardsToJson(rewards)));

    public static JsonObject rewardsToJson(AdvancementRewards rewards) {
        AdvancementRewardsAccessor accessor = (AdvancementRewardsAccessor) rewards;
        JsonObject json = new JsonObject();
        json.addProperty("experience", accessor.callresponse$experience());
        JsonArray loot = new JsonArray();
        for (ResourceLocation location : accessor.callresponse$loot()) {
            loot.add(location.toString());
        }
        json.add("loot", loot);
        JsonArray recipes = new JsonArray();
        for (ResourceLocation location : accessor.callresponse$recipes()) {
            recipes.add(location.toString());
        }
        json.add("recipes", recipes);
        CommandFunction.CacheableFunction function = accessor.callresponse$function();
        if (function != null && function.getId() != null) {
            json.addProperty("function", function.getId().toString());
        }
        return json;
    }

    public static final Codec<DispatchEventDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Category.CODEC.optionalFieldOf("category", Category.WORK).forGetter(DispatchEventDefinition::category),
            COMPONENT_CODEC.fieldOf("title").forGetter(DispatchEventDefinition::title),
            COMPONENT_CODEC.fieldOf("description").forGetter(DispatchEventDefinition::description),
            IntProvider.CODEC.fieldOf("duration").forGetter(DispatchEventDefinition::duration),
            Emotion.CODEC.optionalFieldOf("emotion", new Emotion(0, 0, 0, 0)).forGetter(DispatchEventDefinition::emotion),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(DispatchEventDefinition::weight),
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(DispatchEventDefinition::cooldownMin),
            REWARDS_CODEC.optionalFieldOf("rewards", AdvancementRewards.EMPTY).forGetter(DispatchEventDefinition::rewards)
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
        // 派遣界面与配置约定均为 10～60 分钟，不能沿用高版本移植时误写的 10 分钟上限。
        return Math.max(10, Math.min(duration.sample(random), 60)) * 60_000L;
    }
}
