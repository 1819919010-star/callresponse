package com.github.JumDa5he.callresponse.compat.damage;

/** 主人攻击自己女仆时使用的保护破除等级。 */
public enum ProtectionBreakLevel {
    NONE,
    BASIC,
    ULTIMATE;

    public boolean atLeast(ProtectionBreakLevel required) {
        return ordinal() >= required.ordinal();
    }
}
