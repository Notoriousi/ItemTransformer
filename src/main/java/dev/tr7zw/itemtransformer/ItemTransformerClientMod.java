package dev.tr7zw.itemtransformer;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemTransformerClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemTransformer");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ItemTransformer] ===== CLIENT ENTRYPOINT LOADED =====");
        LOGGER.info("[ItemTransformer] Block-family transform enabled for ItemSwapper palette");
        LOGGER.info("[ItemTransformer] Mixins will intercept PaletteWidget clicks + render");
        ServerCompat.init();
        LOGGER.info("[ItemTransformer] Server-compat check registered");
    }
}
