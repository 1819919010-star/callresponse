package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.AnimationProcessor;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.IBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 给普通 TLM 模型的抱人女仆叠加简洁的双臂横抱姿势。 */
@Mixin(GeckoMaidEntity.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class PrincessCarryCarrierPoseMixin {
    @Inject(method = "setCustomAnimations", at = @At("RETURN"), remap = false)
    private void callresponse$applyCarrierArmPose(AnimationContext context, AnimationEvent event,
                                                   CallbackInfoReturnable<Boolean> cir) {
        GeckoMaidEntity<?> gecko = (GeckoMaidEntity<?>) (Object) this;
        if (!(gecko.getMaid().asEntity() instanceof EntityMaid carrier)
                || !PrincessCarryManager.isMaidCarrySession(carrier)) {
            return;
        }

        AnimationProcessor processor = gecko.getAnimationProcessor();
        // 数值取自 carryon.animation.json 的 carryon:player；只覆盖抱持所需的手臂和前臂。
        callresponse$setRotation(processor.getBone("LeftArm"), -30.0F, 5.0F, -5.0F, -0.5F);
        callresponse$setRotation(processor.getBone("LeftForeArm"), -90.0F, -47.5F, 90.0F, 0.0F);
        callresponse$setRotation(processor.getBone("RightArm"), -30.0F, -5.0F, 5.0F, -0.5F);
        callresponse$setRotation(processor.getBone("RightForeArm"), -90.0F, 47.5F, -90.0F, 0.0F);
    }

    private static void callresponse$setRotation(IBone bone, float x, float y, float z, float yOffset) {
        if (bone == null) return;
        BoneSnapshot initial = bone.getInitialSnapshot();
        // TLM 的 Bedrock 动画解析规则是 X/Y 取反、Z 保持原方向。
        bone.setRotationX(initial.rotationValueX - (float) Math.toRadians(x));
        bone.setRotationY(initial.rotationValueY - (float) Math.toRadians(y));
        bone.setRotationZ(initial.rotationValueZ + (float) Math.toRadians(z));
        if (yOffset != 0.0F) {
            bone.setPositionY(initial.positionOffsetY + yOffset);
        }
    }
}
