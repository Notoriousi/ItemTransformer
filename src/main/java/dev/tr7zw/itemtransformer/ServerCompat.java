package dev.tr7zw.itemtransformer;

import dev.tr7zw.itemtransformer.network.ServerProbePayload;
import dev.tr7zw.itemtransformer.network.ServerProbeResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks whether the connected server has ItemTransformer installed. */
public final class ServerCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ItemTransformer");
    private static volatile boolean serverHasMod = true;

    private ServerCompat() {}

    /** Register the network events. Call once from the client entrypoint. */
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ServerProbeResponsePayload.TYPE, (payload, context) -> {
            serverHasMod = true;
            LOGGER.info("[ItemTransformer] Server probe response received — mod is installed on server");
        });

        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            if (client.isLocalServer()) {
                serverHasMod = true;
                LOGGER.info("[ItemTransformer] Singleplayer — server-compat flag = true");
            } else {
                serverHasMod = false;
                LOGGER.info("[ItemTransformer] Multiplayer — sending probe to server");
                try {
                    ClientPlayNetworking.send(new ServerProbePayload());
                } catch (Exception e) {
                    LOGGER.warn("[ItemTransformer] Probe send failed (server likely lacks the mod): {}",
                        e.getMessage());
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            serverHasMod = true;
            LOGGER.info("[ItemTransformer] Disconnected — resetting server-compat flag to true");
        });
    }

    /** @return true if the palette may intercept clicks. */
    public static boolean isServerCompatible() {
        return serverHasMod;
    }
}