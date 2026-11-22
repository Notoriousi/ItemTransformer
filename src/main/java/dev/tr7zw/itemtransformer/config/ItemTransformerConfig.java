package dev.tr7zw.itemtransformer.config;

import dev.tr7zw.transition.config.ConfigManager;

public class ItemTransformerConfig {

    public static class Config {
        public boolean showTransformFeedback = false;
    }

    private static final ConfigManager<Config> manager = new ConfigManager<>("itemtransformer", Config::new, null);

    public static Config get() {
        return manager.getConfig();
    }

    public static void save() {
        manager.writeConfig();
    }

    public static void reset() {
        manager.reset();
    }
}