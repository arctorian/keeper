package com.example.keeper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-updater that checks GitHub releases for new versions,
 * downloads updates, and schedules game restart.
 * 
 * Also handles duplicate version detection to prevent multiple Keeper instances.
 */
public class AutoUpdater {
    
    private static final String GITHUB_API_URL = "https://api.github.com/repos/arctorian/keeper/releases/latest";
    private static final String CURRENT_VERSION = FabricLoader.getInstance()
            .getModContainer("keeper")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");
    
    private static final AtomicBoolean updateInProgress = new AtomicBoolean(false);
    private static String latestVersion = null;
    private static String downloadUrl = null;
    private static String releaseNotes = null;
    
    // Flags to control mod behavior
    private static boolean disabledDueToNewerVersion = false;
    private static String newerVersionFound = null;
    private static boolean updatePendingRestart = false;
    
    public enum UpdateStatus {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        CHECKING,
        ERROR,
        DOWNLOADING,
        READY_TO_INSTALL,
        DISABLED_NEWER_EXISTS
    }
    
    private static UpdateStatus status = UpdateStatus.UP_TO_DATE;
    private static String statusMessage = "";
    
    public static UpdateStatus getStatus() {
        return status;
    }
    
    public static String getStatusMessage() {
        return statusMessage;
    }
    
