package com.github.JumDa5he.callresponse.compat.task;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.menu.PrincessCarryContainer;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;

/** 使用 TLM 正规任务入口注册的公主抱工作模式。 */
public final class PrincessCarryTask implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "princess_carry");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.SADDLE.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // TLM 会在返回列表中继续追加日程、进食等本体行为，因此这里必须返回可修改列表。
        return new ArrayList<>(List.of(Pair.of(4, new PrincessCarryBehavior())));
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return hasSaddle(maid);
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        return List.of(Pair.of("has_saddle", PrincessCarryTask::hasSaddle));
    }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        int entityId = maid.getId();
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.callresponse.princess_carry.title");
            }

            @Override
            public AbstractMaidContainer createMenu(int id, Inventory inventory, Player player) {
                return new PrincessCarryContainer(id, inventory, entityId);
            }
        };
    }

    public static boolean isCurrentTask(EntityMaid maid) {
        return UID.equals(maid.getTask().getUid());
    }

    public static boolean hasSaddle(EntityMaid maid) {
        if (maid.getMainHandItem().is(Items.SADDLE) || maid.getOffhandItem().is(Items.SADDLE)) {
            return true;
        }
        for (int slot = 0; slot < maid.getMaidInv().getSlots(); slot++) {
            if (maid.getMaidInv().getStackInSlot(slot).is(Items.SADDLE)) {
                return true;
            }
        }
        return false;
    }
}
