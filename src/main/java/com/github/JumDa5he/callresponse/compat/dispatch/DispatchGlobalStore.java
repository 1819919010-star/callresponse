package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 可选跨存档回收的存档外镜像。NBT 以 SNBT 字符串安全放进 JSON。 */
public final class DispatchGlobalStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("callresponse").resolve("dispatch_global.json");
    private static final Path CLAIMED_FILE = FMLPaths.CONFIGDIR.get().resolve("callresponse").resolve("dispatch_global_claimed.json");

    private DispatchGlobalStore() {
    }

    public static synchronized void put(DispatchData.DispatchRecord record, HolderLookup.Provider provider) {
        Map<UUID, DispatchData.DispatchRecord> all = read(provider);
        all.put(record.dispatchId(), record);
        write(all, provider);
        Set<UUID> claimed = readClaimed();
        if (claimed.remove(record.dispatchId())) {
            writeClaimed(claimed);
        }
    }

    public static synchronized void remove(UUID dispatchId, HolderLookup.Provider provider) {
        Map<UUID, DispatchData.DispatchRecord> all = read(provider);
        if (all.remove(dispatchId) != null) {
            write(all, provider);
        }
        Set<UUID> claimed = readClaimed();
        claimed.add(dispatchId);
        writeClaimed(claimed);
    }

    public static synchronized boolean contains(UUID dispatchId, HolderLookup.Provider provider) {
        return read(provider).containsKey(dispatchId);
    }

    public static synchronized boolean isClaimed(UUID dispatchId) {
        return readClaimed().contains(dispatchId);
    }

    public static synchronized List<DispatchData.DispatchRecord> owner(UUID owner, HolderLookup.Provider provider) {
        return read(provider).values().stream().filter(record -> record.ownerId().equals(owner)).toList();
    }

    private static Map<UUID, DispatchData.DispatchRecord> read(HolderLookup.Provider provider) {
        Map<UUID, DispatchData.DispatchRecord> result = new LinkedHashMap<>();
        if (!Files.isRegularFile(FILE)) {
            return result;
        }
        try {
            JsonArray array = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("dispatches");
            if (array == null) {
                return result;
            }
            array.forEach(value -> {
                try {
                    JsonObject entry = value.getAsJsonObject();
                    DispatchData.DispatchRecord record = DispatchData.DispatchRecord.load(
                            TagParser.parseCompoundFully(entry.get("record").getAsString()));
                    result.put(record.dispatchId(), record);
                } catch (Exception exception) {
                    CallResponseMod.LOGGER.error("跳过损坏的跨存档派遣记录", exception);
                }
            });
        } catch (Exception exception) {
            CallResponseMod.LOGGER.error("读取跨存档派遣文件失败", exception);
        }
        return result;
    }

    private static void write(Map<UUID, DispatchData.DispatchRecord> records, HolderLookup.Provider provider) {
        try {
            Files.createDirectories(FILE.getParent());
            JsonArray array = new JsonArray();
            records.values().forEach(record -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("dispatchId", record.dispatchId().toString());
                entry.addProperty("ownerId", record.ownerId().toString());
                entry.addProperty("record", record.save().toString());
                array.add(entry);
            });
            JsonObject root = new JsonObject();
            root.add("dispatches", array);
            Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            CallResponseMod.LOGGER.error("写入跨存档派遣文件失败", exception);
        }
    }

    private static Set<UUID> readClaimed() {
        Set<UUID> result = new LinkedHashSet<>();
        if (!Files.isRegularFile(CLAIMED_FILE)) {
            return result;
        }
        try {
            JsonParser.parseString(Files.readString(CLAIMED_FILE, StandardCharsets.UTF_8))
                    .getAsJsonArray().forEach(value -> result.add(UUID.fromString(value.getAsString())));
        } catch (Exception exception) {
            CallResponseMod.LOGGER.error("读取跨存档已回收标记失败", exception);
        }
        return result;
    }

    private static void writeClaimed(Set<UUID> claimed) {
        try {
            Files.createDirectories(CLAIMED_FILE.getParent());
            JsonArray array = new JsonArray();
            claimed.forEach(id -> array.add(id.toString()));
            Files.writeString(CLAIMED_FILE, GSON.toJson(array), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            CallResponseMod.LOGGER.error("写入跨存档已回收标记失败", exception);
        }
    }
}
