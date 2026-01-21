package com.example.keeper;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfirmStopScreen extends Screen {
    private final Keeper bot;
    private final long openTime;
    private static final int TIMEOUT_MS = 5000;

    public ConfirmStopScreen(Keeper bot) {
        super(Text.literal("Confirm Disable Defense"));
        this.bot = bot;
        this.openTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("§cDisable Defense"), b -> {
            bot.confirmStop();
            close();
        }).dimensions(cx - 100, cy, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§aKeep Enabled"), b -> {
            bot.cancelStop();
            close();
        }).dimensions(cx - 100, cy + 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        long elapsed = System.currentTimeMillis() - openTime;
        long remaining = Math.max(0, TIMEOUT_MS - elapsed);
        
        context.drawCenteredTextWithShadow(textRenderer, "§eDisable Player Watching?", width / 2, height / 2 - 50, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Bot will NOT auto-start on threat detection", width / 2, height / 2 - 35, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, String.format("§7Auto-resume in %.1fs", remaining / 1000.0), width / 2, height / 2 - 15, 0xAAAAAA);
        
        if (remaining == 0) {
            bot.cancelStop();
            close();
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
