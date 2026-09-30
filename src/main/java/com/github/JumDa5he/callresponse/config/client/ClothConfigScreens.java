package com.github.JumDa5he.callresponse.config.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.config.NeoForgeConfigDraft;
import me.shedaniel.clothconfig2.api.*;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

/** 只从已检测 Cloth 存在的客户端入口加载；不注册第二套配置。 */
public final class ClothConfigScreens extends Screen {
    private static final String PREFIX = "callresponse.configuration.";
    private final Screen parent;
    private MultiLineLabel notice = MultiLineLabel.EMPTY;

    public ClothConfigScreens(Screen parent) {
        super(Component.translatable(PREFIX + "title"));
        this.parent = parent;
    }

    private static boolean remote() {
        Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null && !client.isLocalServer();
    }

    private static boolean editable(ModConfig config) {
        return config.getSpec() instanceof ModConfigSpec spec && spec.isLoaded()
                && config.getLoadedConfig() != null && config.getFullPath() != null
                && (config.getType() == ModConfig.Type.CLIENT || !remote());
    }

    @Override protected void init() {
        notice = MultiLineLabel.create(font, text(remote() ? "remote" : "local"), width - 48);
        List<ModConfig> configs = ModConfigs.getModConfigs(CallResponseMod.MOD_ID).stream()
                .sorted(Comparator.comparing(ModConfig::getFileName)).toList();
        int y = Math.max(95, 54 + notice.getLineCount() * 9);
        for (ModConfig config : configs) {
            String key = PREFIX + "section." + config.getFileName().replace('-', '.') + ".title";
            Button button = Button.builder(Component.translatableWithFallback(key, config.getFileName()), b -> open(config))
                    .bounds(width / 2 - 150, y, 300, 20).build();
            button.active = editable(config);
            addRenderableWidget(button);
            y += 26;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private void open(ModConfig config) {
        try {
            if (!editable(config)) throw new IllegalStateException("Configuration is not editable");
            NeoForgeConfigDraft draft = new NeoForgeConfigDraft(config);
            SaveResult result = new SaveResult(this);
            ConfigBuilder builder = ConfigBuilder.create().setParentScreen(result).setTitle(title);
            ConfigEntryBuilder entries = builder.entryBuilder();
            ModConfigSpec spec = (ModConfigSpec) config.getSpec();
            for (NeoForgeConfigDraft.Field field : draft.fields()) {
                String groupKey = spec.getLevelTranslationKey(List.of(field.path().getFirst()));
                ConfigCategory category = builder.getOrCreateCategory(Component.translatable(groupKey));
                if (category.getEntries().isEmpty()) {
                    category.addEntry(entries.startTextDescription(text("apply")).build());
                    if (field.path().getFirst().equals("dispatch")) {
                        category.addEntry(entries.startTextDescription(text("dispatchRange")).build());
                    }
                }
                category.addEntry(entry(entries, draft, field));
            }
            builder.setSavingRunnable(() -> {
                result.pending = true;
                if (!editable(config)) {
                    result.failed = true;
                    result.pending = false;
                    return;
                }
                Runnable save = () -> {
                    boolean failed = false;
                    try { draft.save(); }
                    catch (Exception failure) {
                        CallResponseMod.LOGGER.error("Configuration save failed: {}", config.getFileName(), failure);
                        failed = true;
                    }
                    boolean finalFailed = failed;
                    Minecraft.getInstance().execute(() -> {
                        result.failed = finalFailed;
                        result.pending = false;
                        if (Minecraft.getInstance().screen == result) Minecraft.getInstance().setScreen(result);
                    });
                };
                var server = minecraft.getSingleplayerServer();
                if (server != null && config.getType() != ModConfig.Type.CLIENT) server.execute(save);
                else save.run();
            });
            result.editor = builder.build();
            minecraft.setScreen(result.editor);
        } catch (Exception failure) {
            CallResponseMod.LOGGER.error("Could not open configuration", failure);
            SaveResult result = new SaveResult(this);
            result.failed = true;
            minecraft.setScreen(result);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AbstractConfigListEntry<?> entry(ConfigEntryBuilder entries, NeoForgeConfigDraft draft, NeoForgeConfigDraft.Field field) {
        ModConfigSpec.ValueSpec definition = field.definition();
        String key = definition.getTranslationKey();
        Component name = Component.translatable(key);
        Object value = field.original();
        AbstractFieldBuilder control;
        if (value instanceof Boolean v) control = entries.startBooleanToggle(name, v);
        else if (value instanceof Integer v) control = entries.startIntField(name, v);
        else if (value instanceof Long v) control = entries.startLongField(name, v);
        else if (value instanceof Double v) control = entries.startDoubleField(name, v);
        else if (value instanceof Enum v) control = entries.startEnumSelector(name, v.getDeclaringClass(), v)
                .setEnumNameProvider(e -> Component.translatable(PREFIX + "enum." + ((Enum<?>) e).name().toLowerCase(Locale.ROOT)));
        else if (value instanceof String v) control = entries.startStrField(name, v);
        else if (value instanceof List v) control = entries.startStrList(name, new ArrayList<>(v));
        else throw new IllegalArgumentException("Unsupported existing option " + field.path());
        control.setDefaultValue(definition.getDefault());
        control.setSaveConsumer(v -> draft.edit(field, v));
        control.setErrorSupplier(v -> definition.test(v) ? Optional.empty() : Optional.of(text("invalid")));
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(key + ".tooltip"));
        tooltip.add(Component.translatable(PREFIX + "cloth.default", display(definition.getDefault())));
        if (definition.getRange() != null) tooltip.add(Component.translatable(PREFIX + "cloth.range", definition.getRange().toString()));
        var restart = definition.restartType();
        tooltip.add(text(restart == ModConfigSpec.RestartType.GAME ? "gameRestart"
                : restart == ModConfigSpec.RestartType.WORLD ? "restart" : "apply"));
        control.setTooltip(tooltip.toArray(Component[]::new));
        if (restart != ModConfigSpec.RestartType.NONE) control.requireRestart();
        return control.build();
    }

    private static Component display(Object value) {
        if (value instanceof Boolean b) return Component.translatable(b ? "options.on" : "options.off");
        if (value instanceof Enum<?> e) return Component.translatable(PREFIX + "enum." + e.name().toLowerCase(Locale.ROOT));
        if (value instanceof List<?> list && list.isEmpty()) return text("empty");
        return Component.literal(String.valueOf(value));
    }

    private static Component text(String key) { return Component.translatable(PREFIX + "cloth." + key); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
        super.render(graphics, x, y, tick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xffffff);
        notice.renderCentered(graphics, width / 2, 42);
    }

    private static final class SaveResult extends Screen {
        private final Screen parent;
        private Screen editor;
        private boolean pending;
        private boolean failed;
        private MultiLineLabel message = MultiLineLabel.EMPTY;

        private SaveResult(Screen parent) { super(text("failureTitle")); this.parent = parent; }
        @Override protected void init() {
            if (!pending && !failed) { minecraft.setScreen(parent); return; }
            message = MultiLineLabel.create(font, text(pending ? "saving" : "failure"), width - 48);
            if (!pending) addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                    .bounds(width / 2 - 100, height - 40, 200, 20).build());
        }
        @Override public void onClose() {
            if (!pending) {
                failed = false;
                minecraft.setScreen(editor == null ? parent : editor);
            }
        }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            super.render(graphics, x, y, tick);
            graphics.drawCenteredString(font, pending ? text("saving") : title, width / 2, 30, pending ? 0xffffff : 0xff5555);
            message.renderCentered(graphics, width / 2, 65);
        }
    }
}
