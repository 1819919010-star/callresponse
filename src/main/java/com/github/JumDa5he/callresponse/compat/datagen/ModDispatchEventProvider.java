package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventProvider;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchEventDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class ModDispatchEventProvider extends DispatchEventProvider {
    public ModDispatchEventProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                    ExistingFileHelper existingFileHelper) {
        super(output, CallResponseMod.MOD_ID, lookupProvider, existingFileHelper);
    }

    @Override
    public void gatherDispatchEvent(Saver saver) {
        DispatchEventBuilder.builder("bakery_assist")
                .category(DispatchEventDefinition.Category.WORK)
                .title("面包房帮厨")
                .description("面包房今天订单很多，邀请女仆去搭把手。")
                .duration(10, 30)
                .emotion(1, -3, 5, 4)
                .weight(12)
                .cooldown(15)
                .item(Items.BREAD, 32, 64, 10)
                .item(Items.CAKE, 2, 4, 4)
                .item(Items.COOKIE, 24, 32, 8)
                .save(saver);

        DispatchEventBuilder.builder("festival_visit")
                .category(DispatchEventDefinition.Category.PLAY)
                .title("村庄小庆典")
                .description("附近村庄正在举办热闹的小庆典，去逛逛摊位。")
                .duration(20, 40)
                .emotion(4, -2, 6, 6)
                .weight(8)
                .cooldown(60)
                .item(Items.APPLE, 16, 24, 10)
                .item(Items.FIREWORK_ROCKET, 10, 20, 6)
                .item(Items.EMERALD, 7, 14, 4)
                .save(saver);

        DispatchEventBuilder.builder("fishing_day")
                .category(DispatchEventDefinition.Category.PLAY)
                .title("河边垂钓")
                .description("带上鱼竿，在安静的河边消磨半天。")
                .duration(10, 20)
                .emotion(1, -1, 2, -4)
                .weight(9)
                .cooldown(30)
                .item(Items.COD, 16, 32, 10)
                .item(Items.SALMON, 15, 30, 7)
                .enchant(ResourceLocation.withDefaultNamespace("luck_of_the_sea"), 1, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("flower_trip")
                .category(DispatchEventDefinition.Category.PLAY)
                .title("花海散步")
                .description("去远处的花海慢慢走一圈，顺手采些漂亮的花。")
                .duration(10, 25)
                .emotion(2, -2, 15, -3)
                .weight(10)
                .cooldown(20)
                .item(Items.POPPY, 4, 12, 10)
                .item(Items.CORNFLOWER, 3, 9, 8)
                .item(Items.HONEY_BOTTLE, 6, 12, 3)
                .save(saver);

        DispatchEventBuilder.builder("forest_picnic")
                .category(DispatchEventDefinition.Category.PLAY)
                .title("林间野餐")
                .description("找一片树荫铺好餐布，安静享受难得的休息。")
                .duration(15, 25)
                .emotion(3, -3, 12, 9)
                .weight(11)
                .cooldown(20)
                .item(Items.APPLE, 7, 14, 10)
                .item(Items.SWEET_BERRIES, 10, 20, 8)
                .item(Items.OAK_SAPLING, 1, 4, 4)
                .save(saver);

        DispatchEventBuilder.builder("library_sort")
                .category(DispatchEventDefinition.Category.WORK)
                .title("图书馆整理委托")
                .description("旧书堆得太乱了，需要细心的人重新分类登记。")
                .duration(20, 40)
                .emotion(1, -2, 3, -5)
                .weight(9)
                .cooldown(20)
                .item(Items.BOOK, 16, 32, 10)
                .item(Items.EXPERIENCE_BOTTLE, 32, 64, 5)
                .enchant(ResourceLocation.withDefaultNamespace("unbreaking"), 1, 3, 3)
                .save(saver);

        DispatchEventBuilder.builder("maiddoll_model")
                .category(DispatchEventDefinition.Category.WORK)
                .title("成为女仆布娃娃模特")
                .description("某地正在进行女仆布娃娃的制作，需要一些模特来做样式")
                .duration(30, 50)
                .emotion(2, 1, 2, -10)
                .weight(6)
                .cooldown(90)
                .item(Items.CAKE, 1, 2, 3)
                .item(Items.DIAMOND, 5, 12, 10)
                .item(Items.GOLD_BLOCK, 1, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("merchant_guard")
                .category(DispatchEventDefinition.Category.WORK)
                .title("商队短程护送")
                .description("商队要穿过一段危险道路，请女仆同行照应。")
                .duration(45, 60)
                .emotion(2, -1, 6, -15)
                .weight(7)
                .cooldown(90)
                .item(Items.EMERALD, 48, 64, 10)
                .item(Items.GOLDEN_APPLE, 13, 20, 3)
                .enchant(ResourceLocation.withDefaultNamespace("protection"), 2, 4, 2)
                .save(saver);

        DispatchEventBuilder.builder("mine_survey")
                .category(DispatchEventDefinition.Category.WORK)
                .title("矿洞物资勘察")
                .description("替矿工检查旧矿道，并把仍可使用的矿物带回来。")
                .duration(20, 40)
                .emotion(1, 1, 2, -15)
                .weight(8)
                .cooldown(60)
                .item(Items.IRON_INGOT, 32, 64, 10)
                .item(Items.GOLD_INGOT, 16, 32, 6)
                .item(Items.DIAMOND, 10, 20, 2)
                .save(saver);

        DispatchEventBuilder.builder("nether_expedition")
                .category(DispatchEventDefinition.Category.WORK)
                .title("参加地狱探险队")
                .description("加入地狱探险队深入下界，协助队伍清理烈焰人与凋零骷髅并搜集稀有物资。")
                .duration(45, 60)
                .emotion(2, 3, 4, -14)
                .weight(6)
                .cooldown(90)
                .item(Items.WITHER_SKELETON_SKULL, 1, 2, 3)
                .item(Items.BLAZE_ROD, 5, 12, 10)
                .item(Items.NETHERITE_SCRAP, 1, 3, 2)
                .save(saver);

        DispatchEventBuilder.builder("snow_view")
                .category(DispatchEventDefinition.Category.PLAY)
                .title("雪原看风景")
                .description("去雪原看看白茫茫的景色，捎回一些旅途纪念。")
                .duration(15, 30)
                .emotion(3, -1, 6, -8)
                .weight(7)
                .cooldown(60)
                .item(Items.SNOWBALL, 12, 16, 10)
                .item(Items.BLUE_ICE, 16, 32, 3)
                .item(Items.RABBIT_HIDE, 8, 16, 5)
                .save(saver);

        DispatchEventBuilder.builder("village_help")
                .category(DispatchEventDefinition.Category.WORK)
                .title("村庄临时帮工")
                .description("村民忙不过来，请女仆帮忙整理货物和照看农田。")
                .duration(10, 20)
                .emotion(1, -4, 2, -10)
                .weight(10)
                .cooldown(30)
                .item(Items.EMERALD, 16, 32, 10)
                .item(Items.BREAD, 12, 24, 6)
                .enchant(ResourceLocation.withDefaultNamespace("efficiency"), 2, 4, 2)
                .save(saver);
    }
}
