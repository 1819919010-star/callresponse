package com.github.JumDa5he.callresponse.compat.gift;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 拆开礼物盒：抽内容 → 发物品 → 触发陷阱 → 女仆 AI 台词。
 */
public final class GiftService {
    private static final double SPEAK_RANGE = 48.0D;
    private static final String GARAGE_KIT_ID = "touhou_little_maid:garage_kit";

    private GiftService() {
    }

    public static void open(ServerPlayer opener, GiftBoxData data) {
        if (!(opener.level() instanceof ServerLevel level)) {
            return;
        }
        GiftKind kind = data.kind();
        List<ItemStack> contents = resolveContents(level, opener, data);

        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(opener, stack);
            }
        }

        level.playSound(null, opener.getX(), opener.getY(), opener.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.3F);

        EntityMaid giver = findGiver(level, opener, data);
        EmotionData.EmotionTendency tendency = giver != null
                ? EmotionData.getTendency(giver, opener.getUUID())
                : EmotionData.EmotionTendency.NEUTRAL;

        if (kind.isTrap()) {
            GiftTraps.apply(kind, new GiftTraps.TrapContext(level, opener.position(), opener, giver, tendency));
            if (giver != null) {
                // 动手做了坏事，也怕主人报复
                EmotionData.addFear(giver, opener.getUUID(), kind == GiftKind.PRANK ? 1 : 3);
            }
        } else if (giver != null) {
            EmotionData.addTrust(giver, opener.getUUID(), 1);
        }

        speak(opener, data, giver, kind, contents);
    }

    private static List<ItemStack> resolveContents(ServerLevel level, ServerPlayer opener, GiftBoxData data) {
        List<ItemStack> items = new ArrayList<>();
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, data.kind().lootTable());
        try {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            if (table != null && table != LootTable.EMPTY) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, opener)
                        .withParameter(LootContextParams.ORIGIN, opener.position())
                        .create(LootContextParamSets.ADVANCEMENT_REWARD);
                items.addAll(table.getRandomItems(params));
            }
        } catch (Exception exception) {
            com.github.JumDa5he.callresponse.CallResponseMod.LOGGER.warn("[礼物] 读取战利品表 {} 失败", data.kind().lootTable(), exception);
        }

        if (data.kind() == GiftKind.HANDMADE) {
            ItemStack kit = createGarageKit(data.modelId());
            if (!kit.isEmpty()) {
                items.add(kit);
            }
        }

        if (items.isEmpty()) {
            items.add(new ItemStack(Items.BREAD));
        }
        return items;
    }

    /** 拆出来的手办用送礼女仆自己的模型。 */
    private static ItemStack createGarageKit(String modelId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(GARAGE_KIT_ID));
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (modelId != null && !modelId.isEmpty()) {
            stack.set(InitDataComponent.MODEL_ID_TAG.get(), modelId);
        }
        return stack;
    }

    private static EntityMaid findGiver(ServerLevel level, ServerPlayer opener, GiftBoxData data) {
        UUID id = data.senderUuid();
        if (id == null) {
            return null;
        }
        List<EntityMaid> maids = level.getEntitiesOfClass(EntityMaid.class,
                opener.getBoundingBox().inflate(SPEAK_RANGE), maid -> maid.getUUID().equals(id));
        return maids.isEmpty() ? null : maids.get(0);
    }

    /** AI 台词只在拆盒时触发。 */
    private static void speak(ServerPlayer opener, GiftBoxData data, EntityMaid giver, GiftKind kind, List<ItemStack> contents) {
        if (giver == null || !giver.isAlive()) {
            return;
        }
        if (giver.distanceToSqr(opener) > SPEAK_RANGE * SPEAK_RANGE) {
            return;
        }
        String names = contents.stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.getHoverName().getString())
                .collect(Collectors.joining("、"));
        if (names.isEmpty()) {
            names = "一些杂物";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("主人刚刚拆开了你送的礼物盒，里面是：").append(names).append("。");
        if (kind.isTrap()) {
            prompt.append("其实盒子里是你准备的").append(kind.displayName().getString()).append("，现在已经在主人面前触发了。");
        }
        prompt.append(EmotionData.getTendencyPromptSuffix(giver, opener.getUUID()));
        prompt.append("根据你当前的情感状态，说几句话表达你此刻的心情。");

        MaidResponder.processBroadcast(opener, List.of(giver), prompt.toString(), false);
    }
}
