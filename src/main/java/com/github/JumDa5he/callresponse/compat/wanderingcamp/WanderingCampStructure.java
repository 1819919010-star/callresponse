package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import java.util.Optional;

/** Use the vanilla pool/piece/placement pipeline; validate its actual rotated footprint BEFORE placement. */
public final class WanderingCampStructure extends Structure {
    public static final Codec<WanderingCampStructure> CODEC = simpleCodec(WanderingCampStructure::new);
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, CallResponseMod.MOD_ID);
    public static final RegistryObject<StructureType<WanderingCampStructure>> TYPE = TYPES.register("wandering_maid_camp", () -> () -> CODEC);
    public WanderingCampStructure(StructureSettings settings) { super(settings); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var pool = context.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).getHolderOrThrow(
                ResourceKey.create(Registries.TEMPLATE_POOL, new ResourceLocation(CallResponseMod.MOD_ID,"wandering_maid_camp/start_pool")));
        // Extra technical layer is below the intact original floor. Offset -1 compensates rebasing.
        var candidate = JigsawPlacement.addPieces(context, pool, Optional.empty(), 1,
                new BlockPos(context.chunkPos().getMinBlockX(), -1, context.chunkPos().getMinBlockZ()),
                false, Optional.of(Heightmap.Types.WORLD_SURFACE_WG), 32);
        if (candidate.isEmpty()) return Optional.empty();
        var stub = candidate.get();
        var builder = stub.getPiecesBuilder();
        var pieces = builder.build().pieces();
        if (pieces.size() != 1) return Optional.empty();
        var box = pieces.get(0).getBoundingBox();
        int floorY = box.minY()+1;
        // Noise columns do not load chunks or inspect water far below otherwise sound land.
        for (int x=box.minX(); x<=box.maxX(); x++) for (int z=box.minZ(); z<=box.maxZ(); z++) {
            int top = context.chunkGenerator().getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(),context.randomState());
            var column = context.chunkGenerator().getBaseColumn(x,z,context.heightAccessor(),context.randomState());
            if (!validColumn(column,top,floorY)) return Optional.empty();
        }
        return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
    }
    public static boolean validColumn(BlockColumn column, int top, int floorY) {
        if (!drySurface(column, top)) return false;
        int landY=top;
        for (int n=0;n<4 && (column.getBlock(landY).is(Blocks.SNOW) || column.getBlock(landY).is(Blocks.SNOW_BLOCK));n++) landY--;
        var land=column.getBlock(landY);
        if (Math.abs(landY-floorY)>1 || !land.canOcclude() || land.is(BlockTags.LEAVES)) return false;
        for (int y : new int[]{landY, floorY, floorY-1}) {
            var state=column.getBlock(y);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.ICE) || state.is(Blocks.POWDER_SNOW)) return false;
        }
        return column.getBlock(floorY-1).canOcclude();
    }
    /** Shared surface-only check: buried water does not disqualify otherwise solid land. */
    public static boolean drySurface(BlockColumn column, int top) {
        int landY = top;
        for (int n=0; n<4 && (column.getBlock(landY).is(Blocks.SNOW) || column.getBlock(landY).is(Blocks.SNOW_BLOCK)); n++) landY--;
        var land = column.getBlock(landY);
        return land.canOcclude() && !land.is(BlockTags.LEAVES) && land.getFluidState().isEmpty()
                && !land.is(BlockTags.ICE) && !land.is(Blocks.POWDER_SNOW);
    }
    @Override public StructureType<?> type() { return TYPE.get(); }
}
