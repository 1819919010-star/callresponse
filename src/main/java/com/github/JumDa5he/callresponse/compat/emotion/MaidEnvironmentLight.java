package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 女仆环境亮度采样。
 * <p>
 * 坐在 EntitySit 上时，女仆脚部坐标可能因骑乘偏移落进设施方块内部；
 * 因此同时读取脚部、上半身和眼睛位置，取其中最高的真实环境亮度。
 */
public final class MaidEnvironmentLight {
    private MaidEnvironmentLight() {
    }

    public static int getEffectiveLightLevel(EntityMaid maid) {
        Level level = maid.level();
        BlockPos feetPos = maid.blockPosition();
        BlockPos bodyPos = feetPos.above();
        BlockPos eyePos = BlockPos.containing(maid.getEyePosition());

        int feetLight = level.getRawBrightness(feetPos, 0);
        int bodyLight = level.getRawBrightness(bodyPos, 0);
        int eyeLight = level.getRawBrightness(eyePos, 0);
        return Math.max(feetLight, Math.max(bodyLight, eyeLight));
    }
}
