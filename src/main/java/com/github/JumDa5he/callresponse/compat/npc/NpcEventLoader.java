package com.github.JumDa5he.callresponse.compat.npc;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内置 assets 事件先加载，数据包中的同 id 定义随后覆盖。 */
public final class NpcEventLoader extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final String BUILTIN_PATH = "assets/callresponse/npc_events/events.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final NpcEventLoader INSTANCE = new NpcEventLoader();
    private static volatile Map<String, NpcEventDefinition> events = Map.of();

    private NpcEventLoader() {
        super(Codec.PASSTHROUGH.xmap(dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
                        element -> new Dynamic<>(JsonOps.INSTANCE, element)),
                FileToIdConverter.json("npc_events"));
    }

    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "npc_events"), INSTANCE);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<String, NpcEventDefinition> loaded = new LinkedHashMap<>();
        loadBuiltin(loaded);
        objects.forEach((fileId, element) -> loadElement(loaded, element, fileId.toString()));
        events = Map.copyOf(loaded);
        CallResponseMod.LOGGER.info("已加载 {} 个 NPC 日常事件", loaded.size());
    }

    private static void loadBuiltin(Map<String, NpcEventDefinition> loaded) {
        try (InputStream stream = CallResponseMod.class.getClassLoader().getResourceAsStream(BUILTIN_PATH)) {
            if (stream == null) {
                CallResponseMod.LOGGER.error("找不到内置 NPC 事件文件 {}", BUILTIN_PATH);
                return;
            }
            JsonElement element = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            loadElement(loaded, element, BUILTIN_PATH);
        } catch (Exception e) {
            CallResponseMod.LOGGER.error("读取内置 NPC 事件失败 {}", BUILTIN_PATH, e);
        }
    }

    private static void loadElement(Map<String, NpcEventDefinition> loaded,
                                    JsonElement element, String source) {
        List<JsonElement> entries = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(entries::add);
        } else {
            entries.add(element);
        }
        for (int i = 0; i < entries.size(); i++) {
            try {
                NpcEventDefinition definition = NpcEventDefinition.fromJson(entries.get(i).getAsJsonObject());
                loaded.put(definition.id(), definition);
            } catch (Exception e) {
                CallResponseMod.LOGGER.error("跳过无效 NPC 事件 {}#{}: {}", source, i, e.getMessage());
            }
        }
    }

    public static NpcEventDefinition get(String id) {
        return events.get(id);
    }

    public static Collection<NpcEventDefinition> all() {
        return events.values();
    }

    public static List<NpcEventDefinition> byType(NpcEventDefinition.Type type) {
        return events.values().stream().filter(event -> event.type() == type)
                .sorted(Comparator.comparingInt(NpcEventDefinition::priority).reversed())
                .toList();
    }
}
