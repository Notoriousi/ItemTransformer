package dev.tr7zw.itemtransformer.client;

import dev.tr7zw.itemtransformer.ServerCompat;
import dev.tr7zw.itemswapper.manager.itemgroups.ItemEntry;
import dev.tr7zw.itemswapper.manager.itemgroups.ItemGroup;
import dev.tr7zw.itemswapper.overlay.logic.GuiSlot;
import dev.tr7zw.itemtransformer.util.ItemFamilyUtil;
import dev.tr7zw.trender.gui.client.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "dev.tr7zw.itemswapper.overlay.logic.PaletteWidget")
public abstract class PaletteRenderMixin {

    @Shadow
    private ItemGroup itemGroup;

    @Inject(method = "renderSlot(Ldev/tr7zw/trender/gui/client/RenderContext;IILjava/util/List;Ldev/tr7zw/itemswapper/overlay/logic/GuiSlot;Z)V",
            at = @At("TAIL"))
    private void itemtransformer_renderSlotFeedback(RenderContext context, int x, int y,
            List<Runnable> callbacks, GuiSlot guiSlot, boolean overwrideAvailable, CallbackInfo ci) {
        if (!ServerCompat.isServerCompatible()) return;
        if (guiSlot == null || itemGroup == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        ItemStack held = client.player.getInventory().getItem(selectedSlot);
        if (held.isEmpty()) return;

        Item fromItem = held.getItem();
        int slotId = guiSlot.id();
        ItemEntry entry = itemGroup.getItem(slotId);
        if (entry == null || entry.isActAsLink()) return;

        Item toItem = entry.getItem();
        if (toItem == null || toItem == fromItem) return;

        int slotSize = guiSlot.size();
        int gx = guiSlot.x();
        int gy = guiSlot.y();

        if (ItemFamilyUtil.canConvert(held, toItem)) {
            drawBorder(context, gx, gy, slotSize, 0x8055FF55);
        } else if (ItemFamilyUtil.getFamily(fromItem) != null) {
            drawBorder(context, gx, gy, slotSize, 0x40FFFFFF);
        }
    }

    private static void drawBorder(RenderContext context, int x, int y, int size, int color) {
        context.fill(x, y, x + size, y + 1, color);
        context.fill(x, y + size - 1, x + size, y + size, color);
        context.fill(x, y, x + 1, y + size, color);
        context.fill(x + size - 1, y, x + size, y + size, color);
    }
}
