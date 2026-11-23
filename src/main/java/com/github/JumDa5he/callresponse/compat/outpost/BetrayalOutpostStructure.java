package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampStructure;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.Optional;

/** The existing one-piece vanilla jigsaw placement, with a pre-placement dry-land gate. */
public final class BetrayalOutpostStructure extends Structure {
    public static final MapCodec<BetrayalOutpostStructure> CODEC = simpleCodec(BetrayalOutpostStructure::new);
    public static final DeferredHolder<StructureType<?>, StructureType<BetrayalOutpostStructure>> TYPE =
            WanderingCampStructure.TYPES.register("betrayal_maid_outpost", () -> () -> CODEC);
    public BetrayalOutpostStructure(StructureSettings settings) { super(settings); }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var pool = context.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).getHolderOrThrow(
                ResourceKey.create(Registries.TEMPLATE_POOL,
                        ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "betrayal_maid_outpost/start_pool")));
        var candidate = JigsawPlacement.addPieces(context, pool, Optional.empty(), 1,
                new BlockPos(context.chunkPos().getMinBlockX(), 0, context.chunkPos().getMinBlockZ()),
                false, Optional.of(Heightmap.Types.WORLD_SURFACE_WG), 80,
                net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup.EMPTY,
                net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.DEFAULT_DIMENSION_PADDING,
                net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.DEFAULT_LIQUID_SETTINGS);
        if (candidate.isEmpty()) return Optional.empty();
        var stub = candidate.get();
        var builder = stub.getPiecesBuilder();
        var pieces = builder.build().pieces();
        if (pieces.isEmpty()) return Optional.empty();
        // Check the real rotated support footprint, not a biome name or a geometric center.
        for (var piece : pieces) {
            var box = piece.getBoundingBox();
            for (int x=box.minX(); x<=box.maxX(); x++) for (int z=box.minZ(); z<=box.maxZ(); z++) {
                int top = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                var column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
                if (!WanderingCampStructure.drySurface(column, top)) return Optional.empty();
            }
        }
        return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }
    @Override public StructureType<?> type() { return TYPE.get(); }
}
