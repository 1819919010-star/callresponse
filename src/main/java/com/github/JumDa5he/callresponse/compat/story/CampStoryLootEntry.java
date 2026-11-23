package com.github.JumDa5he.callresponse.compat.story;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampStory;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/** Evaluated for each inventory, using the camp at the chest's loot origin. */
public final class CampStoryLootEntry extends LootPoolSingletonContainer {
    public static final DeferredRegister<LootPoolEntryType> TYPES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, CallResponseMod.MOD_ID);
    private static final RegistryObject<LootPoolEntryType> TYPE =
            TYPES.register("camp_story", () -> new LootPoolEntryType(new StorySerializer()));
    private final CampStoryBooks book;

    private CampStoryLootEntry(CampStoryBooks book, int weight, int quality,
                               LootItemCondition[] conditions, LootItemFunction[] functions) {
        super(weight, quality, conditions, functions);
        this.book = book;
    }

    @Override public LootPoolEntryType getType() { return TYPE.get(); }

    @Override public boolean expand(LootContext context, Consumer<LootPoolEntry> entries) {
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return original() && super.expand(context, entries);
        var pos = BlockPos.containing(origin);
        var level = context.getLevel();
        boolean selected = switch (book) {
            case WANDERING_ORIGINAL, WANDERING_SAGE, WANDERING_GLY -> book == WanderingCampStory.lootStory(level, pos);
            case OUTPOST_ORIGINAL -> !BetrayalOutpostManager.usesRecruitedStory(level, pos);
            case OUTPOST_RECRUITED -> BetrayalOutpostManager.usesRecruitedStory(level, pos);
            case OUTPOST_GLY -> BetrayalOutpostManager.hasGlyStory(level, pos);
        };
        return selected && super.expand(context, entries);
    }

    private boolean original() {
        return book == CampStoryBooks.WANDERING_ORIGINAL || book == CampStoryBooks.OUTPOST_ORIGINAL;
    }

    @Override protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        // The two original books retain their existing data-pack NBT and title functions.
        output.accept(original() ? new ItemStack(Items.WRITTEN_BOOK) : book.create());
    }

    private static final class StorySerializer extends LootPoolSingletonContainer.Serializer<CampStoryLootEntry> {
        @Override public void serializeCustom(JsonObject json, CampStoryLootEntry entry, JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            json.addProperty("book", entry.book.lootId());
        }

        @Override protected CampStoryLootEntry deserialize(JsonObject json, JsonDeserializationContext context,
                int weight, int quality, LootItemCondition[] conditions, LootItemFunction[] functions) {
            String id = GsonHelper.getAsString(json, "book");
            for (var book : CampStoryBooks.values()) {
                if (book.lootId().equals(id)) return new CampStoryLootEntry(book, weight, quality, conditions, functions);
            }
            throw new JsonSyntaxException("Unknown camp story book: " + id);
        }
    }
}
