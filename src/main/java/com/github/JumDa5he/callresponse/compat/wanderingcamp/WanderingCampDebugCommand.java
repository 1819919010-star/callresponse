package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostMarkerBlockEntity;
import com.github.JumDa5he.callresponse.compat.outpost.OutpostAnchorBlock;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public final class WanderingCampDebugCommand {
    private WanderingCampDebugCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("wandering_maid_camp").requires(s -> s.hasPermission(2))
                .executes(c -> run(c.getSource(), false))
                .then(Commands.argument("force_gly", BoolArgumentType.bool()).executes(c ->
                        run(c.getSource(), BoolArgumentType.getBool(c,"force_gly"))));
    }
    private static int run(CommandSourceStack source, boolean forceGly) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        // As with the existing outpost command, the player chooses the site; their feet are floor surface.
        boolean success=generate(player.serverLevel(),player.blockPosition().below(2),Rotation.NONE,forceGly);
        if (success) source.sendSuccess(() -> Component.translatable("command.callresponse.wandering_camp.generated",forceGly),false);
        else source.sendFailure(Component.translatable("command.callresponse.wandering_camp.failed"));
        return success ? 1 : 0;
    }
    public static boolean generate(ServerLevel level, BlockPos origin, Rotation rotation, boolean forceGly) {
        var settings=new StructurePlaceSettings().setRotation(rotation);
        BlockPos head=origin.offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                .calculateRelativePosition(settings,new BlockPos(3,2,1)));
        BlockPos anchor=head.below(2);
        String id=level.dimension().location()+"@"+head.asLong();
        var ledger=WanderingCampSavedData.get(level);
        var entry=ledger.entry(id);
        if (entry.getBoolean("Spawned") || entry.contains("Gly") || entry.hasUUID("Maid")) return false;
        // Placement and initialization run in one server command, before any anchor tick can intervene.
        var template=level.getStructureManager().getOrCreate(new ResourceLocation("callresponse","wandering_maid_camp"));
        if (template.getSize().getY()!=7) return false;
        if (!template.placeInWorld(level,origin,origin,settings,level.random,3)) return false;
        if (!(level.getBlockEntity(anchor) instanceof OutpostMarkerBlockEntity marker) || !marker.isWanderingCamp()) return false;
        WanderingCampManager.generate(level,anchor,head,level.getBlockState(anchor).getValue(OutpostAnchorBlock.FACING),forceGly);
        return entry.getBoolean("Spawned");
    }
}
