package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.misc.DefaultMonsterType;
import com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只让 TLM 的攻击任务把复仇据点女仆视为敌对目标；普通女仆分类保持不变。 */
@Mixin(value = DefaultMonsterType.class, remap = false)
public abstract class BetrayalOutpostHostileTypeMixin {
    @Inject(method = "getMonsterType", at = @At("HEAD"), cancellable = true)
    private static void callresponse$outpostMaidIsHostile(LivingEntity target,
                                                           CallbackInfoReturnable<MonsterType> cir) {
        if (target instanceof EntityMaid maid && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            cir.setReturnValue(MonsterType.HOSTILE);
        }
    }
}
