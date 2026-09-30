package com.github.JumDa5he.callresponse.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 一个已加载本地 TOML 的独立草稿。取消界面不接触 live config。 */
public final class NeoForgeConfigDraft {
    public record Field(List<String> path, ModConfigSpec.ValueSpec definition,
                        ModConfigSpec.ConfigValue<?> value, Object original) {}

    private final ModConfig config;
    private final ModConfigSpec spec;
    private final IConfigSpec.ILoadedConfig loaded;
    private final Path path;
    private final byte[] openedBytes;
    private final List<Field> fields = new ArrayList<>();
    private final Map<List<String>, Object> edits = new LinkedHashMap<>();

    public NeoForgeConfigDraft(ModConfig config) throws IOException {
        this.config = config;
        this.spec = (ModConfigSpec) config.getSpec();
        this.loaded = config.getLoadedConfig();
        if (!spec.isLoaded() || loaded == null || config.getFullPath() == null) {
            throw new IOException("Configuration is not a loaded local file");
        }
        path = config.getFullPath();
        openedBytes = Files.readAllBytes(path);
        collect(spec.getValues(), List.of());
    }

    private void collect(UnmodifiableConfig node, List<String> parent) {
        node.valueMap().forEach((key, value) -> {
            List<String> next = new ArrayList<>(parent);
            next.add(key);
            if (value instanceof UnmodifiableConfig child) collect(child, next);
            else if (value instanceof ModConfigSpec.ConfigValue<?> entry) {
                fields.add(new Field(List.copyOf(next), spec.getSpec().get(next), entry, copy(entry.get())));
            }
        });
    }

    public List<Field> fields() { return Collections.unmodifiableList(fields); }
    public void edit(Field field, Object value) { edits.put(field.path(), copy(value)); }
    private static Object copy(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : value;
    }

    /** 使用原 spec 校验；不查询物品注册表删除暂未安装模组的 ID。 */
    public void save() throws IOException {
        if (!spec.isLoaded() || config.getLoadedConfig() != loaded || !path.equals(config.getFullPath())) {
            throw new IOException("Configuration was unloaded or replaced; reopen the editor");
        }
        for (Field field : fields) {
            if (!field.definition().test(edits.getOrDefault(field.path(), field.original()))) {
                throw new IOException("Invalid option: " + field.path());
            }
            if (!Objects.equals(field.original(), field.value().get())) {
                throw new IOException("Live configuration changed; reopen the editor");
            }
        }
        if (fields.stream().noneMatch(f -> !Objects.equals(f.original(), edits.getOrDefault(f.path(), f.original())))) return;
        if (!Arrays.equals(openedBytes, Files.readAllBytes(path))) {
            throw new IOException("Configuration changed externally; reopen the editor");
        }
        Path staged = Files.createTempFile(path.getParent(), "callresponse-config-", ".toml");
        boolean replaced = false;
        try {
            Files.write(staged, openedBytes);
            try (CommentedFileConfig file = CommentedFileConfig.builder(staged).sync().build()) {
                file.load();
                for (Field field : fields) {
                    Object value = edits.getOrDefault(field.path(), field.original());
                    if (!Objects.equals(value, field.original())) file.set(field.path(), value);
                }
                file.save();
                file.load();
                if (!spec.isCorrect(file)) throw new IOException("Staged TOML failed NeoForge validation");
            }
            Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
            for (Field field : fields) {
                Object value = edits.getOrDefault(field.path(), field.original());
                if (!Objects.equals(value, field.original())) set(field, value);
            }
            // 原 spec 负责仅刷新无需重启的缓存。玩法配置在集成服务器线程应用。
            spec.afterReload();
        } catch (Exception failure) {
            if (replaced) {
                try {
                    Files.write(staged, openedBytes);
                    Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    for (Field field : fields) set(field, field.original());
                    spec.afterReload();
                } catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            throw new IOException("Could not save " + config.getFileName(), failure);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(Field field, Object value) {
        ((ModConfigSpec.ConfigValue) field.value()).set(copy(value));
    }
}
