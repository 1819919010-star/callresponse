package com.github.JumDa5he.callresponse.mixin.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.List;

/** Only localize the marked diary's author; ordinary written books retain their own author string. */
@Mixin(WrittenBookItem.class)
public abstract class WanderingDiaryTooltipMixin {
    @Redirect(method="appendHoverText", at=@At(value="INVOKE",
            target="Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent author(String key, Object[] args, ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
        Component storyAuthor = com.github.JumDa5he.callresponse.compat.story.CampStoryBooks.author(stack);
        if (key.equals("book.byAuthor") && storyAuthor != null)
            return Component.translatable(key, storyAuthor);
        if (key.equals("book.byAuthor") && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean("CallResponseWanderingDiary"))
            return Component.translatable(key, Component.translatable("book.callresponse.wandering_camp_diary.author"));
        return Component.translatable(key, args);
    }
}
