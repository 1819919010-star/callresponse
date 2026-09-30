package com.github.JumDa5he.callresponse.compat.sign.client;

import com.github.JumDa5he.callresponse.compat.sign.MaidSignManager;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import net.minecraft.world.item.SignItem;

/**
 * 指着女仆时的物品提示：手里拿着告示牌时告诉玩家可以挂上 / 已挂上。
 */
public final class MaidSignTips {
    private MaidSignTips() {
    }

    public static void register(MaidTipsOverlay overlay) {
        overlay.addSpecialTips("overlay.callresponse.maid_sign.attach", (item, maid, player) ->
                item.getItem() instanceof SignItem
                        && maid.isOwnedBy(player) && maid.isAlive()
                        && !MaidSignManager.hasSign(maid));
        overlay.addSpecialTips("overlay.callresponse.maid_sign.attached", (item, maid, player) ->
                item.getItem() instanceof SignItem
                        && maid.isOwnedBy(player) && maid.isAlive()
                        && MaidSignManager.hasSign(maid));
    }
}
