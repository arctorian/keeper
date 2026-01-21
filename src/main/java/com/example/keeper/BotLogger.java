package com.example.keeper;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BotLogger {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private static Path logDir;
    private static Path currentLogFile;
    private static String currentDate;
    
    static {
        try {
            logDir = FabricLoader.getInstance().getConfigDir().resolve("keeper_logs");
            Files.createDirectories(logDir);
        } catch (Exception e) {
            System.err.println("[Keeper] Failed to create log directory: " + e);
            logDir = Path.of(".");
        }
    }
    
    public static void info(String message) { log("INFO", message); }
    public static void warn(String message) { log("WARN", message); }
    public static void error(String message) { log("ERROR", message); }
    
    public static void threat(String playerName) {
        log("THREAT", "Detected non-whitelisted player: " + playerName);
    }
    
    public static void spawnerMined(int x, int y, int z, int totalInSession) {
        log("MINED", String.format("Spawner at %d,%d,%d (session total: %d)", x, y, z, totalInSession));
    }
    
    public static void packingStarted() { log("PACK", "Started packing inventory"); }
    
    public static void packingComplete(int shulkersPacked) {
        log("PACK", "Packing complete, shulkers stored: " + shulkersPacked);
    }
    
    public static void ghostBlock(int x, int y, int z) {
        log("GHOST", String.format("Ghost block detected at %d,%d,%d", x, y, z));
    }
    
    public static void ghostCheckStart(int count) {
        log("GHOST", "Starting ghost check for " + count + " locations");
    }
    
    public static void ghostCheckComplete() { log("GHOST", "Ghost check complete"); }
    
    public static void death() { log("DEATH", "Player died!"); }
    
    public static void disconnect(String reason) { log("DISCONNECT", "Disconnected: " + reason); }
    
    public static void status(String newStatus) { log("STATUS", newStatus); }
    
    public static void shopPurchase(String item) { log("SHOP", "Purchased: " + item); }
    
    public static void navigation(String event) { log("NAV", event); }
    
    public static void moveTo(int x, int y, int z, String reason) {
        log("MOVE", String.format("Going to %d,%d,%d - %s", x, y, z, reason));
    }
    
    public static void arrived(int x, int y, int z) {
        log("MOVE", String.format("Arrived at %d,%d,%d", x, y, z));
    }
    
    public static void pathFound(int nodeCount, int x, int y, int z) {
        log("PATH", String.format("Path found with %d nodes to %d,%d,%d", nodeCount, x, y, z));
    }
    
    public static void pathFailed(int x, int y, int z) {
        log("PATH", String.format("No path found to %d,%d,%d", x, y, z));
    }
    
    public static void botStarted() { log("BOT", "=== Keeper started ==="); }
    
    public static void botStopped(int spawnersMined, int shulkersPacked) {
        log("BOT", String.format("=== Keeper stopped === (Spawners: %d, Shulkers: %d)", spawnersMined, shulkersPacked));
    }
    
    public static void sessionSummary(int spawnersMined, int shulkersPacked, long durationMs) {
        long minutes = durationMs / 60000;
        long seconds = (durationMs % 60000) / 1000;
        log("SUMMARY", String.format("Session: %d spawners, %d shulkers, %dm %ds", 
            spawnersMined, shulkersPacked, minutes, seconds));
    }
    
    private static void log(String level, String message) {
        try {
            ensureLogFile();
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            String line = String.format("[%s] [%s] %s%n", timestamp, level, message);
            Files.writeString(currentLogFile, line, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            System.out.print("[Keeper] " + line);
        } catch (Exception e) {
            System.err.println("[Keeper] Failed to write log: " + e);
        }
    }
    
    private static void ensureLogFile() throws IOException {
        String today = LocalDateTime.now().format(FILE_FORMAT);
        if (currentLogFile == null || !today.equals(currentDate)) {
            currentDate = today;
            currentLogFile = logDir.resolve("keeper_" + today + ".log");
            if (!Files.exists(currentLogFile)) {
                String header = String.format("=== Keeper Log - %s ===%n%n", today);
                Files.writeString(currentLogFile, header, StandardOpenOption.CREATE);
            }
        }
    }
    
    public static Path getLogFile() {
        try {
            ensureLogFile();
            return currentLogFile;
        } catch (Exception e) {
            return null;
        }
    }
    
    public static Path getLogDir() { return logDir; }
}
