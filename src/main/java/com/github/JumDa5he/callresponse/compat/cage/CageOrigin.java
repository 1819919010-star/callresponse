package com.github.JumDa5he.callresponse.compat.cage;

/** 用于选择空手查看铁笼时的文案。 */
public enum CageOrigin {
    NORMAL,
    PILLAGER_OUTPOST,
    VILLAGE,
    STRONGHOLD;

    public static CageOrigin byName(String value) {
        try {
            return value == null ? NORMAL : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }
}
