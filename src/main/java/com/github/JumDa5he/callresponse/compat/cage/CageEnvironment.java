package com.github.JumDa5he.callresponse.compat.cage;

import net.minecraft.util.StringRepresentable;

/** 铁笼内部的独立环境，不向世界放置真实流体或火方块。 */
public enum CageEnvironment implements StringRepresentable {
    EMPTY("empty"),
    WATER("water"),
    LAVA("lava"),
    POWDER_SNOW("powder_snow"),
    FIRE("fire"),
    CACTUS("cactus"),
    LIGHTNING("lightning"),
    GOLDEN_APPLE("golden_apple");

    private final String serializedName;

    CageEnvironment(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
