package com.github.JumDa5he.callresponse.compat.sign;

import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * 游行示众：把告示牌挂到女仆身上。
 * <p>挂载：手持告示牌物品右键女仆（消耗一个物品）。
 * <p>编辑 / 取下：女仆状态界面里的“示众牌”按钮。
 */
public class MaidSignManager {
    public static final int MAX_LINES = 4;
    /** 手打文本时单行的上限，防止客户端塞超长字符串。 */
    private static final int MAX_LINE_LENGTH = 64;
    /** 玩法上是“挂上去”，所以允许的距离放宽到 8 格。 */
    private static final double EDIT_DISTANCE = 8.0;
    private static final int INTERACT_DISTANCE = 4;

    public static MaidSignData get(EntityMaid maid) {
        return maid.getData(InitAttachTypes.SYNCED_MAID_SIGN);
    }

    public static boolean hasSign(EntityMaid maid) {
        return get(maid).enabled();
    }

    /** 这块牌子会不会威慑旁边看到它的女仆。 */
    public static boolean scaresOnlookers(EntityMaid maid) {
        MaidSignData data = get(maid);
        return data.enabled() && data.scareOnlookers();
    }

    public static void attach(EntityMaid maid, Item signItem) {
        attach(maid, signItem, true);
    }

    /** {@code scareOnlookers} 为 false 的牌子只作展示，不会威慑周围女仆（GLY 的彩蛋牌子用这个）。 */
    public static void attach(EntityMaid maid, Item signItem, boolean scareOnlookers) {
        maid.setData(InitAttachTypes.SYNCED_MAID_SIGN,
                MaidSignData.glowing(new SignText(), signItem, scareOnlookers));
    }

    /** 取下告示牌，并把它还给发起者（背包放不下就掉在脚下）。 */
    public static void detach(ServerPlayer player, EntityMaid maid) {
        Item signItem = takeSign(maid);
        if (signItem == null) {
            return;
        }
        // Inventory#add 会把塞不进去的部分留在传入的 stack 里，剩下的丢在脚下
        ItemStack refund = new ItemStack(signItem);
        if (!player.getInventory().add(refund)) {
            player.drop(refund, false);
        }
        // 女仆界面还开着，手动把背包内容同步回客户端
        player.containerMenu.broadcastChanges();
    }

    /** 摘掉告示牌并返回它的物品种类，没挂则返回 {@code null}。 */
    private static Item takeSign(EntityMaid maid) {
        if (!hasSign(maid)) {
            return null;
        }
        Item signItem = get(maid).item();
        maid.setData(InitAttachTypes.SYNCED_MAID_SIGN, MaidSignData.EMPTY);
        return signItem;
    }

    public static void setText(EntityMaid maid, SignText text) {
        // 只换文本和颜色，物品种类和“是否威慑旁人”都保持原样
        MaidSignData current = get(maid);
        maid.setData(InitAttachTypes.SYNCED_MAID_SIGN,
                MaidSignData.glowing(text, current.item(), current.scareOnlookers()));
    }

    /** 把牌子上的四行文字拼成一段纯文本。 */
    public static String signText(EntityMaid maid) {
        SignText text = get(maid).text();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < MAX_LINES; i++) {
            String line = text.getMessage(i, false).getString();
            if (line.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    /** 女仆身上有没有东西可以被右键挂上告示牌。 */
    public static boolean canAttach(Player player, EntityMaid maid) {
        return maid.isOwnedBy(player) && maid.isAlive() && !maid.isSleeping()
                && !hasSign(maid) && player.canInteractWithEntity(maid, INTERACT_DISTANCE);
    }

    /** 客户端编辑界面提交的结果（文字 + 颜色，或直接取下）。 */
    public static void applyClientUpdate(ServerPlayer player, EntityMaid maid, MaidSignUpdateC2SPacket message) {
        if (!maid.isOwnedBy(player) || !maid.isAlive() || !hasSign(maid)) {
            return;
        }
        if (!player.canInteractWithEntity(maid, EDIT_DISTANCE)) {
            return;
        }
        if (message.clear()) {
            detach(player, maid);
            return;
        }
        List<String> lines = message.lines();
        Component[] messages = new Component[MAX_LINES];
        for (int i = 0; i < MAX_LINES; i++) {
            String line = i < lines.size() && lines.get(i) != null ? lines.get(i) : "";
            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH);
            }
            messages[i] = Component.literal(line);
        }
        // 过滤后文本和原文分开存，避免原版渲染路径拿到同一个数组
        String previousText = signText(maid);
        setText(maid, new SignText(messages, messages.clone(), message.color(), true));
        String updatedText = signText(maid);
        // 只有文字真的变了才让女仆回应，单纯改颜色不算
        if (!updatedText.equals(previousText)) {
            MaidSignDialogue.reactToParade(player, maid, updatedText);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SignItem)) {
            return;
        }
        Player player = event.getEntity();
        if (!canAttach(player, maid)) {
            return;
        }
        attach(maid, stack.getItem());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * 女仆死亡时把示众牌掉出来。
     * <p>{@link MaidDeathEvent} 是在 {@code EntityMaid#die} 里、真正死亡之前触发的，
     * 保命饰品是在触发事件之前就拦下的，所以这里不用额外判断。
     */
    @SubscribeEvent
    public void onMaidDeath(MaidDeathEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        Item signItem = takeSign(maid);
        if (signItem != null) {
            maid.spawnAtLocation(new ItemStack(signItem));
        }
    }
}
