package com.github.JumDa5he.callresponse.compat.api.event.saddle;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class SaddleEvent extends Event {
    private final Player player;
    private final EntityMaid maid;
    public SaddleEvent(Player player, EntityMaid maid){
        this.player = player;
        this.maid = maid;
    }

    public Player getPlayer() {
        return player;
    }

    public EntityMaid getMaid() {
        return maid;
    }

    public abstract static class Launch extends SaddleEvent{
        private final float chargePrecent;
        public Launch(Player player, EntityMaid maid, float chargePercent) {
            super(player, maid);
            this.chargePrecent = chargePercent;
        }

        public float getChargePrecent() {
            return chargePrecent;
        }

        public static class Pre extends Launch implements ICancellableEvent{
            public Pre(Player player, EntityMaid maid, float chargePercent) {
                super(player, maid, chargePercent);
            }
        }

        public static class AfterPush extends Launch implements ICancellableEvent{
            public AfterPush(Player player, EntityMaid maid, float chargePercent) {
                super(player, maid, chargePercent);
            }
        }

        public static class Post extends Launch{
            public Post(Player player, EntityMaid maid, float chargePercent) {
                super(player, maid, chargePercent);
            }
        }
    }

    public abstract static class Charge extends SaddleEvent{
        public Charge(Player player, EntityMaid maid) {
            super(player, maid);
        }

        public static class Start extends Charge{
            public Start(Player player, EntityMaid maid) {
                super(player, maid);
            }
        }

        public static class End extends Charge{
            public End(Player player, EntityMaid maid) {
                super(player, maid);
            }
        }
    }

    public static class Pickup extends SaddleEvent implements ICancellableEvent{
        public Pickup(Player player, EntityMaid maid) {
            super(player, maid);
        }
    }
}
