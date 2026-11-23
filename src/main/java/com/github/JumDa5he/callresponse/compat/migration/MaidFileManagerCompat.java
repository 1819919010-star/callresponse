package com.github.JumDa5he.callresponse.compat.migration;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.neoforged.fml.ModList;

/** No SPI types in this class: absent or incompatible optional mods never load the provider. */
public final class MaidFileManagerCompat {
    private static boolean registered;
    private MaidFileManagerCompat() {}
    public static void register() {
        if (registered || !ModList.get().isLoaded("maid_file_manager")) return;
        try {
            ClassLoader loader = MaidFileManagerCompat.class.getClassLoader();
            Class.forName("io.github.zgxhzhr.maidfm.spi.MaidMigrationProvider", false, loader);
            Class.forName("io.github.zgxhzhr.maidfm.spi.MaidMigrationRegistry", false, loader);
            Class.forName("com.github.JumDa5he.callresponse.compat.migration.CallResponseNpcEventProvider", true, loader)
                    .getMethod("register").invoke(null);
            registered = true;
            CallResponseMod.LOGGER.info("Registered callresponse:npc_event with Maid File Manager (compiled against NeoForge 1.4.1)");
        } catch (ReflectiveOperationException | LinkageError ex) {
            CallResponseMod.LOGGER.warn("Maid File Manager SPI is incompatible; callresponse NPC migration disabled", ex);
        }
    }
}
