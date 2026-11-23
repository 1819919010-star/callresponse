package com.github.JumDa5he.callresponse.compat.story;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.BitSet;
import java.util.Set;
import com.github.JumDa5he.callresponse.mixin.accessor.OutpostLootContainerAccessor;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager;

/** Native written books: page components stay translatable on the reading player's client. */
public enum CampStoryBooks {
    WANDERING_ORIGINAL("wandering_camp_diary", 6),
    OUTPOST_ORIGINAL("outpost_story", 8),
    WANDERING_SAGE("wandering_sage_diary", 28),
    OUTPOST_RECRUITED("outpost_story_recruited", 38),
    WANDERING_GLY("wandering_gly_diary", 41, Set.of(4, 31, 33, 34, 35, 36), Set.of(34)),
    OUTPOST_GLY("outpost_story_gly", 15, Set.of(13), Set.of());

    public static final String TABLE_RECEIPT = "callresponse:camp_story_table";
    private static final String BOOK_ID = "CallResponseCampStory";
    private static final String SELECTED_NEW = "callresponse:camp_story_new";
    public static final String OUTPOST_GLY_RECEIPT = "callresponse:outpost_gly_book_issued";
    public static final ResourceLocation WANDERING_TABLE = new ResourceLocation("callresponse", "chests/wandering_maid_camp");
    public static final ResourceLocation OUTPOST_TABLE = new ResourceLocation("callresponse", "chests/betrayal_outpost_story");
    private final String id;
    private final String prefix;
    private final int pages;
    private final Set<Integer> secondSheets, thirdSheets;

    CampStoryBooks(String id, int pages) {
        this(id, pages, Set.of(), Set.of());
    }

    CampStoryBooks(String id, int pages, Set<Integer> secondSheets, Set<Integer> thirdSheets) {
        this.id = id;
        this.prefix = "book.callresponse." + id;
        this.pages = pages;
        this.secondSheets = secondSheets;
        this.thirdSheets = thirdSheets;
    }

    public String pagePrefix() { return prefix + ".page"; }
    String lootId() { return id; }

    public boolean matches(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && stack.hasTag() && id.equals(stack.getTag().getString(BOOK_ID));
    }

    public static Component author(ItemStack stack) {
        for (CampStoryBooks story : values())
            if (story.matches(stack)) return Component.translatable(story.prefix + ".author");
        return null;
    }

    public ItemStack create() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString(BOOK_ID, id);
        tag.putString("title", id);
        tag.putString("author", prefix + ".author");
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);
        ListTag content = new ListTag();
        for (int page = 1; page <= pages; page++) {
            String key = pagePrefix() + "." + page;
            content.add(translatedPage(key));
            if (secondSheets.contains(page)) content.add(translatedPage(key + ".2"));
            if (thirdSheets.contains(page)) content.add(translatedPage(key + ".3"));
        }
        tag.put("pages", content);
        book.setHoverName(Component.translatable(prefix + ".title"));
        return book;
    }

    private static StringTag translatedPage(String key) {
        return StringTag.valueOf(Component.Serializer.toJson(Component.translatable(key)));
    }

    /** The same decision is available even to a hopper that unpacks the chest before the camp registers. */
    public static boolean selectNew(ServerLevel level, BlockPos chest) {
        long positionSeed = net.minecraft.util.Mth.getSeed(chest);
        long seed = level.getSeed() ^ positionSeed ^ ((long) level.dimension().location().hashCode() << 32);
        return RandomSource.create(seed ^ 0x6C42A6B31974F20DL).nextInt(100) < 40;
    }

    /** Init and native unpack share the same stored choice, in either order. */
    public static boolean registerChoice(ServerLevel level, BlockPos pos, ResourceLocation expectedTable) {
        if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest
                && (expectedTable.equals(((OutpostLootContainerAccessor) chest).callresponse$getLootTable())
                    || expectedTable.toString().equals(chest.getPersistentData().getString(TABLE_RECEIPT)))) {
            return selectedStory(level, chest);
        }
        return selectNew(level, pos);
    }

    private static boolean selectedStory(ServerLevel level, RandomizableContainerBlockEntity chest) {
        CompoundTag data = chest.getPersistentData();
        if (!data.contains(SELECTED_NEW, Tag.TAG_BYTE)) {
            data.putBoolean(SELECTED_NEW, selectNew(level, chest.getBlockPos()));
            chest.setChanged();
        }
        return data.getBoolean(SELECTED_NEW);
    }

    public static void fill(LootTable table, Container container, LootParams params, long seed) {
        ResourceLocation tableId = table.getLootTableId();
        boolean wandering = WANDERING_TABLE.equals(tableId);
        if ((!wandering && !OUTPOST_TABLE.equals(tableId))
                || !(container instanceof RandomizableContainerBlockEntity chest)
                || !(chest.getLevel() instanceof ServerLevel level)) {
            table.fill(container, params, seed);
            return;
        }
        // At this native call site the pending table has already been cleared. Reading slots
        // cannot unpack it again. Only look at slots written by this fill, never a player's book.
        BitSet empty = new BitSet(chest.getContainerSize());
        for (int slot = 0; slot < chest.getContainerSize(); slot++)
            if (chest.getItem(slot).isEmpty()) empty.set(slot);
        boolean alreadyFilled = tableId.toString().equals(chest.getPersistentData().getString(TABLE_RECEIPT));
        table.fill(container, params, seed);

        StringTag originalFirst = translatedPage((wandering ? WANDERING_ORIGINAL : OUTPOST_ORIGINAL).pagePrefix() + ".1");
        for (int slot = empty.nextSetBit(0); slot >= 0; slot = empty.nextSetBit(slot + 1)) {
            ItemStack generated = chest.getItem(slot);
            if (!generated.is(Items.WRITTEN_BOOK) || !generated.hasTag()) continue;
            ListTag list = generated.getTag().getList("pages", Tag.TAG_STRING);
            boolean regular = (!list.isEmpty() && originalFirst.equals(list.get(0)))
                    || (wandering ? WANDERING_SAGE.matches(generated) || WANDERING_GLY.matches(generated)
                                  : OUTPOST_RECRUITED.matches(generated));
            boolean extra = !wandering && OUTPOST_GLY.matches(generated);
            if (!regular && !extra) continue;
            if (alreadyFilled) {
                chest.setItem(slot, ItemStack.EMPTY);
            } else {
                if (wandering && !WANDERING_GLY.matches(generated) && !WANDERING_SAGE.matches(generated))
                    chest.setItem(slot, com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampStory
                            .onNativeDiary(level, chest, slot, generated));
                if (extra) {
                    var instance = BetrayalOutpostManager.storyInstance(level, chest.getBlockPos());
                    if (instance != null) chest.getPersistentData().putUUID(OUTPOST_GLY_RECEIPT, instance);
                }
                chest.getPersistentData().putString(TABLE_RECEIPT, tableId.toString());
            }
            chest.setChanged();
        }
    }
}
