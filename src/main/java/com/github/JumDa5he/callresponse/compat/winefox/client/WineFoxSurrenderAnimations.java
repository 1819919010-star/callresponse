package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;

/** Read the supplied clip through the same embedded loader used by TLM / MoreAnimation.
 * The file has optional tails/expressions for several models; native Gecko plays matching bones. */
@OnlyIn(Dist.CLIENT)
public final class WineFoxSurrenderAnimations implements ResourceManagerReloadListener {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("callresponse", "revenge_surrender");
    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath("callresponse", "winefox/surrender.animation.json");

    public static void ensureLoaded() {
        if (!GeckoLibCache.getInstance().getAnimations().containsKey(ID))
            load(Minecraft.getInstance().getResourceManager());
    }
    private static void load(ResourceManager manager) {
        try (var stream = manager.open(FILE)) {
            AnimationFile file = new AnimationFile();
            GeckoModelLoader.mergeAnimationFile(stream, file);
            if (file.getAnimation("surrender") == null)
                throw new IllegalStateException("Bundled surrender animation is missing its surrender clip");
            GeckoLibCache.getInstance().getAnimations().put(ID, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load bundled surrender animation", exception);
        }
    }
    @Override public void onResourceManagerReload(ResourceManager manager) { load(manager); }
}
