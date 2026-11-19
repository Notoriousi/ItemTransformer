package dev.tr7zw.itemtransformer.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/** Helper for reading/writing items inside Shulker Box data components. */
public final class ShulkerUtil {

    private ShulkerUtil() {}

    /** @return true if the stack is a Shulker Box (any colour). */
    public static boolean isShulkerBox(ItemStack stack) {
        return stack.has(DataComponents.CONTAINER);
    }

    /** @return items inside the shulker box as 27 ItemStacks. */
    public static List<ItemStack> getContents(ItemStack shulkerStack) {
        if (!isShulkerBox(shulkerStack)) return List.of();

        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return List.of();

        List<ItemStack> items = new ArrayList<>(contents.itemCopies().toList());
        // Pad to 27 slots if needed
        while (items.size() < 27) {
            items.add(ItemStack.EMPTY);
        }
        return items;
    }

    /** Replaces the item at the given slot (0–26) inside the shulker box. */
    public static void setSlot(ItemStack shulkerStack, int slot, ItemStack newItem) {
        if (slot < 0 || slot > 26) return;

        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        List<ItemStack> items = new ArrayList<>(contents.itemCopies().toList());
        while (items.size() < 27) {
            items.add(ItemStack.EMPTY);
        }

        items.set(slot, newItem);
        ItemContainerContents newContents = ItemContainerContents.fromItems(items);
        shulkerStack.set(DataComponents.CONTAINER, newContents);
    }
}