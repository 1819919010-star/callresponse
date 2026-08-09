package com.github.tartaricacid.callresponse.compat.hunt;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 狩猎令中的一个目标条目。存储于女仆 PersistentData 的 "CallResponseHuntOrder" 中。
 * 玩家条目名字+UUID 都指向同一玩家时算同一个任务目标（规格要求两者都填）。
 */
public class HuntOrderEntry {
    public final UUID uuid;
    public String name;
    public boolean isPlayer;

    public HuntOrderEntry(UUID uuid, String name, boolean isPlayer) {
        this.uuid = uuid;
        this.name = name;
        this.isPlayer = isPlayer;
    }

    public HuntOrderEntry(CompoundTag tag) {
        this.uuid = UUID.fromString(tag.getString("U"));
        this.name = tag.getString("N");
        this.isPlayer = tag.getBoolean("P");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("U", uuid.toString());
        tag.putString("N", name);
        tag.putBoolean("P", isPlayer);
        return tag;
    }
}