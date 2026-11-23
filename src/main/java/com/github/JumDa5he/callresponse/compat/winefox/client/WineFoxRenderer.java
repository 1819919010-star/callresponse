package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntity;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class WineFoxRenderer extends GeoReplacedEntityRenderer<WineFoxEntity, WineFoxRenderer.Animation>
        implements IGeoEntityRenderer<WineFoxEntity> {
    private final Map<Integer, Animation> animations = new HashMap<>();
    private final WineFoxBubbleRenderer dialogue = new WineFoxBubbleRenderer();

    public WineFoxRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new GeckoLayerMaidHeld<>(this, context.getItemInHandRenderer()));
        // Keep real helmet/chest attributes and drops; this model displays only its held weapon.
        WineFoxClientEvents.track(this);
    }

    public void clearAnimations() {
        animations.clear();
        dialogue.clear();
        currentAnimatable = null;
    }

    @Override
    public void render(WineFoxEntity entity, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffer, int light) {
        super.render(entity, yaw, partialTick, stack, buffer, light);
        // A confirmed dead entity remains renderable for its existing 72-tick death performance.
        dialogue.render(entity, partialTick, stack, buffer, light, entityRenderDispatcher);
    }

    public void pruneAnimations() {
        var world = net.minecraft.client.Minecraft.getInstance().level;
        animations.values().removeIf(animation -> animation.getEntity().isRemoved()
                || animation.getEntity().level() != world);
        if (currentAnimatable != null && (currentAnimatable.getEntity().isRemoved()
                || currentAnimatable.getEntity().level() != world)) currentAnimatable = null;
    }

    @Override
    public Animation getAnimatableEntity(WineFoxEntity entity) {
        WineFoxModelResources.ensureLoaded();
        Animation cached = animations.get(entity.getId());
        if (cached == null || cached.getEntity() != entity) {
            cached = new Animation(entity);
            animations.put(entity.getId(), cached);
        }
        return cached;
    }

    @Override
    public float getWidthScale(WineFoxEntity entity) { return WineFoxModelResources.scale(); }

    @Override
    public float getHeightScale(WineFoxEntity entity) { return WineFoxModelResources.scale(); }

    @Override
    protected float getFlipDegrees(WineFoxEntity entity) { return 0; }

    @Override
    public IGeoEntity getGeoEntity(WineFoxEntity entity) { return getAnimatableEntity(entity); }

    @Override
    @SuppressWarnings("unchecked")
    public void addGeoLayerRenderer(GeoLayerRenderer<?, ?> layer) {
        addLayer((GeoLayerRenderer<WineFoxEntity, ?>) layer);
    }

    @Override
    public void geoRender(WineFoxEntity entity, float yaw, float partialTick, PoseStack stack,
                          MultiBufferSource buffer, int light) {
        render(entity, yaw, partialTick, stack, buffer, light);
    }

    /** One processor, bone state and controller per actual monster; no proxy EntityMaid. */
    public static final class Animation extends AnimatableEntity<WineFoxEntity> implements IGeoEntity {
        private String selected = "";
        private long attackStart = Long.MIN_VALUE;
        private int lastHurtTime;
        private float partialTick;
        private final WineFoxHeadLook headLook = new WineFoxHeadLook();

        public Animation(WineFoxEntity entity) {
            super(entity, 60);
            addAnimationController(new AnimationController<Animation>(this, "winefox", 0, this::select) {
                @Override
                public double adjustTick(double tick) {
                    double ordinary = super.adjustTick(tick);
                    // The server's start time, not the first rendered frame, owns attack phase.
                    if ("death".equals(selected) && dying()) {
                        // Embedded Gecko stops updating at the exact end even for HOLD_ON_LAST_FRAME.
                        return Math.min(WineFoxEntity.DEATH_TICKS - 0.001, Math.max(0,
                                entity.level().getGameTime() - entity.getDeathStart() + partialTick));
                    }
                    if (("sweep".equals(selected) || "cast".equals(selected)) && attacking()) {
                        return Math.min(entity.getAttackDuration() - 0.001, Math.max(0, entity.level().getGameTime()
                                - entity.getAttackStart() + partialTick));
                    }
                    if ("attacked".equals(selected) && entity.hurtTime > 0) {
                        return Math.min(9.999, Math.max(0, 10 - entity.hurtTime + partialTick));
                    }
                    return ordinary;
                }
            });
        }

        private boolean attacking() {
            long elapsed = entity.level().getGameTime() - entity.getAttackStart();
            return entity.isAlive() && !dying() && entity.getAttackKind() != 0
                    && elapsed >= 0 && elapsed < entity.getAttackDuration();
        }

        private boolean dying() { return entity.getDeathStart() >= 0; }

        @Override
        public boolean setCustomAnimations(AnimationContext<?> context, AnimationEvent<?> event) {
            // Gecko calls adjustTick before select; capture this frame before advancing any controller.
            partialTick = event.getPartialTick();
            boolean updated = super.setCustomAnimations(context, event);
            if (entity.isAlive() && !dying() && !attacking() && entity.hurtTime <= 0)
                headLook.apply(getCurrentModel(), context.data(), updated);
            return updated;
        }

        private PlayState select(AnimationEvent<Animation> event) {
            boolean death = dying();
            boolean attack = attacking();
            String name = death ? "death" : attack ? (entity.getAttackKind() == 1 ? "sweep" : "cast")
                    : entity.hurtTime > 0 ? "attacked"
                    : event.getLimbSwingAmount() > 0.015F ? "walk" : "idle";
            boolean restart = !name.equals(selected)
                    || attack && entity.getAttackStart() != attackStart
                    || "attacked".equals(name) && entity.hurtTime > lastHurtTime;
            if (restart) event.getController().markNeedsReload();
            selected = name;
            attackStart = entity.getAttackStart();
            lastHurtTime = entity.hurtTime;
            event.getController().setAnimation(death ? new AnimationBuilder().playAndHold("death")
                    : attack || "attacked".equals(name) ? new AnimationBuilder().playOnce(name)
                    : new AnimationBuilder().loop(name));
            return PlayState.CONTINUE;
        }

        @Override
        public ResourceLocation getModelLocation() { return WineFoxModelResources.MODEL; }

        @Override
        public ResourceLocation getAnimationFileLocation() { return WineFoxModelResources.MODEL; }

        @Override
        public ResourceLocation getTextureLocation() { return WineFoxModelResources.texture(entity.getVariant()); }

        @Override public IMaid getMaid() { return entity; }
        @Override public MaidModelInfo getMaidInfo() { return WineFoxModelResources.modelInfo(); }
        @Override public ILocationModel getGeoModel() { return getCurrentModel(); }
        // Required by TLM's rendering-only interface; this fixed monster has no model switching/YSM/roaming.
        @Override public void setMaidInfo(MaidModelInfo info) { }
        @Override public void setYsmModel(String modelId, String texture) { }
        @Override public void updateRoamingVars(Object2FloatOpenHashMap<String> values) { }
    }
}
