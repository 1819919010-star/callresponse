package com.github.JumDa5he.callresponse.compat.api.broadcast;

import com.github.JumDa5he.callresponse.compat.broadcast.actions.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class BroadcastManager {
    private static final List<IBroadcast> BROADCASTS = new ArrayList<>();

    public static void registry(IBroadcast broadcast){
        BROADCASTS.add(broadcast);
    }

    public static void call(ServerPlayer player, List<EntityMaid> maids, String command){
        BROADCASTS.forEach(broadcast -> {
            if(broadcast.isMatch(command)) {
                maids.forEach(maid -> broadcast.onCall(player, maid));
            }
        });
    }

    static {
        registry(new SimpleBroadcast(WalkToOwnerAndSitAction::execute, "集合", "过来"));
        registry(new SimpleBroadcast(WalkToOwnerAndTakeFoodAction::execute, "开饭", "拿食物"));
        registry(new SimpleBroadcast(AttackOtherMaidAction::execute, "打起来", "攻击", "打架", "决斗"));
        registry(new SimpleBroadcast(StopAttackAction::execute, SimpleBroadcast.EMPTY, "停战", "停止攻击"));
        registry(new SimpleBroadcast(StandUpAction::execute, "站起来", "起立"));
        registry(new SimpleBroadcast(SitDownAction::execute, "坐下"));
    }
}
