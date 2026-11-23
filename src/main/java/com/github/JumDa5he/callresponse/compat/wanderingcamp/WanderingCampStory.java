package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostAnchorBlock;
import com.github.JumDa5he.callresponse.compat.outpost.OutpostMarkerBlockEntity;
import com.github.JumDa5he.callresponse.compat.story.CampStoryBooks;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import javax.annotation.Nullable;

/** The native diary roll represents one book. Only its identity waits for the camp's outcome. */
public final class WanderingCampStory {
    static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath("callresponse", "chests/wandering_maid_camp");
    static final String ORIGINAL_PAGE = "book.callresponse.wandering_camp_diary.page";
    private static final String SAGE = CampStoryBooks.WANDERING_SAGE.pagePrefix();
    private static final String GLY = CampStoryBooks.WANDERING_GLY.pagePrefix();
    private static final String HELD_DIARY = "CallResponseHeldCampDiary";
    private static final String ROLLED_SLOT = "CallResponseDiarySlot";

    static void select(ServerLevel level, WanderingCampSavedData ledger, CompoundTag camp, BlockPos chest) {
        if (!camp.contains("StoryChest")) {
            camp.putLong("StoryChest", chest.asLong());
            ledger.setDirty();
        }
        if (!camp.getBoolean("Gly")) finishOrdinary(level, ledger, camp);
    }

    static void finishOrdinary(ServerLevel level, WanderingCampSavedData ledger, CompoundTag camp) {
        if (!camp.contains("StoryChest") || camp.contains("StoryPage")) return;
        BlockPos chest = BlockPos.of(camp.getLong("StoryChest"));
        String page = camp.contains("NormalStory") ? camp.getString("NormalStory") : ordinary(level, chest);
        camp.putString("NormalStory", page);
        camp.putString("StoryPage", page);
        ledger.setDirty();
        deliverIfHeld(level, chest);
    }

    static void residentAdded(ServerLevel level, WanderingCampSavedData ledger, String id, CompoundTag camp,
                              EntityMaid maid) {
        CompoundTag resident = WanderingCampData.data(maid);
        if (!camp.hasUUID("Maid") || !camp.getUUID("Maid").equals(maid.getUUID())
                || !id.equals(resident.getString("Camp"))) return;
        if (resident.getBoolean("Gly")) {
            camp.putBoolean("GlySpawned", true);
            camp.putString("StoryPage", GLY);
            camp.remove("NormalStory");
            ledger.setDirty();
            deliverIfHeld(level, BlockPos.of(camp.getLong("StoryChest")));
        } else {
            finishOrdinary(level, ledger, camp);
        }
    }

    private static String ordinary(ServerLevel level, BlockPos chest) {
        return CampStoryBooks.selectNew(level, chest) ? SAGE : ORIGINAL_PAGE;
    }

    /** Runs synchronously inside native unpack, before any caller can remove the rolled diary. */
    public static ItemStack onNativeDiary(ServerLevel level, RandomizableContainerBlockEntity chest,
                                          int slot, ItemStack nativeDiary) {
        if (!(chest instanceof ChestBlockEntity storyChest)) return nativeDiary;
        if (lootStoryPage(level, storyChest.getBlockPos()) != null) return nativeDiary;
        CompoundTag metadata = chest.getPersistentData();
        metadata.put(HELD_DIARY, nativeDiary.save(level.registryAccess()));
        metadata.putInt(ROLLED_SLOT, slot);
        WanderingCampSavedData.get(level).deferStoryBook(level, chest.getBlockPos());
        return ItemStack.EMPTY;
    }

    /** Loot tables receive the chest position for both a shared chest and a player's Lootr inventory. */
    @Nullable public static String lootStoryPage(ServerLevel level, BlockPos pos) {
        var ledger = WanderingCampSavedData.get(level);
        // The installed template has a south-facing chest west of the north-facing bed head.
        // Check the four possible registered IDs as well, so an already registered/rotated
        // chest, or an older camp with no live resident, still uses its own persistent record.
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos head = pos.relative(facing.getClockWise());
            CompoundTag camp = ledger.findEntry(level.dimension().location() + "@" + head.asLong());
            if (camp == null || !camp.contains("Facing")
                    || camp.contains("StoryChest") && camp.getLong("StoryChest") != pos.asLong()
                    || Direction.from2DDataValue(camp.getInt("Facing")) != facing) continue;
            if (camp.contains("StoryPage")) return camp.getString("StoryPage");
            if (camp.getBoolean("Spawned") || camp.getInt("Attempts") >= 3 || !camp.getBoolean("Gly")) {
                if (camp.getBoolean("GlySpawned") || camp.getBoolean("Spawned") && camp.getBoolean("Gly"))
                    return GLY;
                return camp.contains("NormalStory") ? camp.getString("NormalStory") : ordinary(level, pos);
            }
            return hasPendingAnchor(level, head, facing) ? null : ordinary(level, pos);
        }
        if (!level.hasChunkAt(pos)) return ordinary(level, pos);
        var state = level.getBlockState(pos);
        if (!state.hasProperty(ChestBlock.FACING)) return ordinary(level, pos);
        Direction facing = state.getValue(ChestBlock.FACING).getOpposite();
        BlockPos head = pos.relative(facing.getClockWise());
        return hasPendingAnchor(level, head, facing) ? null : ordinary(level, pos);
    }

    private static boolean hasPendingAnchor(ServerLevel level, BlockPos head, Direction facing) {
        if (!level.hasChunkAt(head)) return true;
        return level.getBlockEntity(head.below(2)) instanceof OutpostMarkerBlockEntity anchor
                && anchor.isWanderingCamp() && head.equals(anchor.beddingHead())
                && anchor.getBlockState().getValue(OutpostAnchorBlock.FACING) == facing;
    }

    /** Unloaded or full chests retain the original roll. Broken/replaced chests have no held roll. */
    static boolean deliverIfHeld(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest) || CampStoryBooks.isPersonalChest(chest)) return true;
        CompoundTag metadata = chest.getPersistentData();
        if (!metadata.contains(HELD_DIARY) || !TABLE.toString().equals(metadata.getString(CampStoryBooks.TABLE_RECEIPT)))
            return true;
        String page = lootStoryPage(level, pos);
        if (page == null) return false;
        int slot = metadata.getInt(ROLLED_SLOT);
        if (slot < 0 || slot >= chest.getContainerSize() || !chest.getItem(slot).isEmpty()) {
            for (slot = 0; slot < chest.getContainerSize() && !chest.getItem(slot).isEmpty(); slot++) {}
            if (slot == chest.getContainerSize()) return false;
        }
        chest.setItem(slot, makeBook(page, ItemStack.parseOptional(level.registryAccess(), metadata.getCompound(HELD_DIARY))));
        metadata.remove(HELD_DIARY);
        metadata.remove(ROLLED_SLOT);
        chest.setChanged();
        return true;
    }

    private static ItemStack makeBook(String page, ItemStack nativeDiary) {
        if (GLY.equals(page)) return CampStoryBooks.WANDERING_GLY.create();
        if (SAGE.equals(page)) return CampStoryBooks.WANDERING_SAGE.create();
        return nativeDiary;
    }

    private WanderingCampStory() {}
}
