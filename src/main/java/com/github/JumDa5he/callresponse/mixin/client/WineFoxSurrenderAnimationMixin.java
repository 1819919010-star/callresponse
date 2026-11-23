package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender;
import com.github.JumDa5he.callresponse.compat.winefox.client.WineFoxSurrenderAnimations;
import com.github.JumDa5he.callresponse.compat.winefox.client.WineFoxHeadLook;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimationState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.Animation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.keyframe.BoneAnimation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.molang.runtime.ExpressionEvaluator;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Only a living surrendered revenge maid gets this exclusive, per-entity native Gecko controller. */
@Mixin(value = GeckoMaidEntity.class, remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class WineFoxSurrenderAnimationMixin extends AnimatableEntity<Mob> {
    @Unique private AnimationController callresponse$surrenderController;
    @Unique private float callresponse$surrenderPartial;
    @Unique private boolean callresponse$nativeVisibility;
    @Unique private Animation callresponse$visibilityAnimation;
    @Unique private boolean callresponse$restoreModelBindings;
    @Unique private final WineFoxHeadLook callresponse$surrenderHeadLook = new WineFoxHeadLook();
    @Unique private final Map<AnimationController, AnimationController> callresponse$visibilityControllers = new IdentityHashMap<>();

    protected WineFoxSurrenderAnimationMixin(Mob entity, int fps) { super(entity, fps); }

    @Unique private boolean callresponse$surrenderActive() {
        return entity instanceof EntityMaid maid && maid.isAlive() && WineFoxSurrender.isSurrendered(maid);
    }

    @Inject(method = "getAnimationFileLocation", at = @At("HEAD"), cancellable = true)
    private void callresponse$surrenderFile(CallbackInfoReturnable<ResourceLocation> cir) {
        if (callresponse$surrenderActive() && !callresponse$nativeVisibility) {
            WineFoxSurrenderAnimations.ensureLoaded();
            cir.setReturnValue(WineFoxSurrenderAnimations.ID);
        }
    }

    /** Only the isolated visibility controller sees its scale-only clip; native assets stay untouched. */
    @Override public Animation getAnimation(String name) {
        if (callresponse$nativeVisibility && callresponse$visibilityAnimation != null
                && callresponse$visibilityAnimation.animationName.equals(name))
            return callresponse$visibilityAnimation;
        return super.getAnimation(name);
    }

    @Inject(method = "setCustomAnimations", at = @At("HEAD"), cancellable = true)
    private void callresponse$exclusiveSurrender(AnimationContext context, AnimationEvent event,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!callresponse$surrenderActive()) {
            // A resource/model reload during the exclusive pose only rebound the active controllers.
            // Rebind the ordinary ones once, when native death/normal animation has authority again.
            if (callresponse$restoreModelBindings && getCurrentModel() != null)
                getAnimationProcessor().registerModelRenderer(getCurrentModel().bones());
            callresponse$restoreModelBindings = false;
            callresponse$surrenderController = null;
            callresponse$visibilityControllers.clear();
            return;
        }
        WineFoxSurrenderAnimations.ensureLoaded();
        callresponse$surrenderPartial = event.getPartialTick(); // Before Gecko's adjustTick, never one frame late.
        if (callresponse$surrenderController == null) {
            callresponse$surrenderController = new AnimationController(this, "callresponse_surrender", 0, e -> {
                e.getController().setAnimation(new AnimationBuilder().playAndHold("surrender"));
                return PlayState.CONTINUE;
            }) {
                private boolean bound;

                @Override public void process(double tick, AnimationEvent event, ExpressionEvaluator evaluator,
                                              List bones, boolean crash, boolean rendererDirty, boolean scheduledUpdate) {
                    if (rendererDirty) callresponse$restoreModelBindings = true;
                    // A maid already rendered before surrender has a clean model. This NEW controller
                    // still needs native switchRenderer to create its own bone queues exactly once.
                    super.process(tick, event, evaluator, bones, crash, rendererDirty || !bound, scheduledUpdate);
                    bound = true;
                    // Match MoreAnimation's zero-transition alignment: late tracking must show the
                    // synchronized pose on this frame, rather than wait another render to finish setup.
                    if (getAnimationState() == AnimationState.TRANSITIONING && transitionLengthTicks == 0)
                        super.process(tick, event, evaluator, bones, crash, false, false);
                }

                @Override public double adjustTick(double tick) {
                    super.adjustTick(tick);
                    // The embedded controller otherwise stops updating at precisely 20, losing its hold pose.
                    return Math.min(19.999, Math.max(0, WineFoxSurrender.getAnimationTicks((EntityMaid) entity)
                            + callresponse$surrenderPartial));
                }
            };
        }
        var controllers = getAnimationData().getAnimationControllers();
        var ordinary = new ArrayList<>(controllers);
        try {
            controllers.clear();
            for (Object value : ordinary) {
                AnimationController controller = (AnimationController) value;
                if (controller.getName().matches("(?:pre_)?parallel_[0-7]_controller"))
                    controllers.add(callresponse$visibilityControllers.computeIfAbsent(controller,
                            this::callresponse$visibilityController));
            }
            controllers.add(callresponse$surrenderController);
            // Keep the exclusive body pose; only native-style head look is added on top of it.
            boolean updated = super.setCustomAnimations(context, event);
            callresponse$surrenderHeadLook.apply(getCurrentModel(), context.data(), updated);
            cir.setReturnValue(updated);
        } finally {
            controllers.clear();
            controllers.addAll(ordinary);
        }
    }

    /** Model authors hide effects on arbitrary bones, in any native parallel slot.
     * Run only those scale tracks, never their body motion, sound/particle instructions or old actions. */
    @Unique private AnimationController callresponse$visibilityController(AnimationController original) {
        String clipName = original.getName().replace("_controller", "").replace("parallel_", "parallel");
        return new AnimationController(this, original.getName(), 0, e -> {
            if (Minecraft.getInstance().isPaused() || callresponse$visibilityAnimation == null
                    || callresponse$visibilityAnimation.boneAnimations.isEmpty()) return PlayState.STOP;
            e.getController().setAnimation(new AnimationBuilder().loop(clipName));
            return PlayState.CONTINUE;
        }) {
            private boolean bound;
            private Animation source, scales;

            @Override public void process(double tick, AnimationEvent event, ExpressionEvaluator evaluator,
                                          List bones, boolean crash, boolean rendererDirty, boolean scheduledUpdate) {
                // A missing clip can skip this frame, but must not lose a geometry rebind request.
                if (rendererDirty) bound = false;
                boolean previousNative = callresponse$nativeVisibility;
                Animation previousAnimation = callresponse$visibilityAnimation;
                callresponse$nativeVisibility = true;
                callresponse$visibilityAnimation = null;
                try {
                    Animation current = getAnimation(clipName);
                    if (current != source) {
                        source = current;
                        scales = current == null ? null : callresponse$scaleOnly(current);
                        clearAnimationCache(); // Animation resource reload, independent of geometry reload.
                    }
                    callresponse$visibilityAnimation = scales;
                    if (scales == null || scales.boneAnimations.isEmpty()) {
                        setAnimation(null);
                        getBoneAnimationQueues().clear();
                        return;
                    }
                    super.process(tick, event, evaluator, bones, crash, rendererDirty || !bound, scheduledUpdate);
                    bound = true;
                    if (getAnimationState() == AnimationState.TRANSITIONING && transitionLengthTicks == 0)
                        super.process(tick, event, evaluator, bones, crash, false, false);
                } finally {
                    callresponse$nativeVisibility = previousNative;
                    callresponse$visibilityAnimation = previousAnimation;
                }
            }
        };
    }

    @Unique private static Animation callresponse$scaleOnly(Animation source) {
        ReferenceArrayList<BoneAnimation> bones = new ReferenceArrayList<>();
        for (BoneAnimation bone : source.boneAnimations)
            if (!bone.scaleKeyFrames.isEmpty())
                bones.add(new BoneAnimation(bone.boneName, List.of(), List.of(), bone.scaleKeyFrames));
        // Dropping queued events after process is too late: native loop transitions can execute them.
        return new Animation(source.animationName, source.animationLength, source.loop, bones, new ReferenceArrayList<>());
    }
}
