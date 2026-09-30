package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Keep before() bone restoration. Only the two add-ons' after() overlays are excluded, not YSM itself. */
@Pseudo
@Mixin(targets={"cn.autoforged.maid_cute_activity.ysm.YsmAnimationBridge",
        "com.github.JumDa5he.moreanimation.compat.ysm.YsmAnimationBridge"}, remap=false)
public abstract class AddonYsmOverlayMixin {
    @Redirect(method="after", at=@At(value="INVOKE",
            target="Ljava/lang/reflect/Method;invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", ordinal=0))
    private static Object entity(java.lang.reflect.Method method, Object receiver, Object[] args)
            throws java.lang.reflect.InvocationTargetException, IllegalAccessException {
        Object entity = method.invoke(receiver, args);
        return entity instanceof RevengeMaidEntity ? null : entity;
    }
}

