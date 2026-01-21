package com.example.spawnerdefensebot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class BotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath = null;
    private static BotConfig instance;
    
    private static Path getConfigPath() {
        if (configPath == null) {
            try {
                configPath = FabricLoader.getInstance().getConfigDir().resolve("keeper.json");
            } catch (Exception e) {
                System.err.println("[Keeper] Failed to get config dir: " + e);
                configPath = Path.of("keeper.json");
            }
        }
        return configPath;
    }
    
    public static BotConfig get() {
        if (instance == null) {
            try {
                instance = load();
            } catch (Exception e) {
                System.err.println("[Keeper] Config load failed: " + e);
                instance = new BotConfig();
            }
        }
        return instance;
    }
    
    // Whitelist
    public List<String> whitelistedUUIDs = new ArrayList<>();
    
    // HUD
    public boolean hudEnabled = true;
    public boolean hideHudWhenDefenseOff = true;
    public int hudX = 5;
    public int hudY = 5;
    public float hudScale = 1.0f;
    public int hudBackgroundAlpha = 200;
    
    public boolean showTitle = true;
    public boolean showStatus = true;
    public boolean showPlayers = true;
    public boolean showInventory = true;
    public boolean showSpawnerCount = true;
    public boolean showPackedCount = true;
    public boolean showMinedTotal = true;
    public boolean showUptime = true;
    public boolean showWhitelistCount = true;
    public boolean showPackingTime = true;
    public boolean showMiningTime = true;
    
    // Bot
    public boolean defenseEnabled = true;
    public boolean autoEatEnabled = true;
    public int hungerThreshold = 14;
    public int searchRadius = 30;
    public int searchYRange = 8;
    public double miningDistance = 4.0;
    public double walkDistance = 1.5;
    public int ghostCheckDelay = 40;
    public int ghostFixTimeout = 60;
    
    // Rendering
    public boolean pathRenderingEnabled = true;
    
    // Stats
    public long totalSpawnersMined = 0;
    public long totalShulkersPacked = 0;
    public long totalMiningTimeMs = 0;
    public long totalPackingTimeMs = 0;
    public long totalSessionTimeMs = 0;
    
    public static BotConfig load() {
        try {
            Path path = getConfigPath();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                BotConfig config = GSON.fromJson(json, BotConfig.class);
                if (config != null) {
                    if (config.whitelistedUUIDs == null) {
                        config.whitelistedUUIDs = new ArrayList<>();
                    }
                    return config;
                }
            }
        } catch (Exception e) {
            System.err.println("[Keeper] Failed to load config: " + e.getMessage());
        }
        return new BotConfig();
    }
    
    public void save() {
        try {
            Path path = getConfigPath();
            String json = GSON.toJson(this);
            Files.writeString(path, json);
        } catch (Exception e) {
            System.err.println("[Keeper] Failed to save config: " + e.getMessage());
        }
    }
    
    public void addWhitelist(String uuid) {
        if (!whitelistedUUIDs.contains(uuid)) {
            whitelistedUUIDs.add(uuid);
            save();
        }
    }
    
    public void removeWhitelist(String uuid) {
        whitelistedUUIDs.remove(uuid);
        save();
    }
    
    public boolean isWhitelisted(UUID uuid) {
        return whitelistedUUIDs.contains(uuid.toString());
    }
    
    public Set<UUID> getWhitelistAsUUIDs() {
        Set<UUID> set = new HashSet<>();
        for (String s : whitelistedUUIDs) {
            try {
                set.add(UUID.fromString(s));
            } catch (Exception ignored) {}
        }
        return set;
    }
}
