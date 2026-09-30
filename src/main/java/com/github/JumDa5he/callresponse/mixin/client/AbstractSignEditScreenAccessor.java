package com.github.JumDa5he.callresponse.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 原版编辑界面把当前 {@link SignText} 藏在私有字段里，且只在玩家打字时整体替换。
 * 颜色选择需要直接替换这个字段，才能在界面里立刻看到换色效果。
 */
@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
    @Accessor("text")
    SignText callresponse$getSignText();

    @Accessor("text")
    void callresponse$setSignText(SignText text);
}
