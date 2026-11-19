package dev.tr7zw.itemtransformer.client;

import dev.tr7zw.itemtransformer.ServerCompat;
import dev.tr7zw.itemswapper.manager.itemgroups.ItemEntry;
import dev.tr7zw.itemswapper.manager.itemgroups.ItemGroup;
import dev.tr7zw.itemswapper.overlay.SwitchItemOverlay;
import dev.tr7zw.itemswapper.overlay.logic.GuiSlot;
import dev.tr7zw.itemtransformer.ItemTransformerMod;
import dev.tr7zw.itemtransformer.network.TransformItemPayload;
import dev.tr7zw.itemtransformer.network.TransformShulkerPayload;
import dev.tr7zw.itemtransformer.util.ItemFamilyUtil;
import dev.tr7zw.itemtransformer.util.ShulkerUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.tr7zw.itemswapper.overlay.logic.PaletteWidget")
public abstract class PaletteWidgetMixin {

    @Shadow
    private ItemGroup itemGroup;

    @Inject(method = "onPrimaryClick(Ldev/tr7zw/itemswapper/overlay/SwitchItemOverlay;Ldev/tr7zw/itemswapper/overlay/logic/GuiSlot;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void itemtransformer_onPrimaryClick(SwitchItemOverlay overlay, GuiSlot guiSlot, int x, int y,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ServerCompat.isServerCompatible()) return;
        if (guiSlot == null || itemGroup == null) return;

        int slotId = guiSlot.id();
        ItemEntry entry = itemGroup.getItem(slotId);
        if (entry == null || entry.isActAsLink()) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null) return;

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        ItemStack held = client.player.getInventory().getItem(selectedSlot);
        if (held.isEmpty()) return;

        Item fromItem = held.getItem();
        Item toItem = entry.getItem();
        if (toItem == null || fromItem == toItem) return;

        // Case 1: Direct transform.
        if (ItemFamilyUtil.canTransformTo(fromItem, toItem)
                && ItemFamilyUtil.canConvert(held, toItem)) {
            sendTransform(toItem, selectedSlot, selectedSlot);
            cir.setReturnValue(false);
            return;
        }

        // Case 2: Transform + swap from inventory.
        int sourceSlot = findTransformableSource(selectedSlot, toItem);
        if (sourceSlot != -1) {
            sendTransform(toItem, selectedSlot, sourceSlot);
            cir.setReturnValue(false);
            return;
        }

        // Case 3: Transform + swap from Shulker Box.
        int[] shulkerResult = findTransformableInShulker(selectedSlot, toItem);
        if (shulkerResult != null) {
            sendShulkerTransform(toItem, selectedSlot, shulkerResult[0], shulkerResult[1]);
            cir.setReturnValue(false);
            return;
        }

        // No valid transform — fall through.
    }

    /** First inventory slot (≠ heldSlot) that can transform into `target`. */
    private int findTransformableSource(int heldSlot, Item target) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return -1;

        var inv = client.player.getInventory();
        int i = 0;
        for (ItemStack stack : inv) {
            if (i != heldSlot && !stack.isEmpty()
                    && ItemFamilyUtil.canTransformTo(stack.getItem(), target)
                    && ItemFamilyUtil.canConvert(stack, target)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private void sendTransform(Item toItem, int heldSlot, int sourceSlot) {
        ItemTransformerMod.LOGGER.info("[ItemTransformer] SENDING: {} -> {} (held={}, source={})",
            BuiltInRegistries.ITEM.getKey(toItem),
            BuiltInRegistries.ITEM.getKey(toItem), heldSlot, sourceSlot);

        try {
            ClientPlayNetworking.send(new TransformItemPayload(
                BuiltInRegistries.ITEM.getKey(toItem), heldSlot, sourceSlot));
        } catch (Exception e) {
            ItemTransformerMod.LOGGER.error("[ItemTransformer] Send failed: {}", e.getMessage());
        }
    }

    /** First transformable source inside any shulker box. Returns {slot, subSlot} or null. */
    private int[] findTransformableInShulker(int heldSlot, Item target) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return null;

        var inv = client.player.getInventory();
        int i = 0;
        for (ItemStack stack : inv) {
            if (i != heldSlot && !stack.isEmpty() && ShulkerUtil.isShulkerBox(stack)) {
                var contents = ShulkerUtil.getContents(stack);
                for (int sub = 0; sub < contents.size(); sub++) {
                    ItemStack inner = contents.get(sub);
                    if (!inner.isEmpty()
                            && ItemFamilyUtil.canTransformTo(inner.getItem(), target)
                            && ItemFamilyUtil.canConvert(inner, target)) {
                        return new int[]{i, sub};
                    }
                }
            }
            i++;
        }
        return null;
    }

    private void sendShulkerTransform(Item toItem, int heldSlot, int shulkerSlot, int subSlot) {
        ItemTransformerMod.LOGGER.info("[ItemTransformer] SENDING (shulker): {} -> {} (held={}, box={}, sub={})",
            BuiltInRegistries.ITEM.getKey(toItem),
            BuiltInRegistries.ITEM.getKey(toItem), heldSlot, shulkerSlot, subSlot);

        try {
            ClientPlayNetworking.send(new TransformShulkerPayload(
                BuiltInRegistries.ITEM.getKey(toItem), heldSlot, shulkerSlot, subSlot));
        } catch (Exception e) {
            ItemTransformerMod.LOGGER.error("[ItemTransformer] Shulker send failed: {}", e.getMessage());
        }
    }
}
