package com.github.JumDa5he.callresponse.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A draft for one already loaded Forge file; no separate configuration system. */
public final class ForgeConfigDraft {
    public record Field(List<String> path, ForgeConfigSpec.ValueSpec definition,
                        ForgeConfigSpec.ConfigValue<?> value, Object original) {}

    private final ModConfig config;
    private final ForgeConfigSpec spec;
    private final List<Field> fields = new ArrayList<>();
    private final Map<List<String>, Object> edits = new LinkedHashMap<>();
    private final byte[] openedBytes;

    public ForgeConfigDraft(ModConfig config) throws IOException {
        this.config = config;
        this.spec = (ForgeConfigSpec) config.getSpec();
        if (!spec.isLoaded() || !(config.getConfigData() instanceof FileConfig)) {
            throw new IOException("Configuration is not a loaded local file");
        }
        openedBytes = Files.readAllBytes(config.getFullPath());
        collect(spec.getValues(), List.of());
    }

    private void collect(UnmodifiableConfig node, List<String> parent) {
        node.valueMap().forEach((key, value) -> {
            List<String> path = new ArrayList<>(parent);
            path.add(key);
            if (value instanceof UnmodifiableConfig child) {
                collect(child, path);
            } else if (value instanceof ForgeConfigSpec.ConfigValue<?> entry) {
                fields.add(new Field(List.copyOf(path), spec.getSpec().get(path), entry, copy(entry.get())));
            }
        });
    }

    public List<Field> fields() { return Collections.unmodifiableList(fields); }

    public void edit(Field field, Object value) { edits.put(field.path(), copy(value)); }

    private static Object copy(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : value;
    }

    /** Only runs after Save. Cancellation never writes either live values or disk. */
    public void save() throws IOException {
        if (!spec.isLoaded()) throw new IOException("Configuration was unloaded");
        for (Field field : fields) {
            Object value = edits.getOrDefault(field.path(), field.original());
            if (!field.definition().test(value)) throw new IOException("Invalid option: " + field.path());
        }
        if (fields.stream().noneMatch(f -> !Objects.equals(f.original(), edits.getOrDefault(f.path(), f.original())))) return;
        Path path = config.getFullPath();
        // Do not overwrite changes made by a text editor/another configuration screen while this draft was open.
        if (!Arrays.equals(openedBytes, Files.readAllBytes(path))) throw new IOException("Configuration changed externally; reopen the editor");
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
                if (!spec.isCorrect(file)) throw new IOException("Staged TOML failed Forge validation");
            }
            // Require an atomic replacement: if unsupported, leave the original file untouched and report failure.
            Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
            ((FileConfig) config.getConfigData()).load();
            spec.afterReload();
        } catch (Exception failure) {
            if (replaced) {
                try {
                    Files.write(staged, openedBytes);
                    Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    ((FileConfig) config.getConfigData()).load();
                    spec.afterReload();
                } catch (Exception rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw new IOException("Could not save " + config.getFileName(), failure);
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
