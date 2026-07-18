package com.xinian.KryptonHybrid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Fabric-side JSON config loader.
 *
 * <p>The runtime reads values from {@link KryptonConfig}; this class keeps that
 * common holder as the single source of truth and persists the same public
 * fields into {@code config/krypton_hybrid.json}.</p>
 */
public final class KryptonFabricConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("krypton_hybrid.json");

    private KryptonFabricConfig() {}

    public static void load() {
        JsonObject object = readConfigObject();
        apply(object);
        writeCurrent();
    }

    private static JsonObject readConfigObject() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return new JsonObject();
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            KryptonHybrid.LOGGER.warn("Failed to read {}, using defaults", CONFIG_PATH, e);
            return new JsonObject();
        }
    }

    private static void apply(JsonObject object) {
        for (Field field : KryptonConfig.class.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || !object.has(field.getName())) {
                continue;
            }

            try {
                field.set(null, readValue(field.getType(), object.get(field.getName())));
            } catch (Exception e) {
                KryptonHybrid.LOGGER.warn("Ignoring invalid config value '{}'", field.getName(), e);
            }
        }
    }

    private static Object readValue(Class<?> type, JsonElement element) {
        if (type == boolean.class) return element.getAsBoolean();
        if (type == int.class) return element.getAsInt();
        if (type == long.class) return element.getAsLong();
        if (type == double.class) return element.getAsDouble();
        if (type == String.class) return element.getAsString();
        if (type.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class<? extends Enum>) type, element.getAsString().toUpperCase(Locale.ROOT));
            return value;
        }
        throw new IllegalArgumentException("Unsupported config field type: " + type.getName());
    }

    public static void reload() {
        JsonObject object = readConfigObject();
        apply(object);
        KryptonHybrid.LOGGER.info("Reloaded krypton_hybrid.json");
    }

    private static void writeCurrent() {
        JsonObject object = new JsonObject();

        for (Field field : KryptonConfig.class.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }

            try {
                Object value = field.get(null);
                if (value instanceof Boolean b) object.addProperty(field.getName(), b);
                else if (value instanceof Number n) object.addProperty(field.getName(), n);
                else if (value instanceof Enum<?> e) object.addProperty(field.getName(), e.name());
                else if (value instanceof String s) object.addProperty(field.getName(), s);
            } catch (IllegalAccessException e) {
                KryptonHybrid.LOGGER.warn("Failed to serialize config value '{}'", field.getName(), e);
            }
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(object, writer);
            }
        } catch (IOException e) {
            KryptonHybrid.LOGGER.warn("Failed to write {}", CONFIG_PATH, e);
        }
    }
}
