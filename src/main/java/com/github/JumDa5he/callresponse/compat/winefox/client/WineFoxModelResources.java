package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Uses TLM's embedded Gecko loader without registering a household maid model pack. */
@OnlyIn(Dist.CLIENT)
public final class WineFoxModelResources implements ResourceManagerReloadListener {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("callresponse", "corrupted_wine_fox");
    private static final ResourceLocation DESCRIPTION = ResourceLocation.fromNamespaceAndPath("callresponse", "winefox/model.json");
    private static ResourceLocation[] textures;
    private static float scale;
    private static MaidModelInfo modelInfo;

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        load(manager);
    }

    public static void ensureLoaded() {
        // TLM also supports its own pack reload, which clears its geometry cache independently.
        if (textures == null || !GeckoLibCache.getInstance().getGeoModels().containsKey(MODEL)
                || !GeckoLibCache.getInstance().getAnimations().containsKey(MODEL)) {
            load(Minecraft.getInstance().getResourceManager());
        }
    }

    private static void load(ResourceManager manager) {
        try (var reader = new InputStreamReader(manager.open(DESCRIPTION), StandardCharsets.UTF_8)) {
            JsonObject info = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("model_list")
                    .get(0).getAsJsonObject();
            try (InputStream geometry = manager.open(ResourceLocation.parse(info.get("model").getAsString()))) {
                GeckoModelLoader.registerGeo(MODEL, geometry);
            }
            AnimationFile animations = new AnimationFile();
            for (var location : info.getAsJsonArray("animation")) {
                try (InputStream animation = manager.open(ResourceLocation.parse(location.getAsString()))) {
                    GeckoModelLoader.mergeAnimationFile(animation, animations);
                }
            }
            for (String name : new String[]{"idle", "walk", "swing_hand", "attacked", "angry", "cast", "sweep", "death"}) {
                if (animations.getAnimation(name) == null) {
                    throw new IllegalStateException("Wine fox animation missing: " + name);
                }
            }
            GeckoLibCache.getInstance().getAnimations().put(MODEL, animations);
            var variants = info.getAsJsonArray("texture_variants");
            if (variants.size() != 2) throw new IllegalStateException("Wine fox needs two textures");
            textures = new ResourceLocation[]{ResourceLocation.parse(variants.get(0).getAsString()),
                    ResourceLocation.parse(variants.get(1).getAsString())};
            scale = info.get("render_entity_scale").getAsFloat();
            modelInfo = CustomPackLoader.GSON.fromJson(info, MaidModelInfo.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load bundled wine fox model", exception);
        }
    }

    public static ResourceLocation texture(int variant) {
        ensureLoaded();
        return textures[variant == 1 ? 1 : 0];
    }

    public static float scale() {
        ensureLoaded();
        return scale;
    }

    public static MaidModelInfo modelInfo() {
        ensureLoaded();
        return modelInfo;
    }
}
