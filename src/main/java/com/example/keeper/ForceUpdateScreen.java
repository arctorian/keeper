package com.example.keeper;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * A screen that forces the user to restart when an update is pending.
 * Cannot be closed - user must restart or quit the game.
 */
public class ForceUpdateScreen extends Screen {
    
    private static final int BG_COLOR = 0xF0101010;
    private static final int ACCENT_COLOR = 0xFFAA00FF;
    private static final int ACCENT_HOVER = 0xFFCC44FF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int WARNING_COLOR = 0xFFFFAA00;
    
    private final boolean isDisabledDuplicate;
    
    public ForceUpdateScreen(boolean isDisabledDuplicate) {
        super(Text.literal("Keeper Update Required"));
        this.isDisabledDuplicate = isDisabledDuplicate;
    }
    
    @Override
    protected void init() {
        // No widgets - we render everything manually
    }
    
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Full screen dark background
        ctx.fill(0, 0, width, height, BG_COLOR);
        
        int centerX = width / 2;
        int y = height / 2 - 60;
        
        // Title
        String title = "§l⚠ KEEPER UPDATE REQUIRED";
        int titleWidth = textRenderer.getWidth(title);
        ctx.drawTextWithShadow(textRenderer, Text.literal(title), centerX - titleWidth / 2, y, WARNING_COLOR);
        y += 20;
        
        if (isDisabledDuplicate) {
            // Duplicate version scenario
            String msg1 = "A newer version of Keeper is installed.";
            String msg2 = "Please restart to use v" + AutoUpdater.getNewerLocalVersion();
            String msg3 = "This instance (v" + AutoUpdater.getCurrentVersion() + ") has been disabled.";
            
            ctx.drawTextWithShadow(textRenderer, Text.literal(msg1), centerX - textRenderer.getWidth(msg1) / 2, y, TEXT_COLOR);
            y += 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal(msg2), centerX - textRenderer.getWidth(msg2) / 2, y, TEXT_GRAY);
            y += 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal(msg3), centerX - textRenderer.getWidth(msg3) / 2, y, TEXT_GRAY);
        } else {
            // Update downloaded scenario
            String msg1 = "Keeper v" + AutoUpdater.getLatestVersion() + " has been downloaded.";
            String msg2 = "You must restart to continue using Keeper.";
            String msg3 = "Current version: v" + AutoUpdater.getCurrentVersion();
            
            ctx.drawTextWithShadow(textRenderer, Text.literal(msg1), centerX - textRenderer.getWidth(msg1) / 2, y, TEXT_COLOR);
            y += 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal(msg2), centerX - textRenderer.getWidth(msg2) / 2, y, TEXT_GRAY);
            y += 12;
            ctx.drawTextWithShadow(textRenderer, Text.literal(msg3), centerX - textRenderer.getWidth(msg3) / 2, y, TEXT_GRAY);
        }
        
        y += 30;
        
        // Restart button
        int btnWidth = 140;
        int btnHeight = 24;
        int restartBtnX = centerX - btnWidth - 10;
        int btnY = y;
        
        boolean restartHovered = mouseX >= restartBtnX && mouseX < restartBtnX + btnWidth && 
                                  mouseY >= btnY && mouseY < btnY + btnHeight;
        ctx.fill(restartBtnX, btnY, restartBtnX + btnWidth, btnY + btnHeight, restartHovered ? 0xFF00CC00 : 0xFF00AA00);
        String restartText = "Restart Game";
        ctx.drawTextWithShadow(textRenderer, Text.literal(restartText), 
            restartBtnX + (btnWidth - textRenderer.getWidth(restartText)) / 2, btnY + 8, TEXT_COLOR);
        
        // Quit button
        int quitBtnX = centerX + 10;
        boolean quitHovered = mouseX >= quitBtnX && mouseX < quitBtnX + btnWidth && 
                              mouseY >= btnY && mouseY < btnY + btnHeight;
        ctx.fill(quitBtnX, btnY, quitBtnX + btnWidth, btnY + btnHeight, quitHovered ? 0xFFAA0000 : 0xFF880000);
        String quitText = "Quit Game";
        ctx.drawTextWithShadow(textRenderer, Text.literal(quitText), 
            quitBtnX + (btnWidth - textRenderer.getWidth(quitText)) / 2, btnY + 8, TEXT_COLOR);
        
        y += 50;
        
        // Footer message
        String footer = "Keeper cannot function until you restart.";
        ctx.drawTextWithShadow(textRenderer, Text.literal(footer), centerX - textRenderer.getWidth(footer) / 2, y, TEXT_GRAY);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        
        int centerX = width / 2;
        int btnWidth = 140;
        int btnHeight = 24;
        int btnY = height / 2 - 60 + 20 + (isDisabledDuplicate ? 36 : 36) + 30;
        
        // Restart button
        int restartBtnX = centerX - btnWidth - 10;
        if (mouseX >= restartBtnX && mouseX < restartBtnX + btnWidth && 
            mouseY >= btnY && mouseY < btnY + btnHeight) {
            AutoUpdater.restartGame();
            return true;
        }
        
        // Quit button
        int quitBtnX = centerX + 10;
        if (mouseX >= quitBtnX && mouseX < quitBtnX + btnWidth && 
            mouseY >= btnY && mouseY < btnY + btnHeight) {
            MinecraftClient.getInstance().scheduleStop();
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Cannot escape this screen
    }
    
    @Override
    public boolean shouldPause() {
        return true;
    }
    
    @Override
    public void close() {
        // Do nothing - this screen cannot be closed normally
    }
}
