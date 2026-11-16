package com.github.JumDa5he.callresponse.compat.gift;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * 女仆送出礼物的种类。
 * <p>
 * 正常礼物：{@link #SWEET} {@link #FLOWER} {@link #TRINKET} {@link #HANDMADE} {@link #TREASURE} {@link #STALE}<br>
 * 陷阱礼物：{@link #PRANK} {@link #SPAWN} {@link #TNT} {@link #BLAST} {@link #DEBUFF} {@link #GRUDGE}
 */
public enum GiftKind implements StringRepresentable {
    // ===== 正常礼物 =====
    SWEET("sweet", false, "gift/sweet"),
    FLOWER("flower", false, "gift/flower"),
    TRINKET("trinket", false, "gift/trinket"),
    HANDMADE("handmade", false, "gift/handmade"),
    TREASURE("treasure", false, "gift/treasure"),
    STALE("stale", false, "gift/stale"),

    // ===== 陷阱礼物 =====
    /** 无害恶作剧：喷雪/水花，纯玩闹，只有好感侧才会出现 */
    PRANK("prank", true, "gift/gag"),
    /** 惊吓盒：延迟刷出 1~3 只怪 */
    SPAWN("spawn", true, "gift/stale"),
    /** 引信盒：点燃延迟爆炸，只伤害开盒的玩家 */
    TNT("tnt", true, "gift/stale"),
    /** 立即小爆炸，不破坏方块 */
    BLAST("blast", true, "gift/stale"),
    /** 怨念盒：中毒 + 缓慢 */
    DEBUFF("debuff", true, "gift/stale"),
    /** 怨恨盒：刷怪 + 爆炸叠加 */
    GRUDGE("grudge", true, "gift/stale");

    public static final Codec<GiftKind> CODEC = StringRepresentable.fromEnum(GiftKind::values);

    private final String name;
    private final boolean trap;
    private final String lootPath;

    GiftKind(String name, boolean trap, String lootPath) {
        this.name = name;
        this.trap = trap;
        this.lootPath = lootPath;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    public boolean isTrap() {
        return trap;
    }

    /** 拆盒时用来抽取内容的战利品表 */
    public ResourceLocation lootTable() {
        return ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, lootPath);
    }

    /** 种类显示名，例如「甜食点心」 */
    public Component displayName() {
        return Component.translatable("gift.callresponse.kind." + name);
    }

    public static GiftKind byName(String name) {
        for (GiftKind kind : values()) {
            if (kind.name.equals(name)) {
                return kind;
            }
        }
        return STALE;
    }
}
