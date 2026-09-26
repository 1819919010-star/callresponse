package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;

/** 彩蛋女仆的台词池；内容来自 data/callresponse/outpost/gly_lines.json。 */
public final class OutpostGlyDialogue extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final ResourceLocation LINES_ID =
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "gly_lines");
    private static final OutpostGlyDialogue INSTANCE = new OutpostGlyDialogue();
    private static volatile List<String> lines = List.of();
    private static volatile List<String> hurtLines = List.of();

    private OutpostGlyDialogue() {
        super(GSON, "outpost");
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        JsonElement element = objects.get(LINES_ID);
        if (element == null || !element.isJsonObject()) {
            lines = List.of();
            hurtLines = List.of();
            return;
        }
        lines = readLines(element.getAsJsonObject().get("lines"));
        hurtLines = readLines(element.getAsJsonObject().get("hurt_lines"));
    }

    private static List<String> readLines(JsonElement array) {
        if (array == null || !array.isJsonArray()) {
            return List.of();
        }
        List<String> loaded = new java.util.ArrayList<>();
        for (JsonElement line : array.getAsJsonArray()) {
            String text = line.getAsString();
            if (!text.isBlank()) loaded.add(text);
        }
        return List.copyOf(loaded);
    }

    public static List<String> lines() {
        return lines;
    }

    public static List<String> hurtLines() {
        return hurtLines;
    }
}
