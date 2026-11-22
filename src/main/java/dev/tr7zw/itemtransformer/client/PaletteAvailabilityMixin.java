package dev.tr7zw.itemtransformer.client;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tr7zw.itemswapper.manager.itemgroups.ItemGroup;
import dev.tr7zw.itemtransformer.ServerCompat;
import dev.tr7zw.itemswapper.overlay.logic.GuiSlot;
import dev.tr7zw.itemswapper.overlay.logic.PaletteWidget;
import dev.tr7zw.itemswapper.util.RenderHelper;
import dev.tr7zw.itemtransformer.util.ItemFamilyUtil;
import dev.tr7zw.itemtransformer.util.ShulkerUtil;
import dev.tr7zw.trender.gui.client.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Fixes the RED "unavailable" tint for items reachable via block-family transformation. */
@Mixin(PaletteWidget.class)
public abstract class PaletteAvailabilityMixin {

    @Shadow @Final private ItemGroup itemGroup;

    @Inject(
            method = "renderSlot(Ldev/tr7zw/trender/gui/client/RenderContext;II"
                    + "Ljava/util/List;Ldev/tr7zw/itemswapper/overlay/logic/GuiSlot;Z)V",
            at = @At("TAIL"))
    private void itemtransformer$fixAvailabilityTint(
            RenderContext context, int x, int y,
            List<Runnable> renderList, GuiSlot slot,
            boolean forceAvailable, CallbackInfo ci) {

        if (!ServerCompat.isServerCompatible()) return;
        if (forceAvailable) return;
        if (renderList.isEmpty()) return;

        Item item = getItemForSlot(slot);
        if (item == null) return;
        if (isInInventory(item)) return;

        Set<Item> family = ItemFamilyUtil.getFamily(item);
        if (family == null) return;

        int toCost = ItemFamilyUtil.itemCost(item);
        boolean familyReachable = false;
        for (Item familyItem : family) {
            if (familyItem == item) continue;
            int count = getInventoryCount(familyItem);
            if (count > 0) {
                int fromCost = ItemFamilyUtil.itemCost(familyItem);
                if ((count * fromCost) / toCost >= 1) {
                    familyReachable = true;
                    break;
                }
            }
        }
        if (!familyReachable) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        renderList.remove(renderList.size() - 1);
        ItemStack stack = item.getDefaultInstance();
        renderList.add(() -> RenderHelper.renderSlot(
                context, x + 3, y + 4,
                client.player,
                stack,
                1,
                RenderHelper.SlotEffect.NONE,
                1));
    }

    private boolean isInInventory(Item item) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;
        var inv = client.player.getInventory();
        for (ItemStack stack : inv) {
            if (stack.is(item)) return true;
            if (ShulkerUtil.isShulkerBox(stack) && stack.getCount() == 1) {
                for (ItemStack inner : ShulkerUtil.getContents(stack)) {
                    if (inner.is(item)) return true;
                }
            }
        }
        return false;
    }

    private int getInventoryCount(Item item) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return 0;
        var inv = client.player.getInventory();
        int count = 0;
        for (ItemStack stack : inv) {
            if (stack.is(item)) {
                count += stack.getCount();
            } else if (ShulkerUtil.isShulkerBox(stack) && stack.getCount() == 1) {
                for (ItemStack inner : ShulkerUtil.getContents(stack)) {
                    if (inner.is(item)) count += inner.getCount();
                }
            }
        }
        return count;
    }

    private Item getItemForSlot(GuiSlot slot) {
        try {
            var entries = itemGroup.getItems();
            int id = slot.id();
            if (id < 0 || id >= entries.length) return null;
            return entries[id].getItem();
        } catch (Exception e) {
            return null;
        }
    }
}