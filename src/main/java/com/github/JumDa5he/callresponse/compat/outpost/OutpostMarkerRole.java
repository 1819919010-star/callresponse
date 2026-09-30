package com.github.JumDa5he.callresponse.compat.outpost;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * 结构模板里的出生点标记类型。
 *
 * <p>NONE 表示候选点，运行时从候选点中随机选出五人并分配角色；
 * 其余值表示该点已经固定为对应角色。</p>
 */
public enum OutpostMarkerRole implements StringRepresentable {
    NONE,
    HEAVY,
    SWORDSMAN,
    FARMER,
    FEEDER,
    GLY;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public BetrayalOutpostMaidData.Role toMaidRole() {
        return switch (this) {
            case NONE -> null;
            case HEAVY -> BetrayalOutpostMaidData.Role.HEAVY;
            case SWORDSMAN -> BetrayalOutpostMaidData.Role.SWORDSMAN;
            case FARMER -> BetrayalOutpostMaidData.Role.FARMER;
            case FEEDER -> BetrayalOutpostMaidData.Role.FEEDER;
            case GLY -> BetrayalOutpostMaidData.Role.GLY;
        };
    }
}
