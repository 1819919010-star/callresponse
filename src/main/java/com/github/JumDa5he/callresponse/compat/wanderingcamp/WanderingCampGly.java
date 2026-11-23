package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager;
import com.github.JumDa5he.callresponse.compat.outpost.OutpostGlyDialogue;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/** Appearance and the existing line pools only. No outpost identity, combat or snowball AI. */
public final class WanderingCampGly {
    private WanderingCampGly() {}
    private static boolean isGly(EntityMaid maid) {
        return WanderingCampData.active(maid) && WanderingCampData.data(maid).getBoolean("Gly");
    }
    public static void initialize(EntityMaid maid) {
        // Existing GLY uses the same roster model selection, not a separate fixed skin pool.
        BetrayalOutpostManager.addGlySign(maid);
        WanderingCampData.data(maid).putInt("GlyLastLine", -1);
        WanderingCampData.data(maid).putInt("GlyLastHurt", -1);
    }
    public static void tick(EntityMaid maid) {
        if (!isGly(maid) || IntimidationManager.isIntimidated(maid) || !(maid.level() instanceof ServerLevel level)) return;
        var d = WanderingCampData.data(maid);
        if (level.getGameTime() < d.getLong("GlyNextLine") || level.getNearestPlayer(maid, 32) == null) return;
        speak(maid, OutpostGlyDialogue.lines(), "GlyLastLine");
        d.putLong("GlyNextLine", level.getGameTime() + 10L * (10 + level.random.nextInt(21)));
    }
    public static void hurt(EntityMaid maid) {
        if (!isGly(maid) || IntimidationManager.isIntimidated(maid)) return;
        var d = WanderingCampData.data(maid);
        if (maid.level().getGameTime() < d.getLong("GlyNextHurt")) return;
        speak(maid, OutpostGlyDialogue.hurtLines(), "GlyLastHurt");
        d.putLong("GlyNextHurt", maid.level().getGameTime() + 2 + maid.getRandom().nextInt(4));
    }
    private static void speak(EntityMaid maid, List<String> pool, String lastKey) {
        if (pool.isEmpty()) return;
        var d = WanderingCampData.data(maid);
        int previous = d.getInt(lastKey);
        int index = maid.getRandom().nextInt(pool.size());
        if (pool.size() > 1 && index == previous) index = (index + 1 + maid.getRandom().nextInt(pool.size()-1)) % pool.size();
        d.putInt(lastKey, index);
        String line = pool.get(index);
        WanderingBehaviorAuthority.speak(maid, () -> maid.getChatBubbleManager().addChatBubble(
                TextChatBubbleData.type2(Component.literal(line))));
    }
    public static void death(EntityMaid maid) {
        if (!isGly(maid) || !(maid.level() instanceof ServerLevel level)) return;
        List<String> pool = OutpostGlyDialogue.deathLines();
        if (pool.isEmpty()) return;
        Component message = Component.literal("<").append(maid.getName()).append(">: ")
                .append(pool.get(maid.getRandom().nextInt(pool.size())));
        for (var player : level.players()) if (player.distanceToSqr(maid) <= 32*32) player.sendSystemMessage(message);
    }
}
