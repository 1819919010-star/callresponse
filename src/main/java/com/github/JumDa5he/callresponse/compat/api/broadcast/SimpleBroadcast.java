package com.github.JumDa5he.callresponse.compat.api.broadcast;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder.debug;

public class SimpleBroadcast implements IBroadcast{
    private final List<String> keywords;
    private final CallFunction callFunction;
    private final CanCall canCall;
    private static final CanCall DEFAULT = EmotionDotingManager::isDoting;
    public static final CanCall EMPTY = (a, b) -> true;

    public SimpleBroadcast(CallFunction function, String... keywords){
        this(function, DEFAULT, keywords);
    }
    public SimpleBroadcast(CallFunction function, CanCall canCall, String... keywords){
        this.keywords = List.of(keywords);
        callFunction = function;
        this.canCall = canCall;
    }
    @Override
    public List<String> getKeywords() {
        return keywords;
    }

    @Override
    public void onCall(ServerPlayer player, EntityMaid maid) {
        if(!canCall.result(maid, player)){
            debug(player,
                    Component.literal("§e[调试] ")
                            .append(maid.getName())
                            .append(Component.literal(" 拒绝指令"))
            );
        }
        callFunction.onCall(maid, player);
    }

    public interface CallFunction{
        void onCall(EntityMaid maid, ServerPlayer player);
    }

    public interface CanCall{
        boolean result(EntityMaid maid, ServerPlayer player);
    }
}
