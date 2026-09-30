package com.github.JumDa5he.callresponse.compat.outpost.entity;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.lang.reflect.Method;
import java.util.*;

/** Optional API hand-off; only verified add-on-owned states may be ended, never blanket AI restoration. */
public final class RevengeAddonIsolation {
    private RevengeAddonIsolation() {}
    private record Exit(Method active, Method cancel) {}
    private static List<Exit> exits;
    private static final Set<EntityMaid> RELEASED = Collections.newSetFromMap(new WeakHashMap<>());
    public static void releaseCuteOwnedState(RevengeMaidEntity maid) {
        if (maid.level().isClientSide || !RELEASED.add(maid)) return;
        if (exits == null) {
            List<Exit> found = new ArrayList<>();
            for (String[] entry : new String[][] {
                {"LonelinessHandler", "isAnyInteractionActive", "cancelInteractionEventsForInjury"},
                {"CompanionHandler", "isCompanionActive", "cancelCompanion"},
                {"SleepWithYouHandler", "isSleepWithYouActive", "cancelSleepWithYou"},
                {"MoreExpressionHandler", "isMoreExpressionActive", "cancelMoreExpression"},
                {"AfraidWeaponHandler", "isAfraidWeaponActive", "cancelAfraidWeapon"},
                {"MoreDanceHandler", "isDanceActive", "cancelDance"}
            }) {
                try {
                    Class<?> type = Class.forName("cn.autoforged.maid_cute_activity." + entry[0], false,
                            RevengeAddonIsolation.class.getClassLoader());
                    found.add(new Exit(type.getMethod(entry[1], EntityMaid.class), type.getMethod(entry[2], EntityMaid.class)));
                } catch (ReflectiveOperationException | LinkageError ignored) { /* Optional API absent. */ }
            }
            exits = List.copyOf(found);
        }
        for (Exit exit : exits) {
            try {
                if (Boolean.TRUE.equals(exit.active.invoke(null, maid))) exit.cancel.invoke(null, maid);
            } catch (ReflectiveOperationException | LinkageError failure) {
                org.apache.logging.log4j.LogManager.getLogger("callresponse").warn(
                        "Cannot release optional Cute Activity state {} for {}", exit.cancel.getName(), maid.getUUID());
            }
        }
    }
}

