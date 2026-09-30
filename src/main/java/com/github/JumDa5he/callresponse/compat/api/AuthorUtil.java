package com.github.JumDa5he.callresponse.compat.api;

import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** 作者彩蛋身份；保留合作者 UUID，公共路径不引用客户端 Minecraft。 */
public final class AuthorUtil {
    public static final UUID GLY = UUID.fromString("91bd580f-5f17-4e30-872f-2e480dd9a220");
    public static final UUID JUMDA5HE = UUID.fromString("3da49788-4b30-45df-b1ca-126ef1757bb4");
    private AuthorUtil() {}
    public static boolean isGLY(Player player) { return player.getUUID().equals(GLY); }
    public static boolean isLoveWineFoxTV(Player player) {
        return isGLY(player) || player.getUUID().equals(JUMDA5HE);
    }
}
