package com.github.JumDa5he.callresponse.compat.api.broadcast;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface IBroadcast {
    default boolean isMatch(String command){
        return getKeywords().stream().anyMatch(command::contains);
    }
    List<String> getKeywords();
    void onCall(ServerPlayer player, EntityMaid maid);
}
