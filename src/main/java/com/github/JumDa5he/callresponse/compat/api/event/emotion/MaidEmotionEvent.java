package com.github.JumDa5he.callresponse.compat.api.event.emotion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class MaidEmotionEvent extends Event implements ICancellableEvent{
    private final EntityMaid maid;

    public MaidEmotionEvent(EntityMaid maid){
        this.maid = maid;
    }

    public EntityMaid getMaid() {
        return maid;
    }

    public static class MaidDialogueEvent extends MaidEmotionEvent{
        private final String reason;
        public MaidDialogueEvent(EntityMaid maid, String reason) {
            super(maid);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class MaidBetrayalEvent extends MaidEmotionEvent{
        public MaidBetrayalEvent(EntityMaid maid) {
            super(maid);
        }
    }

    public static class MaidDevotedEvent extends MaidEmotionEvent{
        public MaidDevotedEvent(EntityMaid maid) {
            super(maid);
        }
    }

    public static class MaidDotingEvent extends MaidEmotionEvent{
        public MaidDotingEvent(EntityMaid maid) {
            super(maid);
        }
    }

    public static class MaidForgettingEvent extends MaidEmotionEvent{
        public MaidForgettingEvent(EntityMaid maid) {
            super(maid);
        }
    }

    public static class MaidPassiveEvent extends MaidEmotionEvent{
        private final Type type;
        public MaidPassiveEvent(EntityMaid maid, Type type) {
            super(maid);
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        public enum Type{
            SLEEP,
            NATURAL_GROWTH,
            WORK,
            BREAK,
            NO_INTERACTION,
            ARMOR_FULL,
            POSITIVE_EFFECT,
            SEEING_FOOD,
            SEEING_WEAPON,
            HEAD_ITEM,
            LEASHING,
            SEEING_LEASHING,
            BRIGHT,
            WATER,
            FIREWORK,
            FRIENDS,
            DIMENSION,
            OTHER_DEATH,
            HURT
        }
    }
}