    public static String getLatestVersion() {
        return latestVersion;
    }
    
    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }
    
    public static String getReleaseNotes() {
        return releaseNotes;
    }
    
    /**
     * Check if this mod instance should be disabled (newer version exists locally)
     */
    public static boolean isDisabled() {
        return disabledDueToNewerVersion;
    }
    
    /**
     * Check if an update has been downloaded and is pending restart
     */
    public static boolean isUpdatePendingRestart() {
        return updatePendingRestart || status == UpdateStatus.READY_TO_INSTALL;
    }
    
    /**
     * Get the newer version that was found locally (if disabled)
     */
    public static String getNewerLocalVersion() {
        return newerVersionFound;
    }
    
    /**
     * Check for duplicate/newer Keeper JARs in mods folder.
     * If a newer version exists, this instance should disable itself.
     * Returns true if this instance should continue running.
     */
    public static boolean checkForDuplicates() {
        try {
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            Pattern versionPattern = Pattern.compile("keeper-([0-9]+\\.[0-9]+\\.[0-9]+)\\.jar", Pattern.CASE_INSENSITIVE);
            
            List<String> foundVersions = new ArrayList<>();
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "keeper-*.jar")) {
                for (Path jar : stream) {
                    String name = jar.getFileName().toString();
                    Matcher matcher = versionPattern.matcher(name);
                    if (matcher.matches()) {
                        String version = matcher.group(1);
                        foundVersions.add(version);
                        
                        // Check if this JAR is newer than us
                        if (isNewerVersion(version, CURRENT_VERSION)) {
                            System.out.println("[Keeper] Found newer version in mods folder: " + version + " (current: " + CURRENT_VERSION + ")");
                            disabledDueToNewerVersion = true;
                            newerVersionFound = version;
                            status = UpdateStatus.DISABLED_NEWER_EXISTS;
                            statusMessage = "Disabled: v" + version + " exists";
                        }
                    }
                }
            }
            
            if (foundVersions.size() > 1) {
                System.out.println("[Keeper] Warning: Multiple Keeper JARs found: " + foundVersions);
            }
            
            if (disabledDueToNewerVersion) {
                System.out.println("[Keeper] This instance (v" + CURRENT_VERSION + ") is disabled. Newer version v" + newerVersionFound + " will handle things.");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("[Keeper] Error checking for duplicates: " + e.getMessage());
            return true; // Continue running if we can't check
        }
    }
    
    /**
     * Check for updates asynchronously
     */
    public static CompletableFuture<UpdateStatus> checkForUpdates() {
        if (disabledDueToNewerVersion) {
            return CompletableFuture.completedFuture(UpdateStatus.DISABLED_NEWER_EXISTS);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            status = UpdateStatus.CHECKING;
            statusMessage = "Checking for updates...";
            
            try {
                URL url = URI.create(GITHUB_API_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Keeper-Mod-Updater");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    status = UpdateStatus.ERROR;
                    statusMessage = "GitHub API returned: " + responseCode;
                    return status;
                }
                
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                JsonObject release = JsonParser.parseString(response.toString()).getAsJsonObject();
                String tagName = release.get("tag_name").getAsString();
                latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                
                // Get release notes
                if (release.has("body") && !release.get("body").isJsonNull()) {
                    releaseNotes = release.get("body").getAsString();
                }
                
                // Find the JAR asset (e.g., keeper-1.1.0.jar)
                JsonArray assets = release.getAsJsonArray("assets");
                for (JsonElement assetElement : assets) {
                    JsonObject asset = assetElement.getAsJsonObject();
                    String assetName = asset.get("name").getAsString();
                    if (assetName.startsWith("keeper-") && assetName.endsWith(".jar") && !assetName.contains("sources")) {
                        downloadUrl = asset.get("browser_download_url").getAsString();
                        break;
                    }
                }
                
                if (downloadUrl == null) {
                    status = UpdateStatus.ERROR;
                    statusMessage = "No JAR found in latest release";
                    return status;
                }
                
                // Compare versions
                if (isNewerVersion(latestVersion, CURRENT_VERSION)) {
                    status = UpdateStatus.UPDATE_AVAILABLE;
                    statusMessage = "Update available: v" + latestVersion;
                    System.out.println("[Keeper] Update available! Current: " + CURRENT_VERSION + " -> Latest: " + latestVersion);
                } else {
                    status = UpdateStatus.UP_TO_DATE;
                    statusMessage = "Up to date (v" + CURRENT_VERSION + ")";
                    System.out.println("[Keeper] Already up to date (v" + CURRENT_VERSION + ")");
                }
                
                return status;
                
            } catch (Exception e) {
                status = UpdateStatus.ERROR;
                statusMessage = "Failed to check: " + e.getMessage();
                System.err.println("[Keeper] Update check failed: " + e.getMessage());
                e.printStackTrace();
                return status;
            }
        });
    }
    
    /**
     * Download and install the update
     */
    public static CompletableFuture<Boolean> downloadAndInstall() {
        if (downloadUrl == null || !updateInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            status = UpdateStatus.DOWNLOADING;
            statusMessage = "Downloading update...";
            
            try {
                // Get mods folder
                Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
                Path newJarPath = modsDir.resolve("keeper-" + latestVersion + ".jar");
                
                // Download the new JAR
                System.out.println("[Keeper] Downloading from: " + downloadUrl);
                URL url = URI.create(downloadUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Keeper-Mod-Updater");
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                
                // Handle redirects (GitHub uses them for downloads)
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                    conn.setRequestProperty("User-Agent", "Keeper-Mod-Updater");
                }
                
                long totalSize = conn.getContentLengthLong();
                long downloaded = 0;
                
                try (InputStream in = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(newJarPath)) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;
                        if (totalSize > 0) {
                            int percent = (int) ((downloaded * 100) / totalSize);
                            statusMessage = "Downloading: " + percent + "%";
                        }
                    }
                }
                
                System.out.println("[Keeper] Downloaded to: " + newJarPath);
                
                // Find and mark the current JAR for deletion
                Path currentJar = findCurrentJar();
                if (currentJar != null && !currentJar.equals(newJarPath)) {
                    // Create a marker file for deletion on restart
                    Path deleteMarker = modsDir.resolve(".keeper_delete_old");
                    Files.writeString(deleteMarker, currentJar.toString());
                    System.out.println("[Keeper] Marked old JAR for deletion: " + currentJar);
                }
                
                status = UpdateStatus.READY_TO_INSTALL;
                statusMessage = "Restart required!";
                updatePendingRestart = true;
                
                return true;
                
            } catch (Exception e) {
                status = UpdateStatus.ERROR;
                statusMessage = "Download failed: " + e.getMessage();
                System.err.println("[Keeper] Download failed: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                updateInProgress.set(false);
            }
        });
    }
    
    /**
     * Find the current mod's JAR file
     */
    private static Path findCurrentJar() {
        try {
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            
            // Look for our specific version
            Path expectedJar = modsDir.resolve("keeper-" + CURRENT_VERSION + ".jar");
            if (Files.exists(expectedJar)) {
                return expectedJar;
            }
            
            // Look for keeper JAR files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "keeper*.jar")) {
                for (Path jar : stream) {
                    String name = jar.getFileName().toString().toLowerCase();
                    // Skip the newly downloaded one
                    if (latestVersion != null && name.contains(latestVersion.toLowerCase())) {
                        continue;
                    }
                    // This is likely our current JAR
                    if (name.startsWith("keeper") && name.endsWith(".jar")) {
                        return jar;
                    }
                }
            }
            
            // Alternative: Try to find via class location
            return Path.of(AutoUpdater.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
                    
        } catch (Exception e) {
            System.err.println("[Keeper] Could not find current JAR: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Delete old JAR files marked for deletion (call on mod init)
     */
    public static void cleanupOldVersions() {
        try {
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path deleteMarker = modsDir.resolve(".keeper_delete_old");
            
            if (Files.exists(deleteMarker)) {
                String oldJarPath = Files.readString(deleteMarker).trim();
                Path oldJar = Path.of(oldJarPath);
                
                if (Files.exists(oldJar)) {
                    Files.delete(oldJar);
                    System.out.println("[Keeper] Deleted old version: " + oldJar);
                }
                
                Files.delete(deleteMarker);
            }
        } catch (Exception e) {
            System.err.println("[Keeper] Failed to cleanup old version: " + e.getMessage());
        }
    }
    
    /**
     * Restart the game to apply the update
     */
    public static void restartGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Show message to player
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§6[Keeper] §fRestarting to apply update..."), false);
        }
        
        // Schedule the restart on main thread
        client.execute(() -> {
            // Disconnect if on server
            if (client.world != null) {
                client.world.disconnect(Text.literal("Restarting for Keeper update"));
            }
            
            // Close the game - user will need to restart manually
            // (Full auto-restart requires OS-specific launcher code)
            client.scheduleStop();
        });
    }
    
    /**
     * Compare semantic versions
     * Returns true if v1 is newer than v2
     */
    static boolean isNewerVersion(String v1, String v2) {
        try {
            int[] ver1 = parseVersion(v1);
            int[] ver2 = parseVersion(v2);
            
            for (int i = 0; i < Math.max(ver1.length, ver2.length); i++) {
                int a = i < ver1.length ? ver1[i] : 0;
                int b = i < ver2.length ? ver2[i] : 0;
                if (a > b) return true;
                if (a < b) return false;
            }
            return false;
        } catch (Exception e) {
            // Fallback to string comparison
            return !v1.equals(v2);
        }
    }
    
    private static int[] parseVersion(String version) {
        // Remove any prefix like "v"
        version = version.replaceAll("^[vV]", "");
        // Remove any suffix like "-beta"
        version = version.split("-")[0];
        
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
        }
        return result;
    }
    
    /**
     * Check for updates on startup if enabled
     */
    public static void checkOnStartup() {
        if (disabledDueToNewerVersion) {
            return;
        }
        
        BotConfig config = BotConfig.get();
        if (config.autoUpdateCheck) {
            System.out.println("[Keeper] Checking for updates...");
            checkForUpdates().thenAccept(updateStatus -> {
                if (updateStatus == UpdateStatus.UPDATE_AVAILABLE && config.autoDownloadUpdates) {
                    System.out.println("[Keeper] Auto-downloading update...");
                    downloadAndInstall();
                }
            });
        }
    }
}
