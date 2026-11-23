package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.birthday.client.BirthdayClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 主界面彩蛋：本机玩家生日当天，把标题界面的黄字标语换成生日祝福。
 *
 * <p>标题界面还没有连接服务器，这里读取的是客户端同步并持久化下来的生日，
 * 日期退回本机日期，详见 {@link BirthdayClientData}。</p>
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Shadow private SplashRenderer splash;

    @Inject(method = "init", at = @At("TAIL"))
    private void callresponse$birthdaySplash(CallbackInfo callback) {
        // 原本就没有标语（列表为空）时保持原样，仅在原位置替换文案。
        if (splash != null && BirthdayClientData.isBirthdayToday()) {
            splash = new SplashRenderer(Component.translatable("gui.callresponse.birthday.splash",
                    Minecraft.getInstance().getUser().getName()).getString());
        }
    }
}
