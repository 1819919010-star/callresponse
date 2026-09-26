package com.github.JumDa5he.callresponse.compat.wandering;

public enum WanderingMaidState {
    APPROACHING,
    RETRYING,
    WAITING,
    LEAVING,
    REJECTED;

    public static WanderingMaidState parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return APPROACHING;
        }
    }
}
