package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.IBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.model.provider.data.EntityModelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** TLM's head overlay only: keep the clip's base pose and never accumulate repeated render calls. */
@OnlyIn(Dist.CLIENT)
public final class WineFoxHeadLook {
    private IBone head;
    private float basePitch, baseYaw;

    public void apply(AnimatedGeoModel model, EntityModelData data, boolean animationUpdated) {
        IBone current = model == null ? null : model.head();
        if (current == null) { head = null; return; }
        if (animationUpdated || current != head) {
            head = current;
            basePitch = head.getRotationX();
            baseYaw = head.getRotationY();
        }
        head.setRotationX(basePitch + (float) Math.toRadians(data.headPitch));
        head.setRotationY(baseYaw + (float) Math.toRadians(data.netHeadYaw));
    }
}
