package com.github.JumDa5he.callresponse.compat.bauble;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;

public class BaubleDetector {

    public static boolean hasNoEat(EntityMaid maid) {
        BaubleItemHandler baubles = maid.getMaidBauble();
        for (int i = 0; i < baubles.size(); i++) {
            if (ItemsUtil.extractItem(baubles, i, 1, true, null).getItem() == ModItems.NO_EAT_BAUBLE.get()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasMoreEat(EntityMaid maid) {
        BaubleItemHandler baubles = maid.getMaidBauble();
        for (int i = 0; i < baubles.size(); i++) {
            if (ItemsUtil.extractItem(baubles, i, 1, true, null).getItem() == ModItems.MORE_EAT_BAUBLE.get()) {
                return true;
            }
        }
        return false;
    }
}
