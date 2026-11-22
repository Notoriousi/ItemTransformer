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
            PayloadTypeRegistry.serverboundPlay().register(
                TransformItemPayload.TYPE, TransformItemPayload.STREAM_CODEC);
            PayloadTypeRegistry.serverboundPlay().register(
                ServerProbePayload.TYPE, ServerProbePayload.STREAM_CODEC);
            PayloadTypeRegistry.serverboundPlay().register(
                TransformShulkerPayload.TYPE, TransformShulkerPayload.STREAM_CODEC);
            PayloadTypeRegistry.clientboundPlay().register(
                ServerProbeResponsePayload.TYPE, ServerProbeResponsePayload.STREAM_CODEC);

            ServerPlayNetworking.registerGlobalReceiver(TransformItemPayload.TYPE, (payload, context) -> {
                ServerPlayer player = context.player();
                int heldSlot = payload.heldSlot();
                int sourceSlot = payload.sourceSlot();
                if (!isValidSlot(heldSlot) || !isValidSlot(sourceSlot)) {
                    LOGGER.warn("ItemTransformer: invalid slot index (held={}, source={})", heldSlot, sourceSlot);
                    return;
                }

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
                    if (ItemTransformerConfig.get().showTransformFeedback) {
                        player.sendSystemMessage(
                            Component.literal("\u00a7aTransformed to " +
                                BuiltInRegistries.ITEM.getKey(targetItem)), true);
                    }
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

                int srcCost = ItemFamilyUtil.itemCost(sourceItem);
                int tgtCost = ItemFamilyUtil.itemCost(targetItem);
                int consumed = toCount * tgtCost / srcCost;
                int leftoverCount = sourceStack.getCount() - consumed;
                if (leftoverCount > 0) {
                    placeLeftover(player, sourceItem, leftoverCount,
                        payload.isDirectTransform() ? heldSlot : sourceSlot);
                }

                if (ItemTransformerConfig.get().showTransformFeedback) {
                    player.sendSystemMessage(
                        Component.literal("\u00a7aTransformed to " +
                            BuiltInRegistries.ITEM.getKey(targetItem)), true);
                }
            });

            ServerPlayNetworking.registerGlobalReceiver(TransformShulkerPayload.TYPE, (payload, context) -> {
                ServerPlayer player = context.player();
                int heldSlot = payload.heldSlot();
                int shulkerSlot = payload.shulkerSlot();
                int shulkerSubSlot = payload.shulkerSubSlot();
                if (!isValidSlot(heldSlot) || !isValidSlot(shulkerSlot) || shulkerSubSlot < 0) {
                    LOGGER.warn("ItemTransformer: [shulker] invalid slot index (held={}, box={}, sub={})", heldSlot, shulkerSlot, shulkerSubSlot);
                    return;
                }

                Item targetItem = BuiltInRegistries.ITEM
                    .getOptional(payload.targetItemId()).orElse(null);
                if (targetItem == null) {
                    LOGGER.warn("ItemTransformer: [shulker] unknown target {}", payload.targetItemId());
                    return;
                }

                ItemStack shulkerStack = player.getInventory().getItem(shulkerSlot);
                if (!ShulkerUtil.isShulkerBox(shulkerStack) || shulkerStack.getCount() > 1) {
                    player.sendSystemMessage(Component.literal("\u00a7cInvalid or stacked shulker box"), true);
                    return;
                }

                var contents = ShulkerUtil.getContents(shulkerStack);
                if (shulkerSubSlot >= contents.size()) return;
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
                // Shulker boxes can't nest inside each other (would desync).
                if (ShulkerUtil.isShulkerBox(heldStack)) {
                    LOGGER.warn("ItemTransformer: [shulker] refused to store a shulker box inside a shulker box");
                    player.sendSystemMessage(Component.literal("\u00a7cCannot store a shulker box inside a shulker box."), true);
                    return;
                }
                placeOverflow(player, heldSlot, targetItem, -1, shulkerSlot, shulkerSubSlot, toCount);
                ShulkerUtil.setSlot(shulkerStack, shulkerSubSlot, heldStack);

                int srcCost = ItemFamilyUtil.itemCost(sourceItem);
                int tgtCost = ItemFamilyUtil.itemCost(targetItem);
                int consumed = toCount * tgtCost / srcCost;
                int leftoverCount = sourceStack.getCount() - consumed;
                if (leftoverCount > 0) {
                    placeLeftoverInShulker(player, sourceItem, leftoverCount,
                        shulkerSlot, shulkerSubSlot);
                }

                LOGGER.info("ItemTransformer: [shulker] {} (box={} sub={}) -> {} (held={}, count {})",
                    sourceItem, shulkerSlot, shulkerSubSlot, targetItem, heldSlot, toCount);
                if (ItemTransformerConfig.get().showTransformFeedback) {
                    player.sendSystemMessage(
                        Component.literal("\u00a7aTransformed to " +
                            BuiltInRegistries.ITEM.getKey(targetItem) + " (from shulker)"), true);
                }
            });

            ServerPlayNetworking.registerGlobalReceiver(ServerProbePayload.TYPE, (payload, context) -> {
                LOGGER.info("ItemTransformer: received probe from {}", context.player().getName().getString());
                context.responseSender().sendPacket(new ServerProbeResponsePayload());
            });

            LOGGER.info("[ItemTransformer] Server handler registered successfully");
        } catch (Exception e) {
            LOGGER.warn("[ItemTransformer] Could not register server handler: {}", e.getMessage());
        }
    }

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
            } else if (ShulkerUtil.isShulkerBox(st) && st.getCount() == 1) {
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

        int maxStack = targetItem.getDefaultMaxStackSize();
        if (toCount > maxStack) {
            toCount = maxStack;
        }

        long toConsume = (long) toCount * tgtCost / srcCost;
        // Safety: never consume more than what we actually have.
        if (toConsume > total) {
            toConsume = total;
        }

        int heldCount = player.getInventory().getItem(heldSlot).getCount();
        long poolToConsume = Math.min(toConsume, pool);
        long heldToConsume = toConsume - poolToConsume;

        // Save leftover from held slot (what placeOverflow will overwrite).
        ItemStack heldLeftover = ItemStack.EMPTY;
        ItemStack heldStack = player.getInventory().getItem(heldSlot);
        if (heldToConsume > 0 && heldStack.is(sourceItem)) {
            int leftover = heldCount - (int) heldToConsume;
            if (leftover > 0) {
                heldLeftover = new ItemStack(sourceItem, leftover);
            }
        }

        consumeSourceFromPool(player, heldSlot, sourceItem, (int) poolToConsume);

        placeOverflow(player, heldSlot, targetItem, -1, -1, -1, toCount);

        if (!heldLeftover.isEmpty()) {
            boolean placed = false;
            var allItems = player.getInventory().getNonEquipmentItems();
            for (int i = 0; i < allItems.size() && !placed; i++) {
                if (i == heldSlot) continue;
                if (allItems.get(i).isEmpty()) {
                    player.getInventory().setItem(i, heldLeftover);
                    placed = true;
                }
            }
            if (!placed) {
                for (int i = 0; i < allItems.size() && !placed; i++) {
                    ItemStack s = allItems.get(i);
                    if (!ShulkerUtil.isShulkerBox(s) || s.getCount() > 1) continue;
                    var contents = ShulkerUtil.getContents(s);
                    for (int sub = 0; sub < contents.size() && !placed; sub++) {
                        if (contents.get(sub).isEmpty()) {
                            ShulkerUtil.setSlot(s, sub, heldLeftover);
                            placed = true;
                        }
                    }
                }
            }
            if (!placed) {
                LOGGER.warn("ItemTransformer: could not place {} leftover {} item(s)", heldLeftover.getCount(), sourceItem);
            }
        }
        return true;
    }

    private static void consumeSourceFromPool(ServerPlayer player, int heldSlot,
            Item sourceItem, long amount) {
        if (amount <= 0) {
            return;
        }
        var items = player.getInventory().getNonEquipmentItems();
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
        // Shulker boxes (skip stacked — writing to one duplicates into the whole stack).
        for (int i = 0; i < items.size() && amount > 0; i++) {
            ItemStack box = player.getInventory().getItem(i);
            if (!ShulkerUtil.isShulkerBox(box) || box.getCount() > 1) {
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
        // Free shulker slots (skip stacked — writing to one duplicates into the whole stack).
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (!ShulkerUtil.isShulkerBox(s) || s.getCount() > 1) {
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

    private static void placeOverflow(ServerPlayer player, int destMainSlot, Item to,
            int reservedMainSlot, int reservedBoxSlot, int reservedBoxSubSlot, int toCount) {
        int maxSize = to.getDefaultMaxStackSize();
        var items = player.getInventory().getNonEquipmentItems();

        int first = Math.min(toCount, maxSize);
        player.getInventory().setItem(destMainSlot, new ItemStack(to, first));
        int remaining = toCount - first;

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

        // 3) Free slots inside shulker boxes (skip stacked to prevent duplication).
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack s = items.get(i);
            if (!ShulkerUtil.isShulkerBox(s) || s.getCount() > 1) {
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

    private static void placeLeftover(ServerPlayer player, Item item, int count, int occupiedSlot) {
        var items = player.getInventory().getNonEquipmentItems();

        for (int i = 0; i < items.size(); i++) {
            if (i == occupiedSlot) continue;
            if (items.get(i).isEmpty()) {
                player.getInventory().setItem(i, new ItemStack(item, count));
                return;
            }
        }

        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (!ShulkerUtil.isShulkerBox(s) || s.getCount() > 1) continue;
            var contents = ShulkerUtil.getContents(s);
            for (int sub = 0; sub < contents.size(); sub++) {
                if (contents.get(sub).isEmpty()) {
                    ShulkerUtil.setSlot(s, sub, new ItemStack(item, count));
                    return;
                }
            }
        }

        LOGGER.warn("ItemTransformer: could not place {} leftover {} item(s)", count, item);
    }

    private static void placeLeftoverInShulker(ServerPlayer player, Item item, int count,
            int shulkerSlot, int shulkerSubSlot) {
        ItemStack shulkerStack = player.getInventory().getItem(shulkerSlot);
        if (ShulkerUtil.isShulkerBox(shulkerStack) && shulkerStack.getCount() == 1) {
            var contents = ShulkerUtil.getContents(shulkerStack);
            for (int sub = 0; sub < contents.size(); sub++) {
                if (sub == shulkerSubSlot) continue;
                if (contents.get(sub).isEmpty()) {
                    ShulkerUtil.setSlot(shulkerStack, sub, new ItemStack(item, count));
                    return;
                }
            }
        }

        var items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            if (i == shulkerSlot) continue;
            if (items.get(i).isEmpty()) {
                player.getInventory().setItem(i, new ItemStack(item, count));
                return;
            }
        }

        for (int i = 0; i < items.size(); i++) {
            if (i == shulkerSlot) continue;
            ItemStack s = items.get(i);
            if (!ShulkerUtil.isShulkerBox(s) || s.getCount() > 1) continue;
            var contents = ShulkerUtil.getContents(s);
            for (int sub = 0; sub < contents.size(); sub++) {
                if (contents.get(sub).isEmpty()) {
                    ShulkerUtil.setSlot(s, sub, new ItemStack(item, count));
                    return;
                }
            }
        }

        LOGGER.warn("ItemTransformer: could not place {} leftover {} item(s)", count, item);
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot <= 39;
    }
}
