package com.github.JumDa5he.callresponse.compat.story;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampStory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import java.util.ArrayList;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.BitSet;
import java.util.Set;

/** Native written books: page components stay translatable on the reading player's client. */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public enum CampStoryBooks {
    WANDERING_SAGE("wandering_sage_diary", 28),
    OUTPOST_RECRUITED("outpost_story_recruited", 38),
    WANDERING_GLY("wandering_gly_diary", 41, Set.of(4, 31, 33, 34, 35, 36), Set.of(34)),
    OUTPOST_GLY("outpost_story_gly", 15, Set.of(13), Set.of());

    public static final String TABLE_RECEIPT = "callresponse:camp_story_table";
    private static final String BOOK_ID = "CallResponseCampStory";
    private static final String SELECTED_NEW = "callresponse:camp_story_new";
    private static final ResourceLocation WANDERING_TABLE = ResourceLocation.fromNamespaceAndPath("callresponse", "chests/wandering_maid_camp");
    private static final ResourceLocation OUTPOST_TABLE = ResourceLocation.fromNamespaceAndPath("callresponse", "chests/betrayal_outpost_story");
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

    public boolean matches(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && id.equals(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(BOOK_ID));
    }

    public static Component author(ItemStack stack) {
        for (CampStoryBooks story : values())
            if (story.matches(stack)) return Component.translatable(story.prefix + ".author");
        return null;
    }

    public ItemStack create() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.putString(BOOK_ID, id));
        var content = new ArrayList<Filterable<Component>>();
        for (int page = 1; page <= pages; page++) {
            String key = pagePrefix() + "." + page;
            content.add(translatedPage(key));
            if (secondSheets.contains(page)) content.add(translatedPage(key + ".2"));
            if (thirdSheets.contains(page)) content.add(translatedPage(key + ".3"));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(id), prefix + ".author", 0, content, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable(prefix + ".title"));
        return book;
    }

    private static Filterable<Component> translatedPage(String key) {
        return Filterable.passThrough(Component.translatable(key));
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
                && (chest.getLootTable() != null && expectedTable.equals(chest.getLootTable().location())
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

    public static boolean isPersonalChest(BlockEntity entity) {
        return entity != null && "lootr".equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    /** A personal inventory is saved on first open. Wait for an unresolved camp before generating it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPersonalChestOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)
                || !isPersonalChest(chest) || chest.getLootTable() == null) return;
        ResourceLocation table = chest.getLootTable().location();
        if ((WANDERING_TABLE.equals(table) && WanderingCampStory.lootStoryPage(level, pos) == null)
                || (OUTPOST_TABLE.equals(table) && BetrayalOutpostManager.storyPending(level, pos))) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.translatable("message.callresponse.camp.story_pending"), true);
        }
    }

    public static void fill(LootTable table, Container container, LootParams params, long seed) {
        ResourceLocation tableId = table.getLootTableId();
        boolean wandering = WANDERING_TABLE.equals(tableId);
        if ((!wandering && !OUTPOST_TABLE.equals(tableId))
                || !(container instanceof RandomizableContainerBlockEntity chest)
                || !(chest.getLevel() instanceof ServerLevel level) || isPersonalChest(chest)) {
            table.fill(container, params, seed);
            return;
        }
        // At this native call site the pending table has already been cleared. Reading slots
        // cannot unpack it again. Only look at slots written by this fill, never a player's book.
        BitSet empty = new BitSet(chest.getContainerSize());
        boolean alreadyFilled = tableId.toString().equals(chest.getPersistentData().getString(TABLE_RECEIPT));
        for (int slot = 0; slot < chest.getContainerSize(); slot++)
            if (chest.getItem(slot).isEmpty()) empty.set(slot);
        table.fill(container, params, seed);

        Component originalFirst = Component.translatable(wandering ? "book.callresponse.wandering_camp_diary.page.1"
                : "book.callresponse.outpost_story.page.1");
        for (int slot = empty.nextSetBit(0); slot >= 0; slot = empty.nextSetBit(slot + 1)) {
            ItemStack generated = chest.getItem(slot);
            if (!generated.is(Items.WRITTEN_BOOK)) continue;
            WrittenBookContent content = generated.get(DataComponents.WRITTEN_BOOK_CONTENT);
            boolean original = content != null && !content.pages().isEmpty() && originalFirst.equals(content.pages().getFirst().raw());
            boolean gly = (wandering ? WANDERING_GLY : OUTPOST_GLY).matches(generated);
            if (!original && !gly && !(wandering ? WANDERING_SAGE : OUTPOST_RECRUITED).matches(generated)) continue;
            if (alreadyFilled) {
                chest.setItem(slot, ItemStack.EMPTY);
            } else {
                if (wandering && original) {
                    chest.setItem(slot, WanderingCampStory.onNativeDiary(level, chest, slot, generated));
                } else if (!wandering && gly) {
                    BetrayalOutpostManager.markTableGly(level, chest);
                }
                chest.getPersistentData().putString(TABLE_RECEIPT, tableId.toString());
            }
            chest.setChanged();
        }
    }
}
