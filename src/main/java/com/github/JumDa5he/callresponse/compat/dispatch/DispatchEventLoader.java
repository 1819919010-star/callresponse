package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class DispatchEventLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DispatchEventLoader INSTANCE = new DispatchEventLoader();
    private static volatile Map<ResourceLocation, DispatchEventDefinition> events = Map.of();

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
        Map<ResourceLocation, DispatchEventDefinition> loaded = new LinkedHashMap<>();
        objects.forEach((fileId, element) ->
                DispatchEventDefinition.CODEC.parse(JsonOps.INSTANCE, element)
                        .resultOrPartial(error -> CallResponseMod.LOGGER.error("跳过无效派遣事件 {}: {}", fileId, error))
                        .ifPresent(definition -> loaded.put(fileId, definition)));
        events = Map.copyOf(loaded);
        CallResponseMod.LOGGER.info("已加载 {} 个派遣事件", loaded.size());
    }

    public static DispatchEventDefinition get(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : events.get(key);
    }

    public static Collection<DispatchEventDefinition> all() {
        return events.values();
    }

    public static Map<ResourceLocation, DispatchEventDefinition> allMap() {
        return events;
    }
}
