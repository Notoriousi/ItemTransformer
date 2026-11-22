package dev.tr7zw.itemtransformer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ItemTransformerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemTransformer");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Whether to show the "Transformed to ..." success message in chat. Off by default. */
    public boolean showTransformFeedback = false;

    private static ItemTransformerConfig instance;

    public static synchronized ItemTransformerConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ItemTransformerConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("itemtransformer.json");
        if (Files.exists(configPath)) {
            try {
                ItemTransformerConfig config = GSON.fromJson(
                    Files.readString(configPath), ItemTransformerConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                LOGGER.warn("ItemTransformer: could not load config: {}", e.getMessage());
            }
        }
        // Write default config file so the user can see the option
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(new ItemTransformerConfig()));
        } catch (IOException ignored) {}
        return new ItemTransformerConfig();
    }

    public void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("itemtransformer.json");
        try {
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.error("ItemTransformer: could not save config: {}", e.getMessage());
        }
    }
}