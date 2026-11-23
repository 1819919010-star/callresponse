package com.github.JumDa5he.callresponse.compat.migration;

import com.github.JumDa5he.callresponse.compat.npc.NpcEventSavedData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.zgxhzhr.maidfm.spi.MaidMigrationProvider;
import io.github.zgxhzhr.maidfm.spi.MaidMigrationRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;

/** Only external persistent NPC data. TLM histories and entity emotions are already in native NBT. */
public final class CallResponseNpcEventProvider implements MaidMigrationProvider {
    private final Map<EntityMaid, CompoundTag> imported = new WeakHashMap<>();
    public static void register() { MaidMigrationRegistry.register(new CallResponseNpcEventProvider()); }
    @Override public ResourceLocation getId() { return new ResourceLocation("callresponse", "npc_event"); }
    @Override public String getDependencyModId() { return "callresponse"; }

    @Override public CompoundTag export(EntityMaid maid) {
        requireServer(maid);
        CompoundTag existing = NpcEventSavedData.get(maid).peek(maid.getUUID());
        if (existing == null && maid.getPersistentData().contains("CallResponseNpcEvents", Tag.TAG_COMPOUND))
            existing = maid.getPersistentData().getCompound("CallResponseNpcEvents").copy();
        return NpcMigrationCodec.exportData(existing, maid.level().getGameTime(), maid.level().getDayTime() / 24000);
    }

    @Override public void importData(EntityMaid maid, CompoundTag data) {
        ServerLevel level = requireServer(maid);
        // Official 1.4.1 calls the SPI only after addFreshEntity succeeds.
        if (level.getEntity(maid.getUUID()) != maid || maid.isRemoved())
            throw new IllegalStateException("NPC migration target has not entered its world");
        if (data.equals(imported.get(maid))) return;
        CompoundTag restored = NpcMigrationCodec.importData(data, level.getGameTime(), level.getDayTime() / 24000);
        if (!restored.isEmpty()) NpcEventSavedData.get(maid).replace(maid.getUUID(), restored);
        imported.put(maid, data.copy());
    }

    private static ServerLevel requireServer(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("NPC migration must run on the logical server thread");
        return level;
    }
}
