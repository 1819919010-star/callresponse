package com.github.JumDa5he.callresponse.compat.api;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

public class AuthorUtil {
    // i狐区最长的河
    public static final UUID GLY = UUID.fromString("91bd580f-5f17-4e30-872f-2e480dd9a220");
    // i狐区最高的山
    public static final UUID JUMDA5HE = UUID.fromString("3da49788-4b30-45df-b1ca-126ef1757bb4");

    @OnlyIn(Dist.CLIENT)
    public static boolean isGLY(){
        return getPlayerUUID().equals(GLY);
    }

    @OnlyIn(Dist.CLIENT)
    public static boolean isLoveWineFoxTV(){
        return isGLY() || getPlayerUUID().equals(JUMDA5HE);
    }

    public static boolean isGLY(Player player){
        return player.getUUID().equals(GLY);
    }

    public static boolean isLoveWineFoxTV(Player player){
        return isGLY(player) || player.getUUID().equals(JUMDA5HE);
    }

    @OnlyIn(Dist.CLIENT)
    private static UUID getPlayerUUID(){
        return Minecraft.getInstance().getGameProfile().getId();
    }
}
