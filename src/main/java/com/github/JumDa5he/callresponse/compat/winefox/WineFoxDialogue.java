package com.github.JumDa5he.callresponse.compat.winefox;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** One server-selected line for both the bubble and nearby chat; no maid manager or AI request. */
public final class WineFoxDialogue {
    public static final int COOLDOWN_TICKS = 300, DISPLAY_TICKS = 300, LINE_COUNT = 17;
    public static final int CAGE_ESCAPE_LINE = 17;
    private static final double CHAT_RANGE_SQR = 64.0D * 64.0D;
    private static final String SAVE_KEY = "WineFoxDialogue";
    private static final String ARRIVAL_HANDLED = "CallResponseWineFoxArrivalHandled";
    private static final String[] POOLS = {"combat", "hurt", "last_stand", "death"};
    private final WineFoxEntity fox;
    private int cooldown;
    private boolean combatSeen, lastStandSpoken, deathSpoken;

    public WineFoxDialogue(WineFoxEntity fox) { this.fox = fox; }

    static void skipLoadedArrival(WineFoxEntity fox) {
        fox.getPersistentData().putBoolean(ARRIVAL_HANDLED, true);
    }

    static void announceArrival(WineFoxEntity fox, @Nullable ServerPlayer trigger) {
        if (!(fox.level() instanceof ServerLevel level) || !fox.isAlive() || fox.isRemoved()
                || fox.getPersistentData().getBoolean(ARRIVAL_HANDLED) || WineFoxPursuit.pending(fox)) return;
        fox.getPersistentData().putBoolean(ARRIVAL_HANDLED, true);
        Component message = Component.translatable("message.callresponse.winefox.arrival").withStyle(ChatFormatting.RED);
        if (trigger != null && trigger.level() == level) trigger.sendSystemMessage(message);
        for (ServerPlayer player : level.players()) {
            if (player != trigger && player.distanceToSqr(fox) <= CHAT_RANGE_SQR) player.sendSystemMessage(message);
        }
    }

    public void tick() {
        if (!fox.level().isClientSide && cooldown > 0) cooldown--;
    }

    /** First real engagement in this life, not every target change; suppressed lines are not queued. */
    public void combat() {
        if (fox.level().isClientSide || combatSeen) return;
        combatSeen = true;
        say(0, false);
    }

    public void hurt() { say(1, false); }

    /** The cage's committed release invokes this once; unlike combat lines this is bubble-only. */
    public void cageEscape() {
        if (!(fox.level() instanceof ServerLevel) || !fox.isAlive() || fox.isRemoved()) return;
        fox.syncDialogue(CAGE_ESCAPE_LINE);
        cooldown = COOLDOWN_TICKS;
    }

    public void lastStand() {
        if (fox.level().isClientSide || lastStandSpoken) return;
        lastStandSpoken = true;
        say(2, true);
    }

    /** Caller must confirm native death succeeded before invoking this method. */
    public void death() {
        if (fox.level().isClientSide || deathSpoken) return;
        deathSpoken = true;
        say(3, true);
    }

    private void say(int pool, boolean priority) {
        if (!(fox.level() instanceof ServerLevel level) || fox.isRemoved()
                || (!priority && (!fox.isAlive() || cooldown > 0))) return;
        // Priorities replace the one visible line immediately and cannot be overwritten by ordinary chatter.
        int line = 1 + pool * 4 + fox.getRandom().nextInt(4);
        fox.syncDialogue(line);
        cooldown = COOLDOWN_TICKS;
        // Match revenge death dialogue's same-dimension 64-block audience. Preserve components
        // for the recipient's language, and send only here (never from tracking or load callbacks).
        Component message = Component.translatable("message.callresponse.winefox.dialogue.format",
                fox.getDisplayName(), Component.translatable(translationKey(line)));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(fox) <= CHAT_RANGE_SQR) player.sendSystemMessage(message);
        }
    }

    public static String translationKey(int line) {
        if (line == CAGE_ESCAPE_LINE) return "bubble.callresponse.winefox.cage_escape";
        if (line < 1 || line > LINE_COUNT) return "";
        int index = line - 1;
        return "bubble.callresponse.winefox." + POOLS[index / 4] + "." + index % 4;
    }

    public void save(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt("Cooldown", cooldown);
        data.putBoolean("CombatSeen", combatSeen);
        data.putBoolean("LastStandSpoken", lastStandSpoken);
        data.putBoolean("DeathSpoken", deathSpoken);
        tag.put(SAVE_KEY, data);
    }

    public void load(CompoundTag tag) {
        CompoundTag data = tag.getCompound(SAVE_KEY);
        cooldown = Mth.clamp(data.getInt("Cooldown"), 0, COOLDOWN_TICKS);
        combatSeen = data.getBoolean("CombatSeen");
        lastStandSpoken = data.getBoolean("LastStandSpoken");
        deathSpoken = data.getBoolean("DeathSpoken");
        // Dialogue text is transient: loading does not replay old combat, protection or death lines.
    }
}
