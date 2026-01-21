package com.example.spawnerdefensebot;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private BotConfig config;
    
    private static final int ACCENT_COLOR = 0xFFAA00FF;
    private static final int ACCENT_HOVER = 0xFFCC44FF;
    private static final int BG_COLOR = 0xE0101010;
    private static final int HEADER_COLOR = 0xFF1A1A1A;
    private static final int SECTION_COLOR = 0xFF151515;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    
    private static final String[] TABS = {"Config", "HUD", "Whitelist", "About"};
    private int selectedTab = 0;
    
    private int windowX, windowY, windowWidth, windowHeight;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    
    private final List<Section> sections = new ArrayList<>();
    private TextFieldWidget whitelistInput;
    
    private static final String[] LICENSE_TEXT = {
        "                    Apache License",
        "              Version 2.0, January 2004",
        "         http://www.apache.org/licenses/",
        "",
        "TERMS AND CONDITIONS FOR USE, REPRODUCTION,",
        "AND DISTRIBUTION",
        "",
        "1. Definitions.",
        "",
        "\"License\" shall mean the terms and conditions",
        "for use, reproduction, and distribution as",
        "defined by Sections 1 through 9 of this document.",
        "",
        "\"Licensor\" shall mean the copyright owner or",
        "entity authorized by the copyright owner that",
        "is granting the License.",
        "",
        "\"Legal Entity\" shall mean the union of the",
        "acting entity and all other entities that",
        "control, are controlled by, or are under common",
        "control with that entity.",
        "",
        "\"You\" (or \"Your\") shall mean an individual or",
        "Legal Entity exercising permissions granted by",
        "this License.",
        "",
        "\"Source\" form shall mean the preferred form for",
        "making modifications, including but not limited",
        "to software source code, documentation source,",
        "and configuration files.",
        "",
        "\"Object\" form shall mean any form resulting from",
        "mechanical transformation or translation of a",
        "Source form.",
        "",
        "\"Work\" shall mean the work of authorship made",
        "available under the License.",
        "",
        "\"Derivative Works\" shall mean any work that is",
        "based on (or derived from) the Work.",
        "",
        "\"Contribution\" shall mean any work of authorship",
        "submitted to the Licensor for inclusion in the",
        "Work.",
        "",
        "\"Contributor\" shall mean Licensor and any",
        "individual or Legal Entity on behalf of whom a",
        "Contribution has been received by Licensor.",
        "",
        "2. Grant of Copyright License.",
        "",
        "Subject to the terms and conditions of this",
        "License, each Contributor hereby grants to You",
        "a perpetual, worldwide, non-exclusive,",
        "no-charge, royalty-free, irrevocable copyright",
        "license to reproduce, prepare Derivative Works",
        "of, publicly display, publicly perform,",
        "sublicense, and distribute the Work and such",
        "Derivative Works in Source or Object form.",
        "",
        "3. Grant of Patent License.",
        "",
        "Subject to the terms and conditions of this",
        "License, each Contributor hereby grants to You",
        "a perpetual, worldwide, non-exclusive,",
        "no-charge, royalty-free, irrevocable patent",
        "license to make, have made, use, offer to sell,",
        "sell, import, and otherwise transfer the Work.",
        "",
        "4. Redistribution.",
        "",
        "You may reproduce and distribute copies of the",
        "Work or Derivative Works thereof in any medium,",
        "with or without modifications, and in Source or",
        "Object form, provided that You meet the",
        "following conditions:",
        "",
        "(a) You must give any other recipients of the",
        "    Work or Derivative Works a copy of this",
        "    License; and",
        "",
        "(b) You must cause any modified files to carry",
        "    prominent notices stating that You changed",
        "    the files; and",
        "",
        "(c) You must retain, in the Source form of any",
        "    Derivative Works that You distribute, all",
        "    copyright, patent, trademark, and",
        "    attribution notices from the Source form",
        "    of the Work; and",
        "",
        "(d) If the Work includes a \"NOTICE\" text file,",
        "    then any Derivative Works that You",
        "    distribute must include a readable copy of",
        "    the attribution notices contained within",
        "    such NOTICE file.",
        "",
        "5. Submission of Contributions.",
        "",
        "Unless You explicitly state otherwise, any",
        "Contribution intentionally submitted for",
        "inclusion in the Work by You to the Licensor",
        "shall be under the terms and conditions of",
        "this License, without any additional terms",
        "or conditions.",
        "",
        "6. Trademarks.",
        "",
        "This License does not grant permission to use",
        "the trade names, trademarks, service marks, or",
        "product names of the Licensor.",
        "",
        "7. Disclaimer of Warranty.",
        "",
        "Unless required by applicable law or agreed to",
        "in writing, Licensor provides the Work on an",
        "\"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS",
        "OF ANY KIND, either express or implied.",
        "",
        "8. Limitation of Liability.",
        "",
        "In no event and under no legal theory shall any",
        "Contributor be liable to You for damages,",
        "including any direct, indirect, special,",
        "incidental, or consequential damages.",
        "",
        "9. Accepting Warranty or Additional Liability.",
        "",
        "While redistributing the Work or Derivative",
        "Works thereof, You may choose to offer, and",
        "charge a fee for, acceptance of support,",
        "warranty, indemnity, or other liability",
        "obligations.",
        "",
        "END OF TERMS AND CONDITIONS",
        "",
        "Copyright 2026 Caelen Cater (https://caelen.dev)",
        "",
        "Licensed under the Apache License, Version 2.0",
        "(the \"License\"); you may not use this file",
        "except in compliance with the License.",
        "You may obtain a copy of the License at",
        "",
        "    http://www.apache.org/licenses/LICENSE-2.0",
        "",
        "Unless required by applicable law or agreed to",
        "in writing, software distributed under the",
        "License is distributed on an \"AS IS\" BASIS,",
        "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,",
        "either express or implied. See the License for",
        "the specific language governing permissions",
        "and limitations under the License."
    };
    
    public ConfigScreen(Screen parent) {
        super(Text.literal("Keeper Config"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        config = BotConfig.get();
        
        // Window dimensions - smaller, scaled to fit screen
        windowWidth = Math.min(360, width - 20);
        windowHeight = Math.min(height - 20, 280);
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;
        
        sections.clear();
        buildSections();
        
        // Create whitelist input
        int inputWidth = windowWidth - 70;
        whitelistInput = new TextFieldWidget(textRenderer, windowX + 6, windowY + 30, inputWidth, 16, Text.literal(""));
        whitelistInput.setPlaceholder(Text.literal("Username or UUID"));
        whitelistInput.setMaxLength(100);
        whitelistInput.setEditable(true);
    }
    
    private void buildSections() {
        sections.clear();
        
        switch (selectedTab) {
            case 0: // Config
                Section botSection = new Section("Bot Settings");
                botSection.add(new SettingToggle("Defense Enabled", config.defenseEnabled, v -> {
                    config.defenseEnabled = v;
                    SpawnerDefenseBot bot = SpawnerDefenseBot.getInstance();
                    if (bot != null) bot.setDefenseEnabled(v);
                }));
                botSection.add(new SettingSlider("Search Radius", config.searchRadius, 10, 64, true, v -> config.searchRadius = v.intValue()));
                botSection.add(new SettingSlider("Y Range", config.searchYRange, 4, 32, true, v -> config.searchYRange = v.intValue()));
                botSection.add(new SettingSlider("Mining Distance", config.miningDistance, 2.0, 6.0, false, v -> config.miningDistance = v));
                botSection.add(new SettingToggle("Path Rendering", config.pathRenderingEnabled, v -> {
                    config.pathRenderingEnabled = v;
                    PathRenderer.setEnabled(v);
                }));
                sections.add(botSection);
                
                Section eatSection = new Section("Auto Eat");
                eatSection.add(new SettingToggle("Auto Eat Enabled", config.autoEatEnabled, v -> config.autoEatEnabled = v));
                eatSection.add(new SettingSlider("Hunger Threshold", config.hungerThreshold, 6, 18, true, v -> config.hungerThreshold = v.intValue()));
                sections.add(eatSection);
                
                Section ghostSection = new Section("Ghost Block Settings");
                ghostSection.add(new SettingSlider("Check Delay", config.ghostCheckDelay, 20, 100, true, v -> config.ghostCheckDelay = v.intValue()));
                ghostSection.add(new SettingSlider("Fix Timeout", config.ghostFixTimeout, 30, 120, true, v -> config.ghostFixTimeout = v.intValue()));
                sections.add(ghostSection);
                break;
                
            case 1: // HUD
                Section hudSection = new Section("HUD Display");
                hudSection.add(new SettingToggle("Enable HUD", config.hudEnabled, v -> config.hudEnabled = v));
                hudSection.add(new SettingToggle("Auto-Hide When Defense Off", config.hideHudWhenDefenseOff, v -> config.hideHudWhenDefenseOff = v));
                hudSection.add(new SettingSlider("Scale", config.hudScale, 0.5, 2.0, false, v -> config.hudScale = v.floatValue()));
                hudSection.add(new SettingSlider("Opacity", config.hudBackgroundAlpha, 0, 255, true, v -> config.hudBackgroundAlpha = v.intValue()));
                sections.add(hudSection);
                
                Section posSection = new Section("Position");
                posSection.add(new SettingSlider("X Position", config.hudX, 0, width, true, v -> config.hudX = v.intValue()));
                posSection.add(new SettingSlider("Y Position", config.hudY, 0, height, true, v -> config.hudY = v.intValue()));
                sections.add(posSection);
                
                Section elemSection = new Section("Elements");
                elemSection.add(new SettingToggle("Show Title", config.showTitle, v -> config.showTitle = v));
                elemSection.add(new SettingToggle("Show Status", config.showStatus, v -> config.showStatus = v));
                elemSection.add(new SettingToggle("Show Players", config.showPlayers, v -> config.showPlayers = v));
                elemSection.add(new SettingToggle("Show Inventory", config.showInventory, v -> config.showInventory = v));
                elemSection.add(new SettingToggle("Show Spawner Count", config.showSpawnerCount, v -> config.showSpawnerCount = v));
                elemSection.add(new SettingToggle("Show Mined Total", config.showMinedTotal, v -> config.showMinedTotal = v));
                sections.add(elemSection);
                break;
                
            case 2: // Whitelist - handled separately
                break;
                
            case 3: // About - handled separately
                break;
        }
    }
    
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Darken background
        ctx.fill(0, 0, width, height, 0x88000000);
        
        // Main window background
        ctx.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, BG_COLOR);
        
        // Tab bar
        ctx.fill(windowX, windowY, windowX + windowWidth, windowY + 20, HEADER_COLOR);
        
        int tabWidth = windowWidth / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = windowX + i * tabWidth;
            boolean hovered = mouseX >= tx && mouseX < tx + tabWidth && mouseY >= windowY && mouseY < windowY + 20;
            
            if (i == selectedTab) {
                ctx.fill(tx, windowY + 17, tx + tabWidth, windowY + 20, ACCENT_COLOR);
            } else if (hovered) {
                ctx.fill(tx, windowY, tx + tabWidth, windowY + 20, 0x30FFFFFF);
            }
            
            int textWidth = textRenderer.getWidth(TABS[i]);
            ctx.drawTextWithShadow(textRenderer, Text.literal(TABS[i]), 
                tx + (tabWidth - textWidth) / 2, windowY + 6, 
                i == selectedTab ? ACCENT_COLOR : TEXT_GRAY);
        }
        
        // Content area
        int contentTop = windowY + 22;
        int contentBottom = windowY + windowHeight - 2;
        int contentHeight = contentBottom - contentTop;
        
        ctx.enableScissor(windowX, contentTop, windowX + windowWidth, contentBottom);
        
        int y = contentTop - scrollOffset + 2;
        
        if (selectedTab == 2) {
            y = renderWhitelistTab(ctx, mouseX, mouseY, y);
        } else if (selectedTab == 3) {
            y = renderAboutTab(ctx, mouseX, mouseY, y);
        } else {
            for (Section section : sections) {
                y = renderSection(ctx, section, mouseX, mouseY, y);
            }
        }
        
        maxScroll = Math.max(0, (y + scrollOffset) - contentBottom + 4);
        
        ctx.disableScissor();
        
        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarHeight = Math.max(15, (int)(contentHeight * (contentHeight / (float)(contentHeight + maxScroll))));
            int scrollbarY = contentTop + (int)((contentHeight - scrollbarHeight) * (scrollOffset / (float)maxScroll));
            ctx.fill(windowX + windowWidth - 3, scrollbarY, windowX + windowWidth - 1, scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }
        
        // Window border
        ctx.drawBorder(windowX, windowY, windowWidth, windowHeight, 0xFF000000);
        
        // Render whitelist input field on top (outside scissor)
        if (selectedTab == 2) {
            whitelistInput.setX(windowX + 6);
            whitelistInput.setY(windowY + 24);
            whitelistInput.setWidth(windowWidth - 60);
            whitelistInput.render(ctx, mouseX, mouseY, delta);
            
            // Add button
            int btnX = windowX + windowWidth - 50;
            int btnY = windowY + 24;
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + 46 && mouseY >= btnY && mouseY < btnY + 16;
            ctx.fill(btnX, btnY, btnX + 46, btnY + 16, btnHovered ? ACCENT_HOVER : ACCENT_COLOR);
            ctx.drawTextWithShadow(textRenderer, Text.literal("Add"), btnX + 14, btnY + 4, TEXT_COLOR);
        }
    }
    
    private int renderSection(DrawContext ctx, Section section, int mouseX, int mouseY, int y) {
        int sectionX = windowX + 4;
        int sectionWidth = windowWidth - 8;
        
        boolean headerHovered = mouseX >= sectionX && mouseX < sectionX + sectionWidth && 
                               mouseY >= y && mouseY < y + 16;
        
        ctx.fill(sectionX, y, sectionX + sectionWidth, y + 16, headerHovered ? 0xFF252525 : SECTION_COLOR);
        
        String arrow = section.expanded ? "▼" : "▶";
        ctx.drawTextWithShadow(textRenderer, Text.literal(arrow), sectionX + 3, y + 4, ACCENT_COLOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal(section.name), sectionX + 12, y + 4, TEXT_COLOR);
        
        y += 18;
        
        if (section.expanded) {
            for (Setting setting : section.settings) {
                y = renderSetting(ctx, setting, mouseX, mouseY, y, sectionX + 2, sectionWidth - 4);
            }
        }
        
        return y + 2;
    }
    
    private int renderSetting(DrawContext ctx, Setting setting, int mouseX, int mouseY, int y, int x, int settingWidth) {
        int settingHeight = 14;
        boolean hovered = mouseX >= x && mouseX < x + settingWidth && mouseY >= y && mouseY < y + settingHeight;
        
        if (hovered) {
            ctx.fill(x, y, x + settingWidth, y + settingHeight, 0x20FFFFFF);
        }
        
        ctx.drawTextWithShadow(textRenderer, Text.literal(setting.name), x + 2, y + 3, TEXT_GRAY);
        
        int controlX = x + settingWidth - 50;
        int controlWidth = 46;
        
        if (setting instanceof SettingToggle toggle) {
            int boxX = x + settingWidth - 14;
            int boxY = y + 2;
            int boxSize = 10;
            
            ctx.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, toggle.value ? ACCENT_COLOR : 0xFF333333);
            ctx.drawBorder(boxX, boxY, boxSize, boxSize, 0xFF000000);
            
            if (toggle.value) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("✓"), boxX + 1, boxY + 1, TEXT_COLOR);
            }
            
            toggle.clickArea = new int[]{boxX, boxY, boxX + boxSize, boxY + boxSize};
        } else if (setting instanceof SettingSlider slider) {
            int sliderX = controlX;
            int sliderY = y + 5;
            int sliderWidth = controlWidth;
            int sliderHeight = 4;
            
            ctx.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, 0xFF333333);
            
            double percent = (slider.value - slider.min) / (slider.max - slider.min);
            int fillWidth = (int)(sliderWidth * percent);
            ctx.fill(sliderX, sliderY, sliderX + fillWidth, sliderY + sliderHeight, ACCENT_COLOR);
            
            int handleX = sliderX + fillWidth - 2;
            ctx.fill(handleX, sliderY - 1, handleX + 4, sliderY + sliderHeight + 1, TEXT_COLOR);
            
            String valueStr = slider.isInt ? String.valueOf((int)slider.value) : String.format("%.1f", slider.value);
            int textWidth = textRenderer.getWidth(valueStr);
            ctx.drawTextWithShadow(textRenderer, Text.literal(valueStr), sliderX - textWidth - 2, y + 3, TEXT_GRAY);
            
            slider.sliderArea = new int[]{sliderX, y, sliderX + sliderWidth, y + settingHeight};
        }
        
        return y + settingHeight + 1;
    }
    
    private int renderWhitelistTab(DrawContext ctx, int mouseX, int mouseY, int y) {
        int contentX = windowX + 6;
        int contentWidth = windowWidth - 12;
        
        // Skip space for fixed input field at top
        y += 22;
        
        // Description text
        ctx.drawTextWithShadow(textRenderer, Text.literal("Players on whitelist won't trigger defense."), contentX, y, TEXT_GRAY);
        y += 14;
        
        // Separator
        ctx.drawTextWithShadow(textRenderer, Text.literal("--- Whitelisted Players ---"), contentX, y, ACCENT_COLOR);
        y += 12;
        
        if (config.whitelistedUUIDs.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("No players whitelisted"), contentX, y, TEXT_GRAY);
            y += 12;
        } else {
            for (String uuid : config.whitelistedUUIDs) {
                MojangAPI.Profile profile = MojangAPI.getProfileFromUUID(uuid).getNow(null);
                if (profile == null) profile = new MojangAPI.Profile("Loading...", uuid);
                
                // Load face pixels if needed
                if (!MojangAPI.isFaceLoaded(profile)) {
                    MojangAPI.loadFacePixels(profile);
                }
                
                // Entry background
                boolean entryHovered = mouseX >= contentX && mouseX < contentX + contentWidth && 
                                      mouseY >= y && mouseY < y + 28;
                if (entryHovered) {
                    ctx.fill(contentX, y, contentX + contentWidth, y + 28, 0x20FFFFFF);
                }
                
                // Player head
                int headSize = 16;
                int headX = contentX + 2;
                int headY = y + 1;
                
                int[][] facePixels = MojangAPI.getFacePixels(uuid);
                if (facePixels != null) {
                    // Draw face from pixel data (8x8 scaled to headSize)
                    drawFacePixels(ctx, facePixels, headX, headY, headSize);
                } else {
                    // Fallback - colored box with initial while loading
                    int color = MojangAPI.getColorForUUID(uuid);
                    ctx.fill(headX, headY, headX + headSize, headY + headSize, color);
                    ctx.drawBorder(headX, headY, headSize, headSize, 0xFF000000);
                    String initial = profile.name.length() > 0 ? profile.name.substring(0, 1).toUpperCase() : "?";
                    ctx.drawTextWithShadow(textRenderer, Text.literal(initial), headX + 4, headY + 4, TEXT_COLOR);
                }
                
                // Name on first line
                ctx.drawTextWithShadow(textRenderer, Text.literal(profile.name), contentX + 22, y + 2, TEXT_COLOR);
                
                // Full UUID on second line (smaller, gray)
                ctx.drawTextWithShadow(textRenderer, Text.literal(uuid), contentX + 22, y + 14, TEXT_GRAY);
                
                // Remove button
                int rmX = contentX + contentWidth - 46;
                boolean rmHovered = mouseX >= rmX && mouseX < rmX + 42 && mouseY >= y + 4 && mouseY < y + 18;
                ctx.fill(rmX, y + 6, rmX + 42, y + 20, rmHovered ? 0xFFAA0000 : 0xFF660000);
                ctx.drawTextWithShadow(textRenderer, Text.literal("Remove"), rmX + 2, y + 9, TEXT_COLOR);
                
                y += 30;
            }
        }
        
        return y;
    }
    
    private void drawFacePixels(DrawContext ctx, int[][] pixels, int x, int y, int size) {
        int pixelSize = size / 8; // Each skin pixel scaled up
        
        for (int px = 0; px < 8; px++) {
            for (int py = 0; py < 8; py++) {
                int color = pixels[px][py];
                int alpha = (color >> 24) & 0xFF;
                if (alpha > 0) { // Only draw non-transparent pixels
                    int drawX = x + px * pixelSize;
                    int drawY = y + py * pixelSize;
                    ctx.fill(drawX, drawY, drawX + pixelSize, drawY + pixelSize, color);
                }
            }
        }
    }
    
    private int renderAboutTab(DrawContext ctx, int mouseX, int mouseY, int y) {
        int contentX = windowX + 6;
        
        ctx.drawTextWithShadow(textRenderer, Text.literal("Project Keeper"), contentX, y, ACCENT_COLOR);
        y += 12;
        ctx.drawTextWithShadow(textRenderer, Text.literal("By Team Arctorian"), contentX, y, TEXT_GRAY);
        y += 16;
        
        // Buttons
        int btn1X = contentX;
        boolean btn1Hovered = mouseX >= btn1X && mouseX < btn1X + 80 && mouseY >= y && mouseY < y + 14;
        ctx.fill(btn1X, y, btn1X + 80, y + 14, btn1Hovered ? ACCENT_HOVER : ACCENT_COLOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Website"), btn1X + 20, y + 3, TEXT_COLOR);
        
        int btn2X = contentX + 90;
        boolean btn2Hovered = mouseX >= btn2X && mouseX < btn2X + 80 && mouseY >= y && mouseY < y + 14;
        ctx.fill(btn2X, y, btn2X + 80, y + 14, btn2Hovered ? ACCENT_HOVER : ACCENT_COLOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("GitHub"), btn2X + 22, y + 3, TEXT_COLOR);
        
        y += 20;
        
        // Stats section
        ctx.drawTextWithShadow(textRenderer, Text.literal("--- Session Stats ---"), contentX, y, ACCENT_COLOR);
        y += 12;
        
        SpawnerDefenseBot bot = SpawnerDefenseBot.getInstance();
        long totalSpawners = config.totalSpawnersMined + bot.sessionSpawnersMined;
        long totalShulkers = config.totalShulkersPacked + bot.sessionShulkersPacked;
        long totalTime = config.totalMiningTimeMs + bot.sessionMiningTime;
        
        ctx.drawTextWithShadow(textRenderer, Text.literal("Spawners: " + totalSpawners), contentX, y, TEXT_GRAY);
        y += 10;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Shulkers: " + totalShulkers), contentX, y, TEXT_GRAY);
        y += 10;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Time: " + (totalTime / 60000) + " min"), contentX, y, TEXT_GRAY);
        y += 14;
        
        // Reset button
        boolean resetHovered = mouseX >= contentX && mouseX < contentX + 70 && mouseY >= y && mouseY < y + 14;
        ctx.fill(contentX, y, contentX + 70, y + 14, resetHovered ? 0xFFAA0000 : 0xFF660000);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Reset"), contentX + 20, y + 3, TEXT_COLOR);
        
        y += 22;
        
        // License section
        ctx.drawTextWithShadow(textRenderer, Text.literal("--- License ---"), contentX, y, ACCENT_COLOR);
        y += 12;
        
        // Render full license text
        for (String line : LICENSE_TEXT) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(line), contentX, y, TEXT_GRAY);
            y += 9;
        }
        
        return y + 10;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        
        // Handle whitelist text field
        if (selectedTab == 2) {
            if (whitelistInput.mouseClicked(mouseX, mouseY, button)) {
                setFocused(whitelistInput);
                return true;
            }
            
            // Add button
            int btnX = windowX + windowWidth - 50;
            int btnY = windowY + 24;
            if (mouseX >= btnX && mouseX < btnX + 46 && mouseY >= btnY && mouseY < btnY + 16) {
                addWhitelistEntry();
                return true;
            }
        }
        
        // Tab clicks
        int tabWidth = windowWidth / TABS.length;
        if (mouseY >= windowY && mouseY < windowY + 20) {
            for (int i = 0; i < TABS.length; i++) {
                int tx = windowX + i * tabWidth;
                if (mouseX >= tx && mouseX < tx + tabWidth) {
                    selectedTab = i;
                    scrollOffset = 0;
                    buildSections();
                    return true;
                }
            }
        }
        
        // Content area clicks
        int contentTop = windowY + 22;
        int contentBottom = windowY + windowHeight - 2;
        
        if (mouseX >= windowX && mouseX < windowX + windowWidth && 
            mouseY >= contentTop && mouseY < contentBottom) {
            
            if (selectedTab == 2) {
                return handleWhitelistClick(mouseX, mouseY);
            } else if (selectedTab == 3) {
                return handleAboutClick(mouseX, mouseY);
            } else {
                // Section clicks
                int y = contentTop - scrollOffset + 2;
                for (Section section : sections) {
                    int sectionX = windowX + 4;
                    int sectionWidth = windowWidth - 8;
                    
                    if (mouseY >= y && mouseY < y + 16 && mouseX >= sectionX && mouseX < sectionX + sectionWidth) {
                        section.expanded = !section.expanded;
                        return true;
                    }
                    y += 18;
                    
                    if (section.expanded) {
                        for (Setting setting : section.settings) {
                            if (setting instanceof SettingToggle toggle && toggle.clickArea != null) {
                                if (mouseX >= toggle.clickArea[0] && mouseX < toggle.clickArea[2] &&
                                    mouseY >= toggle.clickArea[1] && mouseY < toggle.clickArea[3]) {
                                    toggle.value = !toggle.value;
                                    toggle.onChange.accept(toggle.value);
                                    return true;
                                }
                            }
                            y += 15;
                        }
                    }
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void addWhitelistEntry() {
        String text = whitelistInput.getText().trim();
        if (!text.isEmpty()) {
            if (text.matches("[0-9a-fA-F-]{32,36}")) {
                String normalized = text.replace("-", "").replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
                );
                config.addWhitelist(normalized);
                whitelistInput.setText("");
            } else {
                MojangAPI.getProfileFromUsername(text).thenAccept(profile -> {
                    if (profile != null) {
                        config.addWhitelist(profile.uuid);
                        MinecraftClient.getInstance().execute(() -> whitelistInput.setText(""));
                    }
                });
            }
        }
    }
    
    private boolean handleWhitelistClick(double mouseX, double mouseY) {
        int contentX = windowX + 6;
        int contentWidth = windowWidth - 12;
        
        // Remove buttons - calculate y position for each entry
        int y = windowY + 22 - scrollOffset + 2 + 22 + 14 + 12;
        for (String uuid : new ArrayList<>(config.whitelistedUUIDs)) {
            int rmX = contentX + contentWidth - 46;
            if (mouseX >= rmX && mouseX < rmX + 42 && mouseY >= y + 6 && mouseY < y + 20) {
                config.removeWhitelist(uuid);
                return true;
            }
            y += 30;
        }
        
        return false;
    }
    
    private boolean handleAboutClick(double mouseX, double mouseY) {
        int contentX = windowX + 6;
        int y = windowY + 22 - scrollOffset + 2 + 12 + 16;
        
        // Website button
        if (mouseX >= contentX && mouseX < contentX + 80 && mouseY >= y && mouseY < y + 14) {
            Util.getOperatingSystem().open(URI.create("https://arctorian.com"));
            return true;
        }
        
        // GitHub button
        int btn2X = contentX + 90;
        if (mouseX >= btn2X && mouseX < btn2X + 80 && mouseY >= y && mouseY < y + 14) {
            Util.getOperatingSystem().open(URI.create("https://github.com/arctorian/keeper"));
            return true;
        }
        
        // Reset stats button
        int resetY = y + 20 + 12 + 10 + 10 + 10 + 14;
        if (mouseX >= contentX && mouseX < contentX + 70 && mouseY >= resetY && mouseY < resetY + 14) {
            config.totalSpawnersMined = 0;
            config.totalShulkersPacked = 0;
            config.totalMiningTimeMs = 0;
            config.totalPackingTimeMs = 0;
            config.save();
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedTab == 2 && whitelistInput.isFocused()) {
            if (whitelistInput.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (selectedTab == 2 && whitelistInput.isFocused()) {
            if (whitelistInput.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (Section section : sections) {
            if (!section.expanded) continue;
            for (Setting setting : section.settings) {
                if (setting instanceof SettingSlider slider && slider.sliderArea != null) {
                    if (mouseX >= slider.sliderArea[0] && mouseX < slider.sliderArea[2] &&
                        mouseY >= slider.sliderArea[1] && mouseY < slider.sliderArea[3]) {
                        double percent = (mouseX - slider.sliderArea[0]) / (slider.sliderArea[2] - slider.sliderArea[0]);
                        percent = Math.max(0, Math.min(1, percent));
                        slider.value = slider.min + (slider.max - slider.min) * percent;
                        if (slider.isInt) slider.value = Math.round(slider.value);
                        slider.onChange.accept(slider.value);
                        return true;
                    }
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= windowX && mouseX < windowX + windowWidth && 
            mouseY >= windowY + 22 && mouseY < windowY + windowHeight) {
            scrollOffset -= (int)(verticalAmount * 15);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public void close() {
        config.save();
        client.setScreen(parent);
    }
    
    @Override
    public boolean shouldPause() { return false; }
    
    // Inner classes
    private static abstract class Setting {
        String name;
        Setting(String name) { this.name = name; }
    }
    
    private static class SettingToggle extends Setting {
        boolean value;
        java.util.function.Consumer<Boolean> onChange;
        int[] clickArea;
        
        SettingToggle(String name, boolean value, java.util.function.Consumer<Boolean> onChange) {
            super(name);
            this.value = value;
            this.onChange = onChange;
        }
    }
    
    private static class SettingSlider extends Setting {
        double value, min, max;
        boolean isInt;
        java.util.function.Consumer<Double> onChange;
        int[] sliderArea;
        
        SettingSlider(String name, double value, double min, double max, boolean isInt, java.util.function.Consumer<Double> onChange) {
            super(name);
            this.value = value;
            this.min = min;
            this.max = max;
            this.isInt = isInt;
            this.onChange = onChange;
        }
    }
    
    private static class Section {
        String name;
        boolean expanded = true;
        List<Setting> settings = new ArrayList<>();
        
        Section(String name) { this.name = name; }
        void add(Setting s) { settings.add(s); }
    }
}
