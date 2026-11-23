package com.github.JumDa5he.callresponse.compat.story;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampStory;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/** Selects the story for the chest at the loot origin, including per-player inventories. */
public record CampStoryCondition(Book book) implements LootItemCondition {
    private static final MapCodec<CampStoryCondition> CODEC = StringRepresentable.fromEnum(Book::values)
            .fieldOf("book").xmap(CampStoryCondition::new, CampStoryCondition::book);
    public static final DeferredRegister<LootItemConditionType> TYPES =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, CallResponseMod.MOD_ID);
    private static final Supplier<LootItemConditionType> TYPE =
            TYPES.register("camp_story", () -> new LootItemConditionType(CODEC));

    public enum Book implements StringRepresentable {
        WANDERING_CAMP_DIARY, WANDERING_SAGE_DIARY, WANDERING_GLY_DIARY,
        OUTPOST_STORY, OUTPOST_STORY_RECRUITED, OUTPOST_STORY_GLY;

        @Override public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override public LootItemConditionType getType() {
        return TYPE.get();
    }

    @Override public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override public boolean test(LootContext context) {
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return book == Book.WANDERING_CAMP_DIARY || book == Book.OUTPOST_STORY;
        BlockPos pos = BlockPos.containing(origin);
        ServerLevel level = context.getLevel();
        return switch (book) {
            case WANDERING_CAMP_DIARY, WANDERING_SAGE_DIARY, WANDERING_GLY_DIARY -> {
                String page = WanderingCampStory.lootStoryPage(level, pos);
                if (CampStoryBooks.WANDERING_GLY.pagePrefix().equals(page)) yield book == Book.WANDERING_GLY_DIARY;
                if (CampStoryBooks.WANDERING_SAGE.pagePrefix().equals(page)) yield book == Book.WANDERING_SAGE_DIARY;
                yield book == Book.WANDERING_CAMP_DIARY;
            }
            case OUTPOST_STORY -> !BetrayalOutpostManager.useRecruitedStory(level, pos);
            case OUTPOST_STORY_RECRUITED -> BetrayalOutpostManager.useRecruitedStory(level, pos);
            case OUTPOST_STORY_GLY -> BetrayalOutpostManager.hasStoryGly(level, pos);
        };
    }
}
