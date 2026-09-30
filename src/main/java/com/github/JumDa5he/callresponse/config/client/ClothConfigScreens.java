package com.github.JumDa5he.callresponse.config.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.config.ForgeConfigDraft;
import com.mojang.logging.LogUtils;
import me.shedaniel.clothconfig2.api.*;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.*;

/** Loaded only by the optional, client-only configuration extension. */
final class ClothConfigScreens extends Screen {
    private static final String PREFIX = "callresponse.configuration.";
    private final Screen parent;
    private MultiLineLabel notice = MultiLineLabel.EMPTY;

    ClothConfigScreens(Screen parent) {
        super(Component.translatable(PREFIX + "title"));
        this.parent = parent;
    }

    private static boolean remote() {
        Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null && !client.isLocalServer();
    }

    @Override protected void init() {
        notice = MultiLineLabel.create(font, text(remote() ? "remote" : "local"), width - 48);
        List<ModConfig> configs = ConfigTracker.INSTANCE.fileMap().values().stream()
                .filter(c -> c.getModId().equals(CallResponseMod.MOD_ID))
                .sorted(Comparator.comparing(ModConfig::getFileName)).toList();
        int y = Math.max(95, 54 + notice.getLineCount() * 9);
        for (ModConfig config : configs) {
            String key = PREFIX + "section." + config.getFileName().replace('-', '.') + ".title";
            Button button = Button.builder(Component.translatable(key), b -> open(config))
                    .bounds(width / 2 - 150, y, 300, 20).build();
            // COMMON is local storage, but its gameplay values are owned by the server in multiplayer.
            button.active = config.getSpec() instanceof ForgeConfigSpec forge && forge.isLoaded()
                    && (config.getType() == ModConfig.Type.CLIENT || !remote());
            addRenderableWidget(button);
            y += 26;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private void open(ModConfig config) {
        try {
            ForgeConfigDraft draft = new ForgeConfigDraft(config);
            SaveResult result = new SaveResult(this);
            ConfigBuilder builder = ConfigBuilder.create().setParentScreen(result).setTitle(title);
            ConfigEntryBuilder entries = builder.entryBuilder();
            ForgeConfigSpec spec = (ForgeConfigSpec) config.getSpec();
            for (ForgeConfigDraft.Field field : draft.fields()) {
                String groupKey = spec.getLevelTranslationKey(List.of(field.path().get(0)));
                ConfigCategory category = builder.getOrCreateCategory(Component.translatable(groupKey));
                if (category.getEntries().isEmpty()) {
                    category.addEntry(entries.startTextDescription(text("apply")).build());
                    if (field.path().get(0).equals("dispatch")) {
                        category.addEntry(entries.startTextDescription(text("dispatchRange")).build());
                    }
                }
                category.addEntry(entry(entries, draft, field));
            }
            builder.setSavingRunnable(() -> {
                try {
                    if (remote() && config.getType() != ModConfig.Type.CLIENT) throw new IllegalStateException("Remote server configuration is not editable");
                    draft.save();
                } catch (Exception failure) {
                    LogUtils.getLogger().error("Call Response configuration save failed: {}", config.getFileName(), failure);
                    result.failed = true;
                }
            });
            result.editor = builder.build();
            minecraft.setScreen(result.editor);
        } catch (Exception failure) {
            LogUtils.getLogger().error("Could not open Call Response configuration", failure);
            SaveResult result = new SaveResult(this);
            result.failed = true;
            minecraft.setScreen(result);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AbstractConfigListEntry<?> entry(ConfigEntryBuilder entries, ForgeConfigDraft draft, ForgeConfigDraft.Field field) {
        ForgeConfigSpec.ValueSpec definition = field.definition();
        String key = definition.getTranslationKey();
        Component name = Component.translatable(key);
        Object value = field.original();
        AbstractFieldBuilder control;
        if (value instanceof Boolean v) control = entries.startBooleanToggle(name, v);
        else if (value instanceof Integer v) control = entries.startIntField(name, v);
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
        tooltip.add(text(definition.needsWorldRestart() ? "restart" : "apply"));
        control.setTooltip(tooltip.toArray(Component[]::new));
        if (definition.needsWorldRestart()) control.requireRestart();
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

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xffffff);
        notice.renderCentered(graphics, width / 2, 42);
        super.render(graphics, x, y, partialTick);
    }

    // Cloth closes to its parent after its saving callback. This parent reports failures
    // without throwing through a render tick or allowing a later close to hide the error.
    private static final class SaveResult extends Screen {
        private final Screen parent;
        private Screen editor;
        private boolean failed;
        private MultiLineLabel message = MultiLineLabel.EMPTY;

        private SaveResult(Screen parent) { super(text("failureTitle")); this.parent = parent; }
        @Override protected void init() {
            if (!failed) { minecraft.setScreen(parent); return; }
            message = MultiLineLabel.create(font, text("failure"), width - 48);
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                    .bounds(width / 2 - 100, height - 40, 200, 20).build());
        }
        @Override public void onClose() {
            failed = false;
            minecraft.setScreen(editor == null ? parent : editor);
        }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            renderBackground(graphics);
            graphics.drawCenteredString(font, title, width / 2, 30, 0xff5555);
            message.renderCentered(graphics, width / 2, 65);
            super.render(graphics, x, y, tick);
        }
    }
}
