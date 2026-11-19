package dev.tr7zw.itemtransformer;

import dev.tr7zw.itemtransformer.network.ServerProbePayload;
import dev.tr7zw.itemtransformer.network.ServerProbeResponsePayload;
import dev.tr7zw.itemtransformer.network.TransformItemPayload;
import dev.tr7zw.itemtransformer.network.TransformShulkerPayload;
import dev.tr7zw.itemtransformer.util.ItemFamilyUtil;
import dev.tr7zw.itemtransformer.util.ShulkerUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemTransformerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ItemTransformer");

    @Override
    public void onInitialize() {
        LOGGER.info("[ItemTransformer] Mod entrypoint initialized (main)");

        try {
            // Register payload types.
            PayloadTypeRegistry.serverboundPlay().register(
                TransformItemPayload.TYPE, TransformItemPayload.STREAM_CODEC);
            PayloadTypeRegistry.serverboundPlay().register(
                ServerProbePayload.TYPE, ServerProbePayload.STREAM_CODEC);
            PayloadTypeRegistry.serverboundPlay().register(
                TransformShulkerPayload.TYPE, TransformShulkerPayload.STREAM_CODEC);
            PayloadTypeRegistry.clientboundPlay().register(
                ServerProbeResponsePayload.TYPE, ServerProbeResponsePayload.STREAM_CODEC);

            // Server handler.
            ServerPlayNetworking.registerGlobalReceiver(TransformItemPayload.TYPE, (payload, context) -> {
                ServerPlayer player = context.player();
                int heldSlot = payload.heldSlot();
                int sourceSlot = payload.sourceSlot();

                Item targetItem = BuiltInRegistries.ITEM
                    .getOptional(payload.targetItemId()).orElse(null);

                if (targetItem == null) {
                    LOGGER.warn("ItemTransformer: unknown target item {}", payload.targetItemId());
                    return;
                }

                ItemStack sourceStack = player.getInventory().getItem(sourceSlot);
                if (sourceStack.isEmpty()) {
                    LOGGER.warn("ItemTransformer: source slot {} is empty", sourceSlot);
                    return;
                }

                Item sourceItem = sourceStack.getItem();

                if (!ItemFamilyUtil.canTransformTo(sourceItem, targetItem)) {
                    LOGGER.info("ItemTransformer: transform not allowed: {} -> {}",
                        sourceItem, targetItem);
                    player.sendSystemMessage(
                        Component.literal("\u00a7cCannot transform " +
                            BuiltInRegistries.ITEM.getKey(sourceItem) +
                            " into " + BuiltInRegistries.ITEM.getKey(targetItem)), true);
                    return;
                }

                // Aggregation: pool source across inventory + shulker boxes.
                if (ItemFamilyUtil.itemCost(sourceItem) < ItemFamilyUtil.itemCost(targetItem)
                        && player.getInventory().getItem(heldSlot).is(sourceItem)) {
                    if (!transformAggregated(player, heldSlot, sourceItem, targetItem)) {
                        player.sendSystemMessage(
                            Component.literal("\u00a7cNot enough " +
                                BuiltInRegistries.ITEM.getKey(sourceItem) +
                                " to make " + BuiltInRegistries.ITEM.getKey(targetItem)), true);
                        return;
                    }
                    LOGGER.info("ItemTransformer: aggregated {} -> {} (held {})",
                        sourceItem, targetItem, heldSlot);
                    player.sendSystemMessage(
                        Component.literal("\u00a7aTransformed to " +
                            BuiltInRegistries.ITEM.getKey(targetItem)), true);
                    return;
                }

                if (!ItemFamilyUtil.canConvert(sourceStack, targetItem)) {
                    player.sendSystemMessage(
                        Component.literal("\u00a7cNot enough " +
                            BuiltInRegistries.ITEM.getKey(sourceItem) +
                            " to convert into " +
                            BuiltInRegistries.ITEM.getKey(targetItem)), true);
                    return;
                }

                int toCount = ItemFamilyUtil.conversionCount(sourceStack, targetItem);
                int maxSize = targetItem.getDefaultMaxStackSize();
                int neededStacks = (toCount + maxSize - 1) / maxSize;
                int extraNeeded = neededStacks - 1;

                // Source slot reserved (reused for held item) unless direct transform.
                int reservedMain = payload.isDirectTransform() ? -1 : sourceSlot;

                if (extraNeeded > 0
                        && !hasRoomForOverflow(player, heldSlot, reservedMain, -1, -1, extraNeeded)) {
                    player.sendSystemMessage(
                        Component.literal("\u00a7eNot enough inventory or shulker space for the converted items"),
                        true);
                    return;
                }

                if (payload.isDirectTransform()) {
                    placeOverflow(player, heldSlot, targetItem, -1, -1, -1, toCount);
                    LOGGER.info("ItemTransformer: direct {} -> {} (slot {}, count {})",
                        sourceItem, targetItem, heldSlot, toCount);
                } else {
                    ItemStack heldStack = player.getInventory().getItem(heldSlot);
                    placeOverflow(player, heldSlot, targetItem, sourceSlot, -1, -1, toCount);
                    player.getInventory().setItem(sourceSlot, heldStack);
                    LOGGER.info("ItemTransformer: transform+swap {} (slot {}) -> {} (slot {}, count {})",
                        sourceItem, sourceSlot, targetItem, heldSlot, toCount);
                }

                player.sendSystemMessage(
                    Component.literal("\u00a7aTransformed to " +
                        BuiltInRegistries.ITEM.getKey(targetItem)), true);
            });

            // Server handler: shulker transform.
            ServerPlayNetworking.registerGlobalReceiver(TransformShulkerPayload.TYPE, (payload, context) -> {
                ServerPlayer player = context.player();
                int heldSlot = payload.heldSlot();
                int shulkerSlot = payload.shulkerSlot();
                int shulkerSubSlot = payload.shulkerSubSlot();

                Item targetItem = BuiltInRegistries.ITEM
                    .getOptional(payload.targetItemId()).orElse(null);
                if (targetItem == null) {
                    LOGGER.warn("ItemTransformer: [shulker] unknown target {}", payload.targetItemId());
                    return;
                }

                ItemStack shulkerStack = player.getInventory().getItem(shulkerSlot);
                if (!ShulkerUtil.isShulkerBox(shulkerStack)) {
                    player.sendSystemMessage(Component.literal("\u00a7cInvalid shulker box"), true);
                    return;
                }

                var contents = ShulkerUtil.getContents(shulkerStack);
                if (shulkerSubSlot < 0 || shulkerSubSlot >= contents.size()) return;
                ItemStack sourceStack = contents.get(shulkerSubSlot);
                if (sourceStack.isEmpty()) return;

                Item sourceItem = sourceStack.getItem();

                if (!ItemFamilyUtil.canTransformTo(sourceItem, targetItem)) {
                    player.sendSystemMessage(
                        Component.literal("\u00a7cCannot transform " +
                            BuiltInRegistries.ITEM.getKey(sourceItem) +
                            " into " + BuiltInRegistries.ITEM.getKey(targetItem)), true);
                    return;
                }

                if (!ItemFamilyUtil.canConvert(sourceStack, targetItem)) {
                    player.sendSystemMessage(
                        Component.literal("\u00a7cNot enough " +
                            BuiltInRegistries.ITEM.getKey(sourceItem) +
                            " to convert into " + BuiltInRegistries.ITEM.getKey(targetItem)), true);
                    return;
                }

                int toCount = ItemFamilyUtil.conversionCount(sourceStack, targetItem);
                int maxSize = targetItem.getDefaultMaxStackSize();
                int neededStacks = (toCount + maxSize - 1) / maxSize;
                int extraNeeded = neededStacks - 1;

                // Shulker sub-slot is reserved (reused for held item).
                if (extraNeeded > 0
                        && !hasRoomForOverflow(player, heldSlot, -1, shulkerSlot, shulkerSubSlot, extraNeeded)) {
                    player.sendSystemMessage(
                        Component.literal("\u00a7eNot enough inventory or shulker space for the converted items"),
                        true);
                    return;
                }

                ItemStack heldStack = player.getInventory().getItem(heldSlot);
                placeOverflow(player, heldSlot, targetItem, -1, shulkerSlot, shulkerSubSlot, toCount);
                ShulkerUtil.setSlot(shulkerStack, shulkerSubSlot, heldStack);

                LOGGER.info("ItemTransformer: [shulker] {} (box={} sub={}) -> {} (held={}, count {})",
                    sourceItem, shulkerSlot, shulkerSubSlot, targetItem, heldSlot, toCount);
                player.sendSystemMessage(
                    Component.literal("\u00a7aTransformed to " +
                        BuiltInRegistries.ITEM.getKey(targetItem) + " (from shulker)"), true);
            });

            // Server handler: probe response.
            ServerPlayNetworking.registerGlobalReceiver(ServerProbePayload.TYPE, (payload, context) -> {
                LOGGER.info("ItemTransformer: received probe from {}", context.player().getName().getString());
                context.responseSender().sendPacket(new ServerProbeResponsePayload());
            });

            LOGGER.info("[ItemTransformer] Server handler registered successfully");
        } catch (Exception e) {
            LOGGER.warn("[ItemTransformer] Could not register server handler: {}", e.getMessage());
        }
    }

    /** Pools source across inventory + shulkers for cheaper -> pricier transforms. */
    private static boolean transformAggregated(ServerPlayer player, int heldSlot,
            Item sourceItem, Item targetItem) {
        int srcCost = ItemFamilyUtil.itemCost(sourceItem);
        int tgtCost = ItemFamilyUtil.itemCost(targetItem);
        if (srcCost >= tgtCost) {
            return false;
        }

        var items = player.getInventory().getNonEquipmentItems();
        long total = 0;
        long pool = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (st.is(sourceItem)) {
                total += st.getCount();
                if (i != heldSlot) {
                    pool += st.getCount();
                }
            } else if (ShulkerUtil.isShulkerBox(st)) {
                for (ItemStack inner : ShulkerUtil.getContents(st)) {
                    if (inner.is(sourceItem)) {
                        total += inner.getCount();
                        pool += inner.getCount();
                    }
                }
            }
        }
        if (total < 1) {
            return false;
        }

        int toCount = (int) (total * srcCost / tgtCost);
        if (toCount < 1) {
            return false;
        }

        // Cap output at one stack.
        int maxStack = targetItem.getDefaultMaxStackSize();
        if (toCount > maxStack) {
            toCount = maxStack;
        }

        // Consume from pool (held slot is destination, not double-counted).
        long toConsume = (long) toCount * tgtCost / srcCost;
        int heldCount = player.getInventory().getItem(heldSlot).getCount();
        long poolToConsume = Math.max(0, toConsume - heldCount);
        consumeSourceFromPool(player, heldSlot, sourceItem, Math.min(poolToConsume, pool));

        // Place target in held slot, overflow to inventory/shulkers.
        placeOverflow(player, heldSlot, targetItem, -1, -1, -1, toCount);
        return true;
    }

    /** Consumes `amount` of `sourceItem` from inventory and shulkers (excluding held slot). */
    private static void consumeSourceFromPool(ServerPlayer player, int heldSlot,
            Item sourceItem, long amount) {
        if (amount <= 0) {
            return;
        }
        var items = player.getInventory().getNonEquipmentItems();
        // Main inventory (excluding held slot).
        for (int i = 0; i < items.size() && amount > 0; i++) {
            if (i == heldSlot) {
                continue;
            }
            ItemStack st = player.getInventory().getItem(i);
            if (st.is(sourceItem)) {
                int take = (int) Math.min(amount, st.getCount());
                if (take >= st.getCount()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                } else {
                    st.shrink(take);
                }
                amount -= take;
            }
        }
        // Shulker boxes.
        for (int i = 0; i < items.size() && amount > 0; i++) {
            ItemStack box = player.getInventory().getItem(i);
            if (!ShulkerUtil.isShulkerBox(box)) {
                continue;
            }
            var contents = ShulkerUtil.getContents(box);
            for (int sub = 0; sub < contents.size() && amount > 0; sub++) {
                ItemStack inner = contents.get(sub);
                if (inner.is(sourceItem)) {
                    int take = (int) Math.min(amount, inner.getCount());
                    if (take >= inner.getCount()) {
                        ShulkerUtil.setSlot(box, sub, ItemStack.EMPTY);
                    } else {
                        inner.shrink(take);
                        ShulkerUtil.setSlot(box, sub, inner);
                    }
                    amount -= take;
                }
            }
        }
    }

    private static boolean hasRoomForOverflow(ServerPlayer player, int destMainSlot,
            int reservedMainSlot, int reservedBoxSlot, int reservedBoxSubSlot, int extraNeeded) {
        if (extraNeeded <= 0) {
            return true;
        }
        var items = player.getInventory().getNonEquipmentItems();
        int free = 0;
        // Empty inventory slots.
        for (int i = 0; i < items.size(); i++) {
            if (i == destMainSlot || i == reservedMainSlot) {
                continue;
            }
            if (items.get(i).isEmpty()) {
                free++;
                if (free >= extraNeeded) {
                    return true;
                }
            }
        }
        // Free shulker slots.
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (!ShulkerUtil.isShulkerBox(s)) {
                continue;
            }
            var contents = ShulkerUtil.getContents(s);
            for (int sub = 0; sub < contents.size(); sub++) {
                if (i == reservedBoxSlot && sub == reservedBoxSubSlot) {
                    continue;
                }
                if (contents.get(sub).isEmpty()) {
                    free++;
                    if (free >= extraNeeded) {
                        return true;
                    }
                }
            }
        }
        return free >= extraNeeded;
    }

    /** Places converted output into destination slot, then overflow to inventory/shulkers. */
    private static void placeOverflow(ServerPlayer player, int destMainSlot, Item to,
            int reservedMainSlot, int reservedBoxSlot, int reservedBoxSubSlot, int toCount) {
        int maxSize = to.getDefaultMaxStackSize();
        var items = player.getInventory().getNonEquipmentItems();

        // 1) Destination slot gets the first (possibly full) stack.
        int first = Math.min(toCount, maxSize);
        player.getInventory().setItem(destMainSlot, new ItemStack(to, first));
        int remaining = toCount - first;

        // 2) Empty main inventory slots.
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            if (i == destMainSlot || i == reservedMainSlot) {
                continue;
            }
            if (items.get(i).isEmpty()) {
                int put = Math.min(remaining, maxSize);
                player.getInventory().setItem(i, new ItemStack(to, put));
                remaining -= put;
            }
        }

        // 3) Free slots inside shulker boxes held in the inventory.
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack s = items.get(i);
            if (!ShulkerUtil.isShulkerBox(s)) {
                continue;
            }
            var contents = ShulkerUtil.getContents(s);
            for (int sub = 0; sub < contents.size() && remaining > 0; sub++) {
                if (i == reservedBoxSlot && sub == reservedBoxSubSlot) {
                    continue;
                }
                if (contents.get(sub).isEmpty()) {
                    int put = Math.min(remaining, maxSize);
                    ShulkerUtil.setSlot(s, sub, new ItemStack(to, put));
                    remaining -= put;
                }
            }
        }

        if (remaining > 0) {
            LOGGER.warn("ItemTransformer: could not place {} converted item(s) (no space)", remaining);
        }
    }
}
