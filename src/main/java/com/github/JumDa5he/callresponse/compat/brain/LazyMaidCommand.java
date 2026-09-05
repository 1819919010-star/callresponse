package com.github.JumDa5he.callresponse.compat.brain;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

/** /callresponse lazy_need：逐项强制测试好吃懒做的五种日常需求。 */
public final class LazyMaidCommand {
    private LazyMaidCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lazy_need")
                .requires(source -> source.hasPermission(2));
        addNeed(root, "rest");
        addNeed(root, "owner_food");
        addNeed(root, "food_source");
        addNeed(root, "cake");
        addNeed(root, "ground_nap");
        return root;
    }

    private static void addNeed(LiteralArgumentBuilder<CommandSourceStack> root, String need) {
        root.then(Commands.literal(need)
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(context -> trigger(context, need))));
    }

    private static int trigger(CommandContext<CommandSourceStack> context, String need)
            throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "targets");
        int accepted = 0;
        for (Entity entity : entities) {
            if (entity instanceof EntityMaid maid && CustomExtraMaidBrain.requestLazyNeed(maid, need)) {
                accepted++;
            }
        }
        int result = accepted;
        context.getSource().sendSuccess(() -> Component.literal("[lazy_need] 已为 " + result
                + " 只好吃懒做女仆安排需求：" + need), false);
        return accepted;
    }
}
