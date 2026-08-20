package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class DispatchEventLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DispatchEventLoader INSTANCE = new DispatchEventLoader();
    private static volatile Map<String, DispatchEventDefinition> events = Map.of();

    private DispatchEventLoader() {
        super(GSON, "dispatch_events");
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<String, DispatchEventDefinition> loaded = new LinkedHashMap<>();
        objects.forEach((fileId, element) -> {
            try {
                DispatchEventDefinition definition = parse(fileId, GsonHelper.convertToJsonObject(element, "dispatch event"));
                loaded.put(definition.id(), definition);
            } catch (Exception exception) {
                CallResponseMod.LOGGER.error("跳过无效派遣事件 {}", fileId, exception);
            }
        });
        events = Map.copyOf(loaded);
        CallResponseMod.LOGGER.info("已加载 {} 个派遣事件", loaded.size());
    }

    public static DispatchEventDefinition get(String id) {
        return events.get(id);
    }

    public static Collection<DispatchEventDefinition> all() {
        return events.values();
    }

    private static DispatchEventDefinition parse(ResourceLocation fileId, JsonObject json) {
        String id = GsonHelper.getAsString(json, "id", fileId.toString());
        var category = DispatchEventDefinition.Category.parse(GsonHelper.getAsString(json, "category", "work"));
        String title = GsonHelper.getAsString(json, "title");
        String description = GsonHelper.getAsString(json, "description");
        int durationMin = clamp(GsonHelper.getAsInt(json, "durationMin", 10), 10, 60);
        int durationMax = clamp(GsonHelper.getAsInt(json, "durationMax", durationMin), 10, 60);
        int weight = Math.max(1, GsonHelper.getAsInt(json, "weight", 1));
        int cooldown = Math.max(0, GsonHelper.getAsInt(json, "cooldownMin", 0));
        JsonObject emotionJson = json.has("emotion") && json.get("emotion").isJsonObject()
                ? json.getAsJsonObject("emotion") : new JsonObject();
        var emotion = new DispatchEventDefinition.Emotion(
                GsonHelper.getAsInt(emotionJson, "trust", 0),
                GsonHelper.getAsInt(emotionJson, "fear", 0),
                GsonHelper.getAsInt(emotionJson, "favor", 0),
                GsonHelper.getAsInt(emotionJson, "hunger", 0));
        List<DispatchEventDefinition.Reward> rewards = new ArrayList<>();
        if (json.has("rewards") && json.get("rewards").isJsonArray()) {
            json.getAsJsonArray("rewards").forEach(value -> {
                JsonObject reward = value.getAsJsonObject();
                String type = GsonHelper.getAsString(reward, "type", "item");
                rewards.add(new DispatchEventDefinition.Reward(type,
                        GsonHelper.getAsString(reward, "item", "minecraft:air"),
                        GsonHelper.getAsString(reward, "enchant", "minecraft:unbreaking"),
                        GsonHelper.getAsInt(reward, "countMin", 1),
                        GsonHelper.getAsInt(reward, "countMax", 1),
                        GsonHelper.getAsInt(reward, "levelMin", 1),
                        GsonHelper.getAsInt(reward, "levelMax", 1),
                        Math.max(1, GsonHelper.getAsInt(reward, "weight", 1))));
            });
        }
        return new DispatchEventDefinition(id, category, title, description, durationMin, durationMax,
                emotion, weight, cooldown, List.copyOf(rewards));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
