package dev.tr7zw.itemtransformer;

import dev.tr7zw.itemtransformer.config.ItemTransformerConfig;
import dev.tr7zw.trender.gui.client.AbstractConfigScreen;
import dev.tr7zw.trender.gui.client.BackgroundPainter;
import dev.tr7zw.trender.gui.widget.WButton;
import dev.tr7zw.trender.gui.widget.WGridPanel;
import dev.tr7zw.trender.gui.widget.WToggleButton;
import dev.tr7zw.trender.gui.widget.data.Insets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class ItemTransformerConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        return new ConfigScreen(parent).createScreen();
    }

    private static class ConfigScreen extends AbstractConfigScreen {

        public ConfigScreen(Screen previous) {
            super(Component.literal("ItemTransformer Config"), previous);
            WGridPanel root = new WGridPanel(8);
            root.setInsets(Insets.ROOT_PANEL);
            setRootPanel(root);

            WToggleButton feedbackToggle = new WToggleButton(Component.literal("ItemTransformer feedback"));
            feedbackToggle.setToggle(ItemTransformerConfig.get().showTransformFeedback);
            feedbackToggle.setOnToggle(b -> ItemTransformerConfig.get().showTransformFeedback = b);
            root.add(feedbackToggle, 2, 2, 10, 2);

            WButton doneButton = new WButton(CommonComponents.GUI_DONE);
            doneButton.setOnClick(() -> {
                save();
                Minecraft.getInstance().setScreenAndShow(previous);
            });
            root.add(doneButton, 0, 26, 6, 2);

            WButton resetButton = new WButton(Component.translatable("controls.reset"));
            resetButton.setOnClick(() -> {
                reset();
                feedbackToggle.setToggle(false);
                root.layout();
            });
            root.add(resetButton, 29, 26, 6, 2);

            root.setBackgroundPainter(BackgroundPainter.VANILLA);

            root.validate(this);
            root.setHost(this);
        }

        @Override
        public void save() {
            ItemTransformerConfig.save();
        }

        @Override
        public void reset() {
            ItemTransformerConfig.get().showTransformFeedback = false;
            ItemTransformerConfig.save();
        }
    }
}