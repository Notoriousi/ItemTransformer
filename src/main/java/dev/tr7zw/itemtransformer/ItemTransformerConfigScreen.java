package dev.tr7zw.itemtransformer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class ItemTransformerConfigScreen extends Screen {
    private final Screen parentScreen;
    private boolean feedbackEnabled;

    public ItemTransformerConfigScreen(Screen parentScreen) {
        super(Component.literal("ItemTransformer Config"));
        this.parentScreen = parentScreen;
        this.feedbackEnabled = ItemTransformerConfig.get().showTransformFeedback;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean down) {
        if (down) {
            if (event.y() >= 50 && event.y() <= 80) {
                feedbackEnabled = !feedbackEnabled;
                ItemTransformerConfig.get().showTransformFeedback = feedbackEnabled;
                ItemTransformerConfig.get().save();
                return true;
            }
        }
        return super.mouseClicked(event, down);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        String state = feedbackEnabled ? "\u00a7aON" : "\u00a7cOFF";
        extractor.centeredText(this.font, "Transform Feedback", this.width / 2, 30, 0xFFFFFF);
        extractor.centeredText(this.font,
            "Show transform feedback: " + state + "  (\u00a77click to toggle\u00a7r)",
            this.width / 2, 60, 0xFFFFFF);
        extractor.centeredText(this.font,
            "When enabled, shows a chat message on successful transform.",
            this.width / 2, 85, 0xAAAAAA);
        extractor.centeredText(this.font,
            "Config file: config/itemtransformer.json",
            this.width / 2, 110, 0x888888);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parentScreen);
    }
}