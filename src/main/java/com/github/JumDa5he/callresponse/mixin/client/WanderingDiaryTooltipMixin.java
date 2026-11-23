package com.github.JumDa5he.callresponse.mixin.client;

import net.minecraft.network.chat.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.List;

/** Only this diary's author; keep ordinary books and the native written-book format unchanged. */
@Mixin(WrittenBookItem.class)
public abstract class WanderingDiaryTooltipMixin {
    @Redirect(method="appendHoverText", at=@At(value="INVOKE",
            target="Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent author(String key, Object[] args, ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (key.equals("book.byAuthor")) {
            Component storyAuthor = com.github.JumDa5he.callresponse.compat.story.CampStoryBooks.author(stack);
            if (storyAuthor != null) return Component.translatable(key, storyAuthor);
        }
        if (key.equals("book.byAuthor") && stack.hasTag() && stack.getTag().getBoolean("CallResponseWanderingDiary"))
            return Component.translatable(key, Component.translatable("book.callresponse.wandering_camp_diary.author"));
        return Component.translatable(key, args);
    }
}
