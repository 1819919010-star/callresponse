package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.Collection;
import java.util.Map;

public final class DispatchEventLoader extends SimpleJsonResourceReloadListener<DispatchEventDefinition> {
    private static final DispatchEventLoader INSTANCE = new DispatchEventLoader();
    private static volatile Map<Identifier, DispatchEventDefinition> events = Map.of();

    private DispatchEventLoader() {
        super(DispatchEventDefinition.CODEC, FileToIdConverter.json("dispatch_events"));
    }

    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "dispatch_events"), INSTANCE);
    }

    @Override
    protected void apply(Map<Identifier, DispatchEventDefinition> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        events = Map.copyOf(objects);
        CallResponseMod.LOGGER.info("Loaded {} dispatch events", objects.size());
    }

    public static DispatchEventDefinition get(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? null : events.get(key);
    }

    public static Collection<DispatchEventDefinition> all() {
        return events.values();
    }

    public static Map<Identifier, DispatchEventDefinition> allMap() {
        return events;
    }
}
