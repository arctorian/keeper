package com.example.spawnerdefensebot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.RegistryKeys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;

public class SpawnerDefenseBot implements ClientModInitializer {

    private boolean running = false;
    private boolean defenseEnabled = true;  // Whether player watching/auto-response is enabled
    private boolean defensePausedForConfirm = false;  // Waiting for confirmation to disable defense
    private String status = "idle";
    
    // Packing state
    private boolean packing = false;
    private int packStep = 0;
    private BlockPos shulkerPos = null;
    private BlockPos echestPos = null;
    private int waitTicks = 0;
    private boolean shulkerBreakStarted = false;  // Track if we've started breaking the shulker
    
    // Ghost check
    private List<BlockPos> minedLocations = new ArrayList<>();
    private int noSpawnerTicks = 0;
    private boolean didGhostCheck = false;
    private int ghostCheckIndex = 0;
    
    // Shop state
    private boolean shopping = false;
    private int shopStep = 0;
    
    // Pickaxe retrieval state
    private boolean retrievingPickaxe = false;
    private int retrieveStep = 0;
    
    // Pickaxe storage state (for end of session)
    private boolean storingPickaxe = false;
    private int storePickStep = 0;
    
    // Movement logging
    private BlockPos lastLoggedDestination = null;
    private String lastLoggedReason = null;
    
    // Ghost detection while mining
    private int spawnerCountBeforeMining = 0;
    private int miningWithoutDropsTicks = 0;
    private BlockPos currentMiningPos = null;
    private boolean miningGhostFix = false;
    private int miningGhostFixTicks = 0;
    
    // Player detection
    private String lastThreatName = null;
    private boolean threatDetected = false;
    private int nearbyPlayerCount = 0;
    private List<String> nearbyPlayerNames = new ArrayList<>();
    
    // Ghost check state
    private boolean checkingGhosts = false;
    private BlockPos cachedGhostStandPos = null;
    private int lastGhostIndex = -1;
    
    // Human-like movement
    private static final java.util.Random rand = new java.util.Random();
    private float targetYaw = 0;
    private float targetPitch = 0;
    private boolean isRotating = false;
    
    // Stats (session)
    public int sessionSpawnersMined = 0;
    public int sessionShulkersPacked = 0;
    public long sessionStartTime = 0;
    public long miningStartTime = 0;
    public long packingStartTime = 0;
    public long sessionMiningTime = 0;
    public long sessionPackingTime = 0;
    
    // Singleton instance access for ConfigScreen
    private static SpawnerDefenseBot instance;
    public static SpawnerDefenseBot getInstance() { return instance; }
    
    @Override
    public void onInitializeClient() {
        instance = this;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register(this::renderHud);
        PathRenderer.init();
        KeyBindings.register(this);
        sessionStartTime = System.currentTimeMillis();
        
        // Load config and apply saved settings
        BotConfig cfg = BotConfig.get();
        PathRenderer.setEnabled(cfg.pathRenderingEnabled);
        defenseEnabled = cfg.defenseEnabled;
        
        // Save config on game shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveStateToConfig();
        }));
        
        System.out.println("[Keeper] Initialized! Defense: " + (defenseEnabled ? "ON" : "OFF"));
    }
    
    private void saveStateToConfig() {
        BotConfig cfg = BotConfig.get();
        cfg.defenseEnabled = defenseEnabled;
        cfg.totalSpawnersMined += sessionSpawnersMined;
        cfg.totalShulkersPacked += sessionShulkersPacked;
        cfg.totalMiningTimeMs += sessionMiningTime;
        cfg.totalPackingTimeMs += sessionPackingTime;
        cfg.save();
    }
    
    private static boolean wasDead = false;
    
    // Auto-eat state
    private boolean isEating = false;
    private int eatingTicks = 0;
    private int previousSlot = -1;
    private float previousPitch = 0;
    
    private void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        
        // ALWAYS disable "Pause On Lost Focus" when defense is enabled or bot is running
        // This allows the bot to work in the background while you do other things
        if ((defenseEnabled || running) && mc.options.pauseOnLostFocus) {
            mc.options.pauseOnLostFocus = false;
        }
        
        // Auto-close the pause menu if it opens while bot is running or defense is enabled
        // This is needed because tabbing out can trigger the pause menu
        if (mc.currentScreen instanceof GameMenuScreen && (running || defenseEnabled)) {
            mc.setScreen(null); // Close the pause menu
            return; // Skip this tick, next tick will continue normally
        }
        
        // Detect death
        if (mc.player.isDead() && !wasDead) {
            wasDead = true;
            BotLogger.death();
            if (running) {
                stop(mc);
            }
        } else if (!mc.player.isDead()) {
            wasDead = false;
        }
        
        // Auto-eat when hungry
        if (handleAutoEat(mc)) {
            return; // Currently eating, skip other actions
        }
        
        // Skip bot logic if a non-container screen is open (config, chat, etc.)
        if (mc.currentScreen != null && !(mc.currentScreen instanceof HandledScreen)) {
            return;
        }
        
        // Always add own UUID to config whitelist
        BotConfig cfg = BotConfig.get();
        String myUuid = mc.player.getUuid().toString();
        if (!cfg.whitelistedUUIDs.contains(myUuid)) {
            cfg.addWhitelist(myUuid);
        }
        
        // Update session time in config periodically
        cfg.totalSessionTimeMs += 50; // roughly 1 tick = 50ms
        
        // Check for threats (always active)
        checkForThreats(mc);
        
        // Check for block breaking activity (always active)
        checkBlockBreaking(mc);
        
        if (!running) return;
        
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        
        if (shopping) {
            doShopping(mc);
        } else if (retrievingPickaxe) {
            doRetrievingPickaxe(mc);
        } else if (storingPickaxe) {
            doStoringPickaxe(mc);
        } else if (packing) {
            doPacking(mc);
        } else {
            doMining(mc);
        }
    }
    
    private void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.options.hudHidden) return;
        if (mc.currentScreen instanceof GameMenuScreen) return;
        if (mc.currentScreen instanceof ConfigScreen) return;
        
        BotConfig cfg = BotConfig.get();
        if (!cfg.hudEnabled) return;
        
        // Hide HUD when defense is off (if configured)
        if (cfg.hideHudWhenDefenseOff && !defenseEnabled && !running) return;
        
        // Position from config
        int x = cfg.hudX;
        int y = cfg.hudY;
        int h = (int)(10 * cfg.hudScale);
        int w = (int)(130 * cfg.hudScale);
        
        // Colors
        int red = 0xFFFF5555;
        int green = 0xFF55FF55;
        int yellow = 0xFFFFFF55;
        int gray = 0xFFAAAAAA;
        int white = 0xFFFFFFFF;
        int darkGray = 0xFF666666;
        
        // Determine main color based on state
        int mainColor = threatDetected ? red : (running ? green : (defenseEnabled ? yellow : gray));
        
        // Count lines to draw
        int lineCount = 0;
        if (cfg.showTitle) lineCount++;
        lineCount++; // Defense status always shown
        if (cfg.showStatus) lineCount++;
        if (cfg.showPlayers) lineCount++;
        if (cfg.showInventory || cfg.showSpawnerCount) lineCount++;
        if (cfg.showMinedTotal || cfg.showPackedCount) lineCount++;
        if (cfg.showUptime || cfg.showWhitelistCount) lineCount++;
        if (cfg.showMiningTime || cfg.showPackingTime) lineCount++;
        
        // Background
        int bgAlpha = cfg.hudBackgroundAlpha << 24;
        context.fill(x - 3, y - 3, x + w + 3, y + (h * lineCount) + 3, bgAlpha);
        
        // Top border line
        context.fill(x - 3, y - 3, x + w + 3, y - 2, mainColor);
        
        if (cfg.showTitle) {
            context.drawText(mc.textRenderer, "Keeper", x, y, mainColor, false);
            y += h;
        }
        
        // Defense status (always shown)
        String defStr = defenseEnabled ? "§aDefense: ON" : "§7Defense: OFF";
        context.drawText(mc.textRenderer, defStr, x, y, defenseEnabled ? green : darkGray, false);
        y += h;
        
        // Status
        if (cfg.showStatus) {
            String statusStr = running ? status : "idle";
            context.drawText(mc.textRenderer, statusStr, x, y, mainColor, false);
            y += h;
        }
        
        // Players
        if (cfg.showPlayers) {
            String pText;
            int pCol;
            if (threatDetected && lastThreatName != null) {
                pText = "! " + lastThreatName;
                pCol = red;
            } else {
                pText = "Players: " + nearbyPlayerCount;
                pCol = green;
            }
            context.drawText(mc.textRenderer, pText, x, y, pCol, false);
            y += h;
        }
        
        // Inventory / Spawner count
        if (cfg.showInventory || cfg.showSpawnerCount) {
            StringBuilder sb = new StringBuilder();
            if (cfg.showInventory) {
                sb.append("Inv:").append(countNonEssential(mc.player));
            }
            if (cfg.showSpawnerCount) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("Sp:").append(countSpawnerItems(mc.player));
            }
            context.drawText(mc.textRenderer, sb.toString(), x, y, white, false);
            y += h;
        }
        
        // Mined / Packed stats
        if (cfg.showMinedTotal || cfg.showPackedCount) {
            StringBuilder sb = new StringBuilder();
            if (cfg.showMinedTotal) {
                sb.append("M:").append(cfg.totalSpawnersMined + sessionSpawnersMined);
            }
            if (cfg.showPackedCount) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("P:").append(cfg.totalShulkersPacked + sessionShulkersPacked);
            }
            context.drawText(mc.textRenderer, sb.toString(), x, y, gray, false);
            y += h;
        }
        
        // Uptime / Whitelist
        if (cfg.showUptime || cfg.showWhitelistCount) {
            StringBuilder sb = new StringBuilder();
            if (cfg.showUptime) {
                long s = (System.currentTimeMillis() - sessionStartTime) / 1000;
                sb.append(s/60).append("m").append(s%60).append("s");
            }
            if (cfg.showWhitelistCount) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("WL:").append(cfg.whitelistedUUIDs.size());
            }
            context.drawText(mc.textRenderer, sb.toString(), x, y, darkGray, false);
            y += h;
        }
        
        // Mining / Packing time
        if (cfg.showMiningTime || cfg.showPackingTime) {
            StringBuilder sb = new StringBuilder();
            if (cfg.showMiningTime) {
                long mt = (cfg.totalMiningTimeMs + sessionMiningTime) / 1000;
                sb.append("Mt:").append(mt/60).append("m");
            }
            if (cfg.showPackingTime) {
                if (sb.length() > 0) sb.append(" ");
                long pt = (cfg.totalPackingTimeMs + sessionPackingTime) / 1000;
                sb.append("Pt:").append(pt/60).append("m");
            }
            context.drawText(mc.textRenderer, sb.toString(), x, y, darkGray, false);
        }
    }
    
    // Block breaking detection
    private boolean blockBreakingDetected = false;
    private BlockPos lastBlockBreakingPos = null;
    
    private final java.util.Map<Long, String> trackedBlocks = new java.util.HashMap<>();
    private final java.util.Map<Long, Integer> blockScanCount = new java.util.HashMap<>();
    private static final int SCANS_TO_CONFIRM = 2;
    private String lastBrokenBlockType = null;
    private long lastFullScanTime = 0;
    private static final long SCAN_INTERVAL_MS = 10000;
    private static final int SCAN_RADIUS = 256;
    
    // Virtual observer position for consistent block tracking
    private BlockPos observerPosition = null;
    private boolean observerInitialized = false;
    
    private boolean canBreakNaturally(Block block) {
        String id = Registries.BLOCK.getId(block).toString().toLowerCase();
        
        // Gravity blocks (sand, gravel, anvils, concrete powder)
        if (id.contains("sand") || id.contains("gravel") || id.contains("anvil") || 
            id.contains("concrete_powder") || id.contains("dragon_egg")) {
            return true;
        }
        
        // Plants and vegetation that can be broken by water/pistons
        if (id.contains("grass") || id.contains("flower") || id.contains("sapling") ||
            id.contains("mushroom") || id.contains("vine") || id.contains("kelp") ||
            id.contains("seagrass") || id.contains("coral") || id.contains("lily") ||
            id.contains("sugar_cane") || id.contains("cactus") || id.contains("bamboo") ||
            id.contains("dead_bush") || id.contains("fern") || id.contains("azalea") ||
            id.contains("moss") || id.contains("spore") || id.contains("dripleaf") ||
            id.contains("glow_lichen") || id.contains("hanging_roots") || id.contains("mangrove_roots") ||
            id.contains("sculk_vein") || id.contains("sweet_berry") || id.contains("crop") ||
            id.contains("wheat") || id.contains("carrot") || id.contains("potato") ||
            id.contains("beetroot") || id.contains("melon") || id.contains("pumpkin") ||
            id.contains("stem") || id.contains("attached") || id.contains("cocoa") ||
            id.contains("nether_wart") || id.contains("chorus") || id.contains("twisting") ||
            id.contains("weeping") || id.contains("cave_vines") || id.contains("pitcher") ||
            id.contains("torchflower")) {
            return true;
        }
        
        // Fire and snow layers
        if (id.contains("fire") || id.contains("snow") || id.contains("powder_snow")) {
            return true;
        }
        
        // Redstone components that can be broken by updates
        if (id.contains("redstone_wire") || id.contains("repeater") || id.contains("comparator") ||
            id.contains("lever") || id.contains("button") || id.contains("pressure_plate") ||
            id.contains("tripwire") || id.contains("string") || id.contains("rail")) {
            return true;
        }
        
        // Torches, signs, banners (can fall off)
        if (id.contains("torch") || id.contains("sign") || id.contains("banner") ||
            id.contains("lantern") || id.contains("candle") || id.contains("head") ||
            id.contains("skull") || id.contains("flower_pot") || id.contains("painting") ||
            id.contains("item_frame") || id.contains("armor_stand")) {
            return true;
        }
        
        // Beds, doors, tall plants (multi-block)
        if (id.contains("bed") || id.contains("door") || id.contains("tall") ||
            id.contains("double") || id.contains("sunflower") || id.contains("lilac") ||
            id.contains("rose_bush") || id.contains("peony") || id.contains("large_fern")) {
            return true;
        }
        
        // Ice that can melt
        if (id.contains("ice") && !id.contains("packed") && !id.contains("blue")) {
            return true;
        }
        
        // Scaffolding
        if (id.contains("scaffolding")) {
            return true;
        }
        
        // Leaves can decay
        if (id.contains("leaves")) {
            return true;
        }
        
        return false;
    }
    
    private boolean isSolidTrackable(BlockState state) {
        if (state.isAir()) return false;
        
        String id = Registries.BLOCK.getId(state.getBlock()).toString().toLowerCase();
        
        // Only track specific underground mining blocks
        if (id.contains("deepslate") ||      // All deepslate variants
            id.contains("tuff") ||            // Tuff and tuff variants
            id.contains("stone") && !id.contains("end_stone") && !id.contains("sandstone") && !id.contains("redstone") && !id.contains("glowstone") ||  // Stone, cobblestone, etc
            id.contains("granite") ||         // Granite
            id.contains("diorite") ||         // Diorite
            id.contains("andesite") ||        // Andesite
            id.contains("calcite") ||         // Calcite
            id.contains("dripstone")) {       // Dripstone
            return true;
        }
        
        return false;
    }
    
    private void checkBlockBreaking(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        
        // Only scan if defense mode is ON - this is an early warning system
        // If defense is off, user doesn't want threat detection
        if (!defenseEnabled) {
            return;
        }
        
        // SKIP scanning while bot is running (mining, packing, etc.)
        // Block scanning is only for IDLE early warning, not during active operations
        if (running || packing || shopping || retrievingPickaxe || checkingGhosts) {
            return;
        }
        
        BlockPos playerPos = mc.player.getBlockPos();
        
        // Initialize observer position if not set, or if player moved VERY far (100+ blocks)
        // The observer is a "virtual freecam" that stays in one place
        if (!observerInitialized || observerPosition == null) {
            observerPosition = playerPos;
            observerInitialized = true;
            trackedBlocks.clear();
            blockScanCount.clear();
            lastFullScanTime = 0;
            System.out.println("[Keeper] Observer initialized at " + observerPosition.toShortString());
        } else if (observerPosition.getSquaredDistance(playerPos) > 10000) { // 100 blocks
            // Player moved very far - reset observer to new position
            observerPosition = playerPos;
            trackedBlocks.clear();
            blockScanCount.clear();
            lastFullScanTime = 0;
            System.out.println("[Keeper] Observer reset to " + observerPosition.toShortString());
        }
        
        // Use observer position for scanning, NOT player position
        // This means player can move/turn freely without affecting block tracking
        BlockPos scanCenter = observerPosition;
        
        long currentTime = System.currentTimeMillis();
        
        // Every SCAN_INTERVAL_MS, do a full scan of render distance
        if (currentTime - lastFullScanTime >= SCAN_INTERVAL_MS) {
            lastFullScanTime = currentTime;
            blockBreakingDetected = false;
            
            // Get render distance from game settings (in chunks)
            int renderDistanceChunks = mc.options.getViewDistance().getValue();
            int scanRadius = Math.min(renderDistanceChunks * 16, SCAN_RADIUS); // Convert to blocks, cap at SCAN_RADIUS
            
            int blocksChecked = 0;
            int brokenBlocks = 0;
            
            // STEP 1: Check ALL currently tracked blocks to see if any were broken
            // This is independent of player position/rotation - we check by stored coordinates
            java.util.List<Long> toRemove = new java.util.ArrayList<>();
            
            for (java.util.Map.Entry<Long, String> entry : trackedBlocks.entrySet()) {
                long key = entry.getKey();
                String wasBlockId = entry.getValue();
                BlockPos checkPos = BlockPos.fromLong(key);
                
                // Check if chunk is loaded for this position
                if (!mc.world.isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                    // Chunk not loaded - can't check, skip but don't remove
                    continue;
                }
                
                // Check if too far from OBSERVER (not player) - outside render distance + buffer
                double distSq = checkPos.getSquaredDistance(scanCenter);
                if (distSq > (scanRadius + 32) * (scanRadius + 32)) {
                    toRemove.add(key);
                    continue;
                }
                
                BlockState state = mc.world.getBlockState(checkPos);
                String currentBlockId = Registries.BLOCK.getId(state.getBlock()).toString();
                Integer scanCount = blockScanCount.getOrDefault(key, 0);
                
                // Check if block was broken (was solid, now is air)
                if (state.isAir() && scanCount >= SCANS_TO_CONFIRM) {
                    // Block WAS a confirmed tracked solid, now it's AIR = someone broke it!
                    
                    // But make sure it wasn't us
                    boolean weBrokeIt = false;
                    if (currentMiningPos != null && currentMiningPos.equals(checkPos)) {
                        weBrokeIt = true;
                    }
                    
                    // Check if it's in our mined locations
                    if (!weBrokeIt) {
                        for (BlockPos minedPos : minedLocations) {
                            if (minedPos.equals(checkPos)) {
                                weBrokeIt = true;
                                break;
                            }
                        }
                    }
                    
                    // Also check if we're currently near it and mining
                    if (!weBrokeIt && currentMiningPos != null) {
                        double distToMining = checkPos.getSquaredDistance(currentMiningPos);
                        if (distToMining < 25) { // Within 5 blocks of where we're mining
                            weBrokeIt = true;
                        }
                    }
                    
                    if (!weBrokeIt) {
                        brokenBlocks++;
                        if (!blockBreakingDetected) {
                            blockBreakingDetected = true;
                            lastBlockBreakingPos = checkPos;
                            lastBrokenBlockType = wasBlockId;
                            System.out.println("[Keeper] ALERT: " + wasBlockId + " at " + 
                                checkPos.toShortString() + " was BROKEN (now air)");
                        }
                    }
                    
                    // Remove from tracking since it's gone
                    toRemove.add(key);
                } else if (!isSolidTrackable(state)) {
                    // Block changed to something non-trackable (but not air) - just stop tracking
                    toRemove.add(key);
                } else if (wasBlockId.equals(currentBlockId)) {
                    // Block still the same, increment scan count
                    blockScanCount.put(key, scanCount + 1);
                } else {
                    // Block changed to different solid type - reset count
                    trackedBlocks.put(key, currentBlockId);
                    blockScanCount.put(key, 1);
                }
                
                blocksChecked++;
            }
            
            // Remove blocks that are gone or too far
            for (Long key : toRemove) {
                trackedBlocks.remove(key);
                blockScanCount.remove(key);
            }
            
            // STEP 2: Add NEW blocks to tracking (using checkerboard to reduce load)
            // This only adds blocks, doesn't check for breaking
            // Scan from OBSERVER position, not player position
            for (int x = -scanRadius; x <= scanRadius; x++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    int absX = scanCenter.getX() + x;
                    int absZ = scanCenter.getZ() + z;
                    
                    // Skip if chunk not loaded
                    if (!mc.world.isChunkLoaded(absX >> 4, absZ >> 4)) {
                        continue;
                    }
                    
                    int minY = mc.world.getBottomY();
                    int maxY = Math.min(mc.world.getTopYInclusive(), 128);
                    
                    for (int y = minY; y <= maxY; y++) {
                        // Checkerboard pattern for adding new blocks (reduces scan load by half)
                        if (((absX + y + absZ) & 1) != 0) {
                            continue;
                        }
                        
                        BlockPos checkPos = new BlockPos(absX, y, absZ);
                        long key = checkPos.asLong();
                        
                        // Skip if already tracking this block
                        if (trackedBlocks.containsKey(key)) {
                            continue;
                        }
                        
                        BlockState state = mc.world.getBlockState(checkPos);
                        if (isSolidTrackable(state)) {
                            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                            trackedBlocks.put(key, blockId);
                            blockScanCount.put(key, 1); // First scan
                        }
                    }
                }
            }
            
            // Debug: Log scan stats occasionally
            if (brokenBlocks > 0) {
                System.out.println("[Keeper] Scan: " + blocksChecked + " checked, " + 
                    brokenBlocks + " broken, " + trackedBlocks.size() + " tracked");
            }
        }
        
        // If block breaking detected, treat as threat
        if (blockBreakingDetected && defenseEnabled) {
            if (!threatDetected) {
                String posStr = lastBlockBreakingPos != null ? lastBlockBreakingPos.toShortString() : "unknown";
                double distance = lastBlockBreakingPos != null ? 
                    Math.sqrt(playerPos.getSquaredDistance(lastBlockBreakingPos)) : 0;
                
                // Get the block type that was broken (stored when detected)
                String brokenBlock = lastBrokenBlockType != null ? 
                    lastBrokenBlockType.replace("minecraft:", "") : "unknown";
                
                msg("§c§l" + brokenBlock.toUpperCase() + " BROKEN §7at " + posStr + " (" + (int)distance + "m away)");
                BotLogger.threat(brokenBlock + " broken at " + posStr + " (" + (int)distance + " blocks away)");
                
                // Play alarm sound
                playAlarm(mc);
            }
            threatDetected = true;
            if (lastThreatName == null) {
                String brokenBlock = lastBrokenBlockType != null ? 
                    lastBrokenBlockType.replace("minecraft:", "") : "block";
                lastThreatName = brokenBlock + " breaker (" + (lastBlockBreakingPos != null ? 
                    (int)Math.sqrt(playerPos.getSquaredDistance(lastBlockBreakingPos)) + "m" : "?") + ")";
            }
            
            // Auto-start if not running
            if (!running && !packing && !shopping && !retrievingPickaxe) {
                running = true;
                status = "threat response (block broken)";
                BotLogger.botStarted();
                BotLogger.status("Started due to block break detection");
            }
        }
    }
    
    private void checkForThreats(MinecraftClient mc) {
        BotConfig cfg = BotConfig.get();
        Set<UUID> whitelist = cfg.getWhitelistAsUUIDs();
        
        nearbyPlayerCount = 0;
        nearbyPlayerNames.clear();
        boolean wasThreat = threatDetected;
        threatDetected = false;
        lastThreatName = null;
        
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            // Skip self
            if (player == mc.player) continue;
            
            nearbyPlayerCount++;
            nearbyPlayerNames.add(player.getName().getString());
            
            // Check if whitelisted (using config)
            if (!whitelist.contains(player.getUuid())) {
                threatDetected = true;
                lastThreatName = player.getName().getString();
            }
        }
        
        // Also check map of block breaking progress (Long2ObjectMap<BlockBreakingInfo>)
        // We can access it via reflection on WorldRenderer
        try {
            var worldRenderer = mc.worldRenderer;
            // Reflect into blockBreakingInfos (Int2ObjectMap)
            // Field name is usually 'blockBreakingInfos' or similar mapping
            // In Yarn: blockBreakingInfos
            // We'll iterate the values if we can access it.
            // Since we can't easily reflect with unknown mappings, we'll try a simpler approach:
            // The map tracks breaking by entity ID.
            
            // If we can't detect block breaking directly, we'll rely on player detection.
            // The user said "breaking blocks well before you can actually see the player".
            // This suggests checking `mc.worldRenderer.getCompletedChunkCount()`? No.
            
            // Let's rely on `mc.world.getPlayers()` which returns all players in render distance, 
            // even if they are far away (but loaded).
            // The normal loop above does this.
            
            // However, maybe they mean purely the block breaking packet?
            // Without a mixin to ClientPlayNetworkHandler.onBlockBreakingProgress, we can't see packets directly.
            
            // We will stick to the player check we have, but ensure it checks ALL loaded players.
            // mc.world.getPlayers() returns all loaded players.
        } catch (Exception e) {
            // Ignore
        }
        
        // If threat detected AND defense is enabled - auto-start the bot
        if (threatDetected && defenseEnabled) {
            if (!wasThreat) {
                // New threat - announce it
                msg("§c§lTHREAT DETECTED: §f" + lastThreatName);
                BotLogger.threat(lastThreatName);
                
                // Play alarm sound - loud and attention-grabbing
                playAlarm(mc);
            }
            
            // If not already doing something, start the process
            if (!running && !packing && !shopping && !retrievingPickaxe) {
                running = true;
                status = "threat response";
                BotLogger.botStarted();
                BotLogger.status("Started due to threat: " + lastThreatName);
            }
        }
    }
    
    // Alarm state
    private long lastAlarmTime = 0;
    private int alarmCount = 0;
    
    private void playAlarm(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        
        long now = System.currentTimeMillis();
        
        // Play multiple alarm sounds in quick succession
        // Reset alarm count if it's been a while
        if (now - lastAlarmTime > 5000) {
            alarmCount = 0;
        }
        
        lastAlarmTime = now;
        
        // Play raid horn / bell sounds - these are loud and distinctive
        // Use world.playSound for client-side sound
        mc.world.playSound(mc.player, mc.player.getBlockPos(), 
            SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.0f, 1.0f);
        mc.world.playSound(mc.player, mc.player.getBlockPos(), 
            SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.0f, 0.5f);
        mc.world.playSound(mc.player, mc.player.getBlockPos(), 
            SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1.0f, 2.0f);
        
        alarmCount++;
    }
    
    private void startGhostCheck(MinecraftClient mc) {
        if (minedLocations.isEmpty()) {
            msg("§7No locations to check for ghosts");
            didGhostCheck = true;
            return;
        }
        
        msg("§7Checking " + minedLocations.size() + " locations for ghost blocks...");
        BotLogger.ghostCheckStart(minedLocations.size());
        checkingGhosts = true;
        ghostCheckIndex = 0;
    }
    
    private int ghostState = 0; // 0=look, 1=click, 2=wait
    
    private void doGhostCheck(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        
        PathRenderer.setGhostCheckPositions(minedLocations, ghostCheckIndex);
        PathRenderer.setTargetBlock(null);
        
        // If any UI is open, close it and move on
        if (mc.currentScreen != null) {
            BotLogger.info("UI opened - ghost block confirmed, closing");
            mc.options.useKey.setPressed(false);
            player.closeHandledScreen();
            ghostCheckIndex++;
            ghostState = 0;
            waitTicks = 10;
            return;
        }
        
        if (ghostCheckIndex >= minedLocations.size()) {
            msg("§aGhost check complete!");
            BotLogger.ghostCheckComplete();
            checkingGhosts = false;
            didGhostCheck = true;
            minedLocations.clear();
            PathRenderer.clearAll();
            mc.options.useKey.setPressed(false);
            ghostState = 0;
            return;
        }
        
        BlockPos ghostPos = minedLocations.get(ghostCheckIndex);
        status = "ghost " + (ghostCheckIndex + 1) + "/" + minedLocations.size();
        
        // Navigate to ghost position if too far
        double dist = player.getPos().distanceTo(Vec3d.ofCenter(ghostPos));
        if (dist > 3.0) { // If further than 3 blocks, walk there
             // Find a spot near the ghost block to stand
             BlockPos standPos = findClearSpotNear(mc, ghostPos);
             if (standPos != null) {
                 status = "walking to ghost " + (ghostCheckIndex + 1);
                 PathRenderer.setTargetBlock(ghostPos);
                 navigateTo(mc, standPos);
                 return;
             }
        }
        
        stopMoving(mc);
        
        switch (ghostState) {
            case 0 -> {
                // Look at ghost block
                lookAt(mc, ghostPos);
                ghostState = 1;
            }
            case 1 -> {
                // Press and hold right-click
                lookAt(mc, ghostPos);
                BotLogger.info("Right-clicking ghost at " + ghostPos.toShortString());
                mc.options.useKey.setPressed(true);
                ghostState = 2;
            }
            case 2 -> {
                // Release right-click and move to next
                mc.options.useKey.setPressed(false);
                ghostCheckIndex++;
                ghostState = 0;
                waitTicks = 10;
            }
        }
    }
    
    // Find an air block adjacent to target that we can stand on
    private BlockPos findClearSpotNear(MinecraftClient mc, BlockPos target) {
        // Check all 4 cardinal directions + the 4 diagonals at same Y level
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},  // cardinal
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, // diagonal
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}    // 2 blocks away
        };
        
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        
        for (int[] off : offsets) {
            BlockPos check = target.add(off[0], 0, off[1]);
            BlockPos below = check.down();
            
            // Need: air at feet, air at head, solid ground below
            boolean feetClear = mc.world.getBlockState(check).isAir();
            boolean headClear = mc.world.getBlockState(check.up()).isAir();
            boolean groundSolid = !mc.world.getBlockState(below).isAir();
            
            if (feetClear && headClear && groundSolid) {
                double dist = playerPos.getSquaredDistance(check);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = check;
                }
            }
        }
        
        // Also check 1 block up/down in case spawner was at different Y
        if (best == null) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int[] off : offsets) {
                    BlockPos check = target.add(off[0], dy, off[1]);
                    BlockPos below = check.down();
                    
                    boolean feetClear = mc.world.getBlockState(check).isAir();
                    boolean headClear = mc.world.getBlockState(check.up()).isAir();
                    boolean groundSolid = !mc.world.getBlockState(below).isAir();
                    
                    if (feetClear && headClear && groundSolid) {
                        double dist = playerPos.getSquaredDistance(check);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = check;
                        }
                    }
                }
            }
        }
        
        return best;
    }
    
    
    private void doMining(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BotConfig cfg = BotConfig.get();
        
        // Track mining time
        if (miningStartTime == 0) {
            miningStartTime = System.currentTimeMillis();
        }
        sessionMiningTime = System.currentTimeMillis() - miningStartTime;
        
        if (checkingGhosts) {
            doGhostCheck(mc);
            return;
        }
        
        // Check for pickaxe - but ONLY if there are spawners to mine
        // Don't retrieve pickaxe just to put it back
        BlockPos spawnerCheck = findNearestSpawner(mc);
        if (spawnerCheck != null && !hasPickaxe(player)) {
            startRetrievingPickaxe(mc);
            return;
        }
        
        if (mc.currentScreen != null) {
            player.closeHandledScreen();
            waitTicks = 5;
            return;
        }
        
        if (countNonEssential(player) >= 27) {
            msg("§eInventory full - packing up");
            releaseAll(mc);
            Pathfinder.clearPath();
            startPacking(mc);
            return;
        }
        
        BlockPos spawner = findNearestSpawner(mc);
        
        if (spawner != null) {
            noSpawnerTicks = 0;
            didGhostCheck = false;
            
            // Update path renderer
            PathRenderer.setTargetBlock(spawner);
            
            // Find the best position to mine from (same Y level, direct line of sight)
            BlockPos miningSpot = findMiningPosition(mc, spawner);
            double dist = player.getPos().distanceTo(Vec3d.ofCenter(miningSpot));
            
            // Check if we can directly hit the spawner from current position
            boolean canHit = canDirectlyHitSpawner(mc, player.getBlockPos(), spawner);
            boolean atMiningSpot = dist < 0.8;
            
            if (!canHit && !atMiningSpot) {
                // Need to walk to the mining spot
                status = "walking to position";
                logMovement(miningSpot, "mining position for spawner");
                navigateTo(mc, miningSpot);
                currentMiningPos = null;
                miningWithoutDropsTicks = 0;
            } else {
                // We can mine - stop moving, clear path
                Pathfinder.clearPath();
                
                boolean justStarted = false;
                if (currentMiningPos == null || !currentMiningPos.equals(spawner)) {
                    currentMiningPos = spawner;
                    spawnerCountBeforeMining = countSpawnerItems(player);
                    miningWithoutDropsTicks = 0;
                    justStarted = true;
                }
                
                int currentSpawners = countSpawnerItems(player);
                if (currentSpawners > spawnerCountBeforeMining) {
                    int gained = currentSpawners - spawnerCountBeforeMining;
                    sessionSpawnersMined += gained;
                    BotLogger.spawnerMined(spawner.getX(), spawner.getY(), spawner.getZ(), sessionSpawnersMined);
                    spawnerCountBeforeMining = currentSpawners;
                    miningWithoutDropsTicks = 0;
                } else {
                    miningWithoutDropsTicks++;
                }
                
                // Handle ghost fix during mining - right click to fix
                if (miningGhostFix) {
                    miningGhostFixTicks++;
                    if (miningGhostFixTicks < 3) {
                        mc.options.useKey.setPressed(true);
                    } else if (miningGhostFixTicks < 10) {
                        mc.options.useKey.setPressed(false);
                        // Close any UI that opened
                        if (mc.currentScreen != null) {
                            player.closeHandledScreen();
                        }
                    } else {
                        // Ghost fix done - resume mining
                        miningGhostFix = false;
                        miningGhostFixTicks = 0;
                        lookAt(mc, spawner);
                        mc.options.sneakKey.setPressed(true);
                        mc.options.attackKey.setPressed(true);
                    }
                    return;
                }
                
                if (miningWithoutDropsTicks > cfg.ghostFixTimeout) {
                    msg("§eNo drops - fixing...");
                    BotLogger.ghostBlock(spawner.getX(), spawner.getY(), spawner.getZ());
                    mc.options.attackKey.setPressed(false);
                    mc.options.sneakKey.setPressed(false);
                    lookAt(mc, spawner);
                    miningGhostFix = true;
                    miningGhostFixTicks = 0;
                    miningWithoutDropsTicks = 0;
                    return;
                }
                
                status = "mining";
                stopMoving(mc);
                
                // Look and equip only when starting
                if (justStarted) {
                    lookAt(mc, spawner);
                    equipPickaxe(mc);
                }
                
                // Always keep crouching while mining
                mc.options.sneakKey.setPressed(true);
                
                // Keep looking at spawner
                lookAt(mc, spawner);
                
                // Use BOTH attackBlock and updateBlockBreakingProgress for reliable mining when tabbed out
                // attackBlock starts the break, updateBlockBreakingProgress continues it each tick
                // This works even when the game window is not focused
                Direction dir = getClosestFace(mc.player.getPos(), spawner);
                if (justStarted) {
                    // Start the break
                    mc.interactionManager.attackBlock(spawner, dir);
                }
                // Continue breaking every tick - this is the key for tabbed out mining
                mc.interactionManager.updateBlockBreakingProgress(spawner, dir);
                mc.options.attackKey.setPressed(true); // Also hold key as backup
                
                if (!minedLocations.contains(spawner)) {
                    minedLocations.add(spawner);
                }
            }
        } else {
            noSpawnerTicks++;
            status = "searching (" + noSpawnerTicks + "/" + cfg.ghostCheckDelay + ")";
            Pathfinder.clearPath();
            
            // Don't release crouch if we were just mining - the spawner might reappear
            if (currentMiningPos != null && noSpawnerTicks < 10) {
                // Keep crouching and looking at where we were mining
                mc.options.sneakKey.setPressed(true);
                mc.options.attackKey.setPressed(true);
                lookAt(mc, currentMiningPos);
            } else {
                // Actually no spawner, stop everything
                currentMiningPos = null;
                releaseAll(mc);
            }
            
            if (noSpawnerTicks >= cfg.ghostCheckDelay && !didGhostCheck && !minedLocations.isEmpty()) {
                startGhostCheck(mc);
                noSpawnerTicks = 0;
            } else if (noSpawnerTicks >= cfg.ghostCheckDelay && (didGhostCheck || minedLocations.isEmpty())) {
                if (countNonEssential(player) > 0) {
                    msg("§aNo more spawners - packing up");
                    startPacking(mc);
                } else if (hasPickaxe(player)) {
                    msg("§7Storing pickaxe...");
                    startStoringPickaxe(mc);
                } else {
                    msg("§aDone! No spawners and nothing to pack.");
                    defenseEnabled = false;
                    msg("§7Defense mode disabled - nothing to protect");
                    syncDefenseToConfig();
                    stop(mc);
                }
            }
        }
    }
    
    private BlockPos findMiningPosition(MinecraftClient mc, BlockPos spawner) {
        BlockPos playerPos = mc.player.getBlockPos();
        BotConfig cfg = BotConfig.get();
        
        // Search for a good position - prioritize same Y level as spawner
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        
        // Search in expanding rings around the spawner
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Check at spawner's Y level first, then +-1
                    for (int dy : new int[]{0, -1, 1}) {
                        BlockPos checkPos = spawner.add(dx, dy, dz);
                        
                        // Skip if not walkable
                        if (!isWalkablePosition(mc, checkPos)) continue;
                        
                        // Check if we can hit spawner from here
                        if (!canDirectlyHitSpawner(mc, checkPos, spawner)) continue;
                        
                        // Calculate distance from player
                        double dist = playerPos.getSquaredDistance(checkPos);
                        
                        // For r=1 (adjacent), we prefer it immensely
                        if (r == 1) {
                            // If we found an adjacent spot, take it immediately if it's closer than previous best adjacent
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = checkPos;
                            }
                        } else {
                            // For further spots, only take if we haven't found anything better
                            if (best == null && dist < bestDist) {
                                bestDist = dist;
                                best = checkPos;
                            }
                        }
                    }
                }
            }
            
            // If we found something in this ring, stop searching further rings
            if (best != null && r == 1) break; 
            // If r > 1, we might want to keep searching for an r=1 spot in next iteration? 
            // Actually the loop order is r=1, r=2... so if we found something at r=1 we break.
            if (best != null) break;
        }
        
        // If current position is valid and we didn't find a better one (or current is the best one)
        if (canDirectlyHitSpawner(mc, playerPos, spawner)) {
             // Only stay if we are close enough (within 2 blocks)
             if (playerPos.getSquaredDistance(spawner) < 5.0) { // sqrt(5) ~= 2.24
                 if (best == null) return playerPos;
                 // If we are already at a good spot, stay
                 if (playerPos.equals(best)) return playerPos;
             }
        }
        
        // Fallback: just walk towards the spawner
        return best != null ? best : spawner;
    }
    
    private boolean isWalkablePosition(MinecraftClient mc, BlockPos pos) {
        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState ground = mc.world.getBlockState(pos.down());
        
        boolean feetClear = !feet.blocksMovement();
        boolean headClear = !head.blocksMovement();
        boolean groundSolid = ground.blocksMovement();
        
        return feetClear && headClear && groundSolid;
    }
    
    private boolean canDirectlyHitSpawner(MinecraftClient mc, BlockPos fromPos, BlockPos spawner) {
        // Eye position at fromPos
        Vec3d eyePos = new Vec3d(fromPos.getX() + 0.5, fromPos.getY() + 1.62, fromPos.getZ() + 0.5);
        Vec3d targetCenter = Vec3d.ofCenter(spawner);
        
        // Distance check
        double dist = eyePos.distanceTo(targetCenter);
        if (dist > 4.5) return false; // Too far
        
        // Ray trace to check for obstructions
        Vec3d dir = targetCenter.subtract(eyePos).normalize();
        double checkDist = dist - 0.5; // Stop before reaching the spawner
        
        for (double d = 0.5; d < checkDist; d += 0.3) {
            Vec3d checkPoint = eyePos.add(dir.multiply(d));
            BlockPos checkBlockPos = new BlockPos(
                (int)Math.floor(checkPoint.x),
                (int)Math.floor(checkPoint.y),
                (int)Math.floor(checkPoint.z)
            );
            
            // Skip the spawner itself
            if (checkBlockPos.equals(spawner)) continue;
            
            BlockState state = mc.world.getBlockState(checkBlockPos);
            
            // If there's a solid block in the way, we can't hit
            if (state.blocksMovement()) {
                return false;
            }
        }
        
        return true;
    }
    
    
    private void logMovement(BlockPos dest, String reason) {
        if (dest == null) return;
        if (!dest.equals(lastLoggedDestination) || !reason.equals(lastLoggedReason)) {
            lastLoggedDestination = dest;
            lastLoggedReason = reason;
            BotLogger.moveTo(dest.getX(), dest.getY(), dest.getZ(), reason);
        }
    }
    
    private void navigateTo(MinecraftClient mc, BlockPos target) {
        ClientPlayerEntity p = mc.player;
        BlockPos playerPos = p.getBlockPos();
        
        // Calculate path if needed
        if (Pathfinder.needsRecalculation(target)) {
            List<BlockPos> path = Pathfinder.findPath(mc.world, playerPos, target);
            if (!path.isEmpty()) {
                BotLogger.pathFound(target.getX(), target.getY(), target.getZ(), path.size());
            }
        }
        
        // Get next waypoint from path
        BlockPos nextWaypoint = Pathfinder.getNextWaypoint(playerPos);
        
        if (nextWaypoint == null) {
            // No path or reached end - walk directly to target as fallback
            walkDirectlyTo(mc, target);
            return;
        }
        
        // Walk to the next waypoint
        walkDirectlyTo(mc, nextWaypoint);
    }
    
    private void walkDirectlyTo(MinecraftClient mc, BlockPos target) {
        ClientPlayerEntity p = mc.player;
        Vec3d pos = p.getPos();
        
        // Calculate direction to target
        double dx = target.getX() + 0.5 - pos.x;
        double dz = target.getZ() + 0.5 - pos.z;
        float yaw = (float)(Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        
        // Face the target
        p.setYaw(yaw);
        p.setPitch(0);
        
        // Walk forward
        mc.options.forwardKey.setPressed(true);
        
        // Jump if needed
        mc.options.jumpKey.setPressed(needsJump(mc, yaw));
    }
    
    private boolean needsJump(MinecraftClient mc, float yaw) {
        ClientPlayerEntity p = mc.player;
        if (!p.isOnGround()) return false;
        
        // Check block directly in front at feet level
        double rad = Math.toRadians(yaw + 90);
        double checkX = p.getX() + Math.cos(rad) * 0.8;
        double checkZ = p.getZ() + Math.sin(rad) * 0.8;
        BlockPos feetPos = new BlockPos((int)Math.floor(checkX), (int)Math.floor(p.getY()), (int)Math.floor(checkZ));
        BlockPos headPos = feetPos.up();
        
        BlockState feetBlock = mc.world.getBlockState(feetPos);
        BlockState headBlock = mc.world.getBlockState(headPos);
        
        // Jump if there's a solid block at feet level and air at head level
        boolean feetBlocked = !feetBlock.isAir() && !feetBlock.getBlock().getTranslationKey().contains("water");
        boolean headClear = headBlock.isAir();
        
        return feetBlocked && headClear;
    }
    
    private void stopMoving(MinecraftClient mc) {
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }
    
    private void releaseAll(MinecraftClient mc) {
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }
    
    private void lookAt(MinecraftClient mc, BlockPos pos) {
        ClientPlayerEntity p = mc.player;
        double dx = pos.getX() + 0.5 - p.getX();
        double dy = pos.getY() + 0.5 - p.getEyeY();
        double dz = pos.getZ() + 0.5 - p.getZ();
        double h = Math.sqrt(dx*dx + dz*dz);
        p.setYaw((float)(Math.atan2(dz, dx) * 180 / Math.PI) - 90);
        p.setPitch((float)(-Math.atan2(dy, h) * 180 / Math.PI));
    }
    
    private void lookAtInstant(MinecraftClient mc, BlockPos pos) {
        lookAt(mc, pos);
    }
    
    private float wrapDegrees(float deg) {
        deg = deg % 360;
        if (deg >= 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }
    
    
    private void startShopping(MinecraftClient mc) {
        msg("§7Opening shop to buy shulker...");
        mc.player.networkHandler.sendChatCommand("shop");
        shopping = true;
        shopStep = 0;
        waitTicks = 20;
    }
    
    private void doShopping(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ScreenHandler handler = player.currentScreenHandler;
        
        switch (shopStep) {
            case 0 -> {
                if (isContainerOpen(mc)) {
                    shopStep = 1;
                    waitTicks = 5;
                } else {
                    waitTicks = 10;
                }
            }
            case 1 -> {
                status = "shop: endstone";
                if (handler == null) { shopStep = 0; return; }
                
                int slot = findSlotWithItem(handler, "end_stone");
                if (slot >= 0) {
                    mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, player);
                    shopStep = 2;
                    waitTicks = 20;
                } else {
                    msg("§cNo endstone in shop!");
                    shopping = false;
                    startPacking(mc);
                }
            }
            case 2 -> {
                status = "shop: shulker";
                if (!isContainerOpen(mc)) { waitTicks = 5; return; }
                if (handler == null) { shopStep = 0; return; }
                
                int slot = findSlotWithItem(handler, "shulker_box");
                if (slot >= 0) {
                    mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, player);
                    shopStep = 3;
                    waitTicks = 20;
                } else {
                    msg("§cNo shulker box in shop!");
                    player.closeHandledScreen();
                    shopping = false;
                    stop(mc);
                }
            }
            case 3 -> {
                status = "shop: confirm";
                if (!isContainerOpen(mc)) { waitTicks = 5; return; }
                if (handler == null) { shopStep = 0; return; }
                
                int containerSize = handler.slots.size() - 36;
                int slot = -1;
                for (int i = containerSize - 1; i >= 0; i--) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!stack.isEmpty()) {
                        String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase();
                        if (id.contains("lime_stained_glass_pane")) {
                            slot = i;
                            break;
                        }
                    }
                }
                
                if (slot >= 0) {
                    mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, player);
                    shopStep = 4;
                    waitTicks = 20;
                } else {
                    msg("§cNo confirm button!");
                    player.closeHandledScreen();
                    shopping = false;
                    stop(mc);
                }
            }
            case 4 -> {
                player.closeHandledScreen();
                msg("§aGot shulker from shop!");
                shopStep = 5;
                waitTicks = 30;
            }
            case 5 -> {
                status = "placing shulker";
                
                // First find if we have a shulker anywhere
                int hotbarSlot = -1;
                int mainInvSlot = -1;
                
                // Check hotbar first (slots 0-8)
                for (int i = 0; i < 9; i++) {
                    if (isShulkerItem(player.getInventory().getStack(i))) {
                        hotbarSlot = i;
                        break;
                    }
                }
                
                // If not in hotbar, check main inventory
                if (hotbarSlot < 0) {
                    for (int i = 9; i < 36; i++) {
                        if (isShulkerItem(player.getInventory().getStack(i))) {
                            mainInvSlot = i;
                            break;
                        }
                    }
                }
                
                // If shulker is in main inv, we need to move it to hotbar
                if (hotbarSlot < 0 && mainInvSlot >= 0) {
                    // Use scroll to select slot 0, then swap
                    player.getInventory().setSelectedSlot(0);
                    // Directly swap via keyboard number key simulation (slot 0)
                    // For now, just use the stack directly - find an empty hotbar slot
                    int emptyHotbar = -1;
                    for (int i = 0; i < 9; i++) {
                        if (player.getInventory().getStack(i).isEmpty()) {
                            emptyHotbar = i;
                            break;
                        }
                    }
                    
                    if (emptyHotbar >= 0) {
                        // Quick move from main inv to hotbar
                        ScreenHandler h = player.currentScreenHandler;
                        mc.interactionManager.clickSlot(h.syncId, mainInvSlot, emptyHotbar, SlotActionType.SWAP, player);
                        waitTicks = 10;
                        return;
                    } else {
                        // Hotbar full, swap with slot 0
                        ScreenHandler h = player.currentScreenHandler;
                        mc.interactionManager.clickSlot(h.syncId, mainInvSlot, 0, SlotActionType.SWAP, player);
                        waitTicks = 10;
                        return;
                    }
                }
                
                if (hotbarSlot < 0 && mainInvSlot < 0) {
                    msg("§cNo shulker in inventory after buying!");
                    BotLogger.error("Shulker not found in inventory after purchase");
                    shopping = false;
                    stop(mc);
                    return;
                }
                
                // Select the shulker
                player.getInventory().setSelectedSlot(hotbarSlot);
                
                BlockPos placePos = findPlaceableSpot(mc);
                if (placePos != null) {
                    lookAt(mc, placePos);
                    waitTicks = 5;
                    shopStep = 6;
                } else {
                    msg("§cNo place for shulker!");
                    BotLogger.error("No valid position to place shulker");
                    shopping = false;
                    stop(mc);
                }
            }
            case 6 -> {
                // Actually place the shulker
                BlockPos placePos = findPlaceableSpot(mc);
                if (placePos != null) {
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(placePos).add(0, 0.5, 0), Direction.UP, placePos, false));
                    
                    msg("§aPlaced shulker!");
                    BotLogger.info("Placed shulker at " + placePos.toShortString());
                    shopping = false;
                    shulkerPos = placePos.up();
                    packing = true;
                    packStep = 0;
                    waitTicks = 15 + rand.nextInt(10);
                } else {
                    msg("§cLost placement spot!");
                    shopping = false;
                    stop(mc);
                }
            }
        }
    }
    
    private int findSlotWithItem(ScreenHandler handler, String itemName) {
        for (int i = 0; i < handler.slots.size() - 36; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase();
                if (id.contains(itemName.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private BlockPos findPlaceableSpot(MinecraftClient mc) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos groundPos = playerPos.add(dx, -1, dz);
                    BlockPos airPos = groundPos.up();
                    Block groundBlock = mc.world.getBlockState(groundPos).getBlock();
                    Block airBlock = mc.world.getBlockState(airPos).getBlock();
                    if (groundBlock != Blocks.AIR && airBlock == Blocks.AIR) {
                        return groundPos;
                    }
                }
            }
        }
        return null;
    }
    
    
    private void startPacking(MinecraftClient mc) {
        // Save mining time and start packing time
        if (miningStartTime > 0) {
            sessionMiningTime = System.currentTimeMillis() - miningStartTime;
            miningStartTime = 0;
        }
        packingStartTime = System.currentTimeMillis();
        
        BotLogger.packingStarted();
        
        shulkerPos = findBlock(mc, b -> b instanceof ShulkerBoxBlock);
        if (shulkerPos == null) {
            msg("§eNo shulker - trying /shop...");
            BotLogger.info("No shulker found, opening shop");
            startShopping(mc);
            return;
        }
        
        echestPos = findBlock(mc, b -> b instanceof EnderChestBlock);
        if (echestPos == null) {
            msg("§cNo ender chest!");
            BotLogger.error("No ender chest found!");
            stop(mc);
            return;
        }
        
        packing = true;
        packStep = 0;
        shulkerBreakStarted = false;
        msg("§7Packing...");
    }
    
    private void doPacking(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        
        // Clear spawner target during packing
        PathRenderer.setTargetBlock(null);
        
        switch (packStep) {
            case 0 -> {
                PathRenderer.setNavTarget(shulkerPos);
                double dist = player.getPos().distanceTo(Vec3d.ofCenter(shulkerPos));
                if (dist > 2.0) {
                    status = "walk to shulker";
                    logMovement(shulkerPos, "storing items in shulker");
                    navigateTo(mc, shulkerPos);
                } else {
                    PathRenderer.setNavTarget(null);
                    BotLogger.arrived(shulkerPos.getX(), shulkerPos.getY(), shulkerPos.getZ());
                    stopMoving(mc);
                    lookAt(mc, shulkerPos);
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(shulkerPos), Direction.UP, shulkerPos, false));
                    packStep = 1;
                    waitTicks = 5;
                }
            }
            case 1 -> {
                if (isContainerOpen(mc)) {
                    packStep = 2;
                } else {
                    waitTicks = 5;
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(shulkerPos), Direction.UP, shulkerPos, false));
                }
            }
            case 2 -> {
                status = "transferring";
                ScreenHandler h = player.currentScreenHandler;
                if (h == null) { packStep = 0; return; }
                
                int start = h.slots.size() - 36;
                boolean transferred = false;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = h.getSlot(start + i).getStack();
                    if (!s.isEmpty() && !isEssential(s)) {
                        mc.interactionManager.clickSlot(h.syncId, start + i, 0, SlotActionType.QUICK_MOVE, player);
                        transferred = true;
                        break;
                    }
                }
                if (!transferred) {
                    player.closeHandledScreen();
                    packStep = 3;
                    waitTicks = 5;
                }
            }
            case 3 -> {
                status = "breaking shulker";
                BlockState shulkerState = mc.world.getBlockState(shulkerPos);
                if (!(shulkerState.getBlock() instanceof ShulkerBoxBlock)) {
                    // Shulker is broken
                    shulkerBreakStarted = false;
                    packStep = 4;
                    waitTicks = 10;
                } else {
                    lookAt(mc, shulkerPos);
                    equipPickaxe(mc);
                    // Use interactionManager for reliable mining when tabbed out
                    Direction dir = getClosestFace(mc.player.getPos(), shulkerPos);
                    if (!shulkerBreakStarted) {
                        // Start the break - only call attackBlock ONCE
                        mc.interactionManager.attackBlock(shulkerPos, dir);
                        shulkerBreakStarted = true;
                    }
                    // Continue breaking every tick
                    mc.interactionManager.updateBlockBreakingProgress(shulkerPos, dir);
                }
            }
            case 4 -> {
                status = "pickup shulker";
                if (hasShulkerItem(player)) {
                    packStep = 5;
                } else {
                    double dist = player.getPos().distanceTo(Vec3d.ofCenter(shulkerPos));
                    if (dist > 0.5) {
                        navigateTo(mc, shulkerPos);
                    } else {
                        stopMoving(mc);
                        waitTicks = 5;
                    }
                }
            }
            case 5 -> {
                PathRenderer.setNavTarget(echestPos);
                double dist = player.getPos().distanceTo(Vec3d.ofCenter(echestPos));
                if (dist > 2.0) {
                    status = "walk to echest";
                    logMovement(echestPos, "storing shulker in ender chest");
                    navigateTo(mc, echestPos);
                } else {
                    PathRenderer.setNavTarget(null);
                    BotLogger.arrived(echestPos.getX(), echestPos.getY(), echestPos.getZ());
                    stopMoving(mc);
                    lookAt(mc, echestPos);
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(echestPos), Direction.UP, echestPos, false));
                    packStep = 6;
                    waitTicks = 5;
                }
            }
            case 6 -> {
                if (isContainerOpen(mc)) {
                    packStep = 7;
                } else {
                    waitTicks = 5;
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(echestPos), Direction.UP, echestPos, false));
                }
            }
            case 7 -> {
                status = "storing shulkers";
                ScreenHandler h = player.currentScreenHandler;
                if (h == null) { packStep = 5; return; }
                
                // Check if echest has space
                int containerSize = h.slots.size() - 36;
                boolean hasSpace = false;
                for (int i = 0; i < containerSize; i++) {
                    if (h.getSlot(i).getStack().isEmpty()) {
                        hasSpace = true;
                        break;
                    }
                }
                
                if (!hasSpace) {
                    msg("§cEnder chest full!");
                    player.closeHandledScreen();
                    packing = false;
                    stop(mc);
                    return;
                }
                
                // Find ANY shulker in player inventory and move it
                int start = h.slots.size() - 36;
                boolean foundShulker = false;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = h.getSlot(start + i).getStack();
                    if (isShulkerItem(s)) {
                        mc.interactionManager.clickSlot(h.syncId, start + i, 0, SlotActionType.QUICK_MOVE, player);
                        foundShulker = true;
                        sessionShulkersPacked++;
                        waitTicks = 3;
                        return; // Stay in step 7, will loop back to check for more
                    }
                }
                
                // If no shulkers, check for pickaxe to store
                if (!foundShulker) {
                    for (int i = 0; i < 36; i++) {
                        ItemStack s = h.getSlot(start + i).getStack();
                        if (isPickaxe(s)) {
                             mc.interactionManager.clickSlot(h.syncId, start + i, 0, SlotActionType.QUICK_MOVE, player);
                             waitTicks = 3;
                             return; // Stay in step 7
                        }
                    }
                    // No shulkers AND no pickaxe -> done
                    packStep = 8;
                }
            }
            case 8 -> {
                mc.player.closeHandledScreen();
                msg("§aAll shulkers stored!");
                BotLogger.packingComplete(sessionShulkersPacked);
                
                // Update packing time
                if (packingStartTime > 0) {
                    sessionPackingTime += System.currentTimeMillis() - packingStartTime;
                    packingStartTime = 0;
                }
                packing = false;
                noSpawnerTicks = 0;
                didGhostCheck = false;
                minedLocations.clear();
                waitTicks = 10;
            }
        }
    }
    

    private void startRetrievingPickaxe(MinecraftClient mc) {
        msg("§7Looking for pickaxe in Ender Chest...");
        
        echestPos = findBlock(mc, b -> b instanceof EnderChestBlock);
        if (echestPos == null) {
            msg("§cNo Ender Chest found nearby!");
            BotLogger.error("Need pickaxe but no Ender Chest found");
            stop(mc);
            return;
        }
        
        retrievingPickaxe = true;
        retrieveStep = 0;
        waitTicks = 5;
    }
    
    private void doRetrievingPickaxe(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        
        switch (retrieveStep) {
            case 0 -> {
                PathRenderer.setNavTarget(echestPos);
                double dist = player.getPos().distanceTo(Vec3d.ofCenter(echestPos));
                if (dist > 2.0) {
                    status = "walk to echest (pickaxe)";
                    logMovement(echestPos, "getting pickaxe from ender chest");
                    navigateTo(mc, echestPos);
                } else {
                    PathRenderer.setNavTarget(null);
                    stopMoving(mc);
                    lookAt(mc, echestPos);
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(echestPos), Direction.UP, echestPos, false));
                    retrieveStep = 1;
                    waitTicks = 5;
                }
            }
            case 1 -> {
                if (isContainerOpen(mc)) {
                    retrieveStep = 2;
                } else {
                    waitTicks = 5;
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(echestPos), Direction.UP, echestPos, false));
                }
            }
            case 2 -> {
                status = "selecting pickaxe";
                ScreenHandler h = player.currentScreenHandler;
                if (h == null) { retrieveStep = 0; return; }
                
                int containerSize = h.slots.size() - 36;
                int bestSlot = -1;
                int bestScore = -1;
                
                for (int i = 0; i < containerSize; i++) {
                    ItemStack s = h.getSlot(i).getStack();
                    if (isPickaxe(s)) {
                        int score = getPickaxeScore(s);
                        if (score > bestScore) {
                            bestScore = score;
                            bestSlot = i;
                        }
                    }
                }
                
                if (bestSlot >= 0) {
                    msg("§aFound pickaxe! Taking it.");
                    mc.interactionManager.clickSlot(h.syncId, bestSlot, 0, SlotActionType.QUICK_MOVE, player);
                    retrieveStep = 3;
                    waitTicks = 10;
                } else {
                    msg("§cNo pickaxe in Ender Chest!");
                    player.closeHandledScreen();
                    retrievingPickaxe = false;
                    stop(mc);
                }
            }
            case 3 -> {
                player.closeHandledScreen();
                retrievingPickaxe = false;
                waitTicks = 10;
                msg("§aResuming mining...");
            }
        }
    }
    
    
    private void startStoringPickaxe(MinecraftClient mc) {
        msg("§7Storing pickaxe in Ender Chest...");
        
        echestPos = findBlock(mc, b -> b instanceof EnderChestBlock);
        if (echestPos == null) {
            msg("§cNo Ender Chest found - keeping pickaxe");
            msg("§aDone! No spawners and nothing to pack.");
            defenseEnabled = false;
            msg("§7Defense mode disabled - nothing to protect");
            syncDefenseToConfig();
            stop(mc);
            return;
        }
        
        storingPickaxe = true;
        storePickStep = 0;
        waitTicks = 5;
    }
    
    private void doStoringPickaxe(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        
        switch (storePickStep) {
            case 0 -> {
                PathRenderer.setNavTarget(echestPos);
                double dist = player.getPos().distanceTo(Vec3d.ofCenter(echestPos));
                if (dist > 2.0) {
                    status = "walk to echest (store pick)";
                    logMovement(echestPos, "storing pickaxe in ender chest");
                    navigateTo(mc, echestPos);
                } else {
                    PathRenderer.setNavTarget(null);
                    stopMoving(mc);
                    lookAt(mc, echestPos);
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(echestPos), Direction.UP, echestPos, false));
                    storePickStep = 1;
                    waitTicks = 5;
                }
            }
            case 1 -> {
                if (isContainerOpen(mc)) {
                    storePickStep = 2;
                } else {
                    waitTicks = 5;
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(echestPos), Direction.UP, echestPos, false));
                }
            }
            case 2 -> {
                status = "storing pickaxe";
                ScreenHandler h = player.currentScreenHandler;
                if (h == null) { storePickStep = 0; return; }
                
                int start = h.slots.size() - 36;
                boolean foundPick = false;
                
                for (int i = 0; i < 36; i++) {
                    ItemStack s = h.getSlot(start + i).getStack();
                    if (isPickaxe(s)) {
                        mc.interactionManager.clickSlot(h.syncId, start + i, 0, SlotActionType.QUICK_MOVE, player);
                        foundPick = true;
                        waitTicks = 5;
                        return; // Stay in step 2 to store more pickaxes if any
                    }
                }
                
                if (!foundPick) {
                    storePickStep = 3;
                }
            }
            case 3 -> {
                player.closeHandledScreen();
                storingPickaxe = false;
                msg("§aPickaxe stored! All done.");
                defenseEnabled = false;
                msg("§7Defense mode disabled - nothing to protect");
                syncDefenseToConfig();
                stop(mc);
            }
        }
    }
    
    private int getPickaxeScore(ItemStack s) {
        String id = Registries.ITEM.getId(s.getItem()).toString().toLowerCase();
        int score = 0;
        
        // Material score
        if (id.contains("netherite")) score += 500;
        else if (id.contains("diamond")) score += 400;
        else if (id.contains("iron")) score += 300;
        else if (id.contains("stone")) score += 200;
        else if (id.contains("wooden")) score += 100;
        else if (id.contains("golden")) score += 50;
        
        // Efficiency score
        try {
             MinecraftClient mc = MinecraftClient.getInstance();
             if (mc.world != null) {
                 var registry = mc.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
                 if (registry != null) {
                    var entry = registry.getEntry(Enchantments.EFFICIENCY.getValue()).orElse(null);
                    if (entry != null) {
                         int level = EnchantmentHelper.getLevel(entry, s);
                         score += level * 10;
                    }
                 }
             }
        } catch (Throwable t) {
            // Ignore
        }
        
        return score;
    }

    
    private BlockPos findNearestSpawner(MinecraftClient mc) {
        BotConfig cfg = BotConfig.get();
        BlockPos pp = mc.player.getBlockPos();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        int r = cfg.searchRadius;
        int yr = cfg.searchYRange;
        for (int x = -r; x <= r; x++) {
            for (int y = -yr; y <= yr; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = pp.add(x, y, z);
                    String blockId = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString();
                    if (blockId.toLowerCase().contains("spawner")) {
                        double dist = pp.getSquaredDistance(pos);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }
    
    private BlockPos findBlock(MinecraftClient mc, java.util.function.Predicate<Block> test) {
        BotConfig cfg = BotConfig.get();
        BlockPos pp = mc.player.getBlockPos();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        int r = cfg.searchRadius;
        int yr = cfg.searchYRange;
        for (int x = -r; x <= r; x++) {
            for (int y = -yr; y <= yr; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = pp.add(x, y, z);
                    if (test.test(mc.world.getBlockState(pos).getBlock())) {
                        // Check line of sight
                        if (!hasLineOfSight(mc, pp, pos)) continue;
                        
                        double dist = pp.getSquaredDistance(pos);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }
    
    private boolean hasLineOfSight(MinecraftClient mc, BlockPos from, BlockPos to) {
        Vec3d start = new Vec3d(from.getX() + 0.5, from.getY() + 1.62, from.getZ() + 0.5); // Eye height
        Vec3d end = Vec3d.ofCenter(to);
        
        Vec3d dir = end.subtract(start);
        double dist = dir.length();
        if (dist < 0.1) return true;
        
        dir = dir.normalize();
        
        // Step along the ray
        for (double d = 0.5; d < dist - 0.5; d += 0.5) {
            Vec3d point = start.add(dir.multiply(d));
            BlockPos checkPos = new BlockPos(
                (int)Math.floor(point.x),
                (int)Math.floor(point.y),
                (int)Math.floor(point.z)
            );
            
            // Skip start and end positions
            if (checkPos.equals(from) || checkPos.equals(to)) continue;
            
            BlockState state = mc.world.getBlockState(checkPos);
            if (state.blocksMovement() && state.isOpaque()) {
                return false; // Blocked
            }
        }
        
        return true;
    }
    
    private boolean isPickaxe(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).toString().contains("pickaxe");
    }
    
    private Direction getClosestFace(Vec3d playerPos, BlockPos blockPos) {
        double dx = playerPos.x - (blockPos.getX() + 0.5);
        double dy = playerPos.y - (blockPos.getY() + 0.5);
        double dz = playerPos.z - (blockPos.getZ() + 0.5);
        
        double adx = Math.abs(dx);
        double ady = Math.abs(dy);
        double adz = Math.abs(dz);
        
        if (adx > ady && adx > adz) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (ady > adz) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
    
    private boolean hasPickaxe(ClientPlayerEntity p) {
        for (int i = 0; i < 36; i++) {
            if (isPickaxe(p.getInventory().getStack(i))) return true;
        }
        return false;
    }
    
    private void equipPickaxe(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        
        // First check hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            if (isPickaxe(player.getInventory().getStack(i))) {
                // Set the slot locally
                player.getInventory().setSelectedSlot(i);
                // Also send the packet to server - this is needed when tabbed out
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(i));
                }
                return;
            }
        }
        
        // If pickaxe is in inventory but not hotbar, swap it to current slot
        int currentSlot = player.getInventory().getSelectedSlot();
        for (int i = 9; i < 36; i++) {
            if (isPickaxe(player.getInventory().getStack(i))) {
                // Swap from inventory slot i to hotbar slot currentSlot
                if (mc.interactionManager != null) {
                    // Pick up from inventory
                    mc.interactionManager.clickSlot(player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, player);
                    // Put in hotbar
                    mc.interactionManager.clickSlot(player.currentScreenHandler.syncId, 36 + currentSlot, 0, SlotActionType.PICKUP, player);
                    // Put whatever was in hotbar back in inventory
                    mc.interactionManager.clickSlot(player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, player);
                }
                return;
            }
        }
    }
    
    private int countNonEssential(ClientPlayerEntity p) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && !isEssential(s)) count++;
        }
        return count;
    }
    
    private int countSpawnerItems(ClientPlayerEntity p) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty()) {
                String id = Registries.ITEM.getId(s.getItem()).toString().toLowerCase();
                if (id.contains("spawner")) {
                    count += s.getCount();
                }
            }
        }
        return count;
    }
    
    private boolean handleAutoEat(MinecraftClient mc) {
        BotConfig cfg = BotConfig.get();
        if (!cfg.autoEatEnabled) return false;
        
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) return false;
        
        int foodLevel = player.getHungerManager().getFoodLevel();
        
        // If currently eating
        if (isEating) {
            eatingTicks++;
            
            // Keep looking up at sky so we don't interact with blocks
            player.setPitch(-90);
            
            // Keep trying to eat by holding use key
            mc.options.useKey.setPressed(true);
            
            // Check if we're done eating (hunger full or ate enough)
            boolean doneEating = foodLevel >= 20 || eatingTicks > 50;
            
            // Also check if we somehow stopped using the item after the initial frames
            if (eatingTicks > 5 && !player.isUsingItem() && foodLevel > cfg.hungerThreshold) {
                doneEating = true;
            }
            
            if (doneEating) {
                // Stop eating
                mc.options.useKey.setPressed(false);
                player.stopUsingItem();
                
                // Restore pitch
                player.setPitch(previousPitch);
                
                // Restore previous slot
                if (previousSlot >= 0) {
                    player.getInventory().setSelectedSlot(previousSlot);
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(previousSlot));
                    }
                }
                
                isEating = false;
                eatingTicks = 0;
                previousSlot = -1;
                return false;
            }
            
            return true; // Still eating - block other actions
        }
        
        // Check if we need to eat
        if (foodLevel <= cfg.hungerThreshold) {
            int foodSlot = findFoodInHotbar(player);
            
            if (foodSlot < 0) {
                foodSlot = moveFoodToHotbar(mc, player);
            }
            
            if (foodSlot >= 0) {
                // Save current state
                previousSlot = player.getInventory().getSelectedSlot();
                previousPitch = player.getPitch();
                
                // Switch to food slot
                player.getInventory().setSelectedSlot(foodSlot);
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(foodSlot));
                }
                
                // Look up at sky so right-click doesn't interact with blocks
                player.setPitch(-90);
                
                // Start eating
                isEating = true;
                eatingTicks = 0;
                mc.options.useKey.setPressed(true);
                
                return true;
            }
        }
        
        return false;
    }
    
    private int findFoodInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isFood(stack)) {
                return i;
            }
        }
        return -1;
    }
    
    private int moveFoodToHotbar(MinecraftClient mc, ClientPlayerEntity player) {
        // Find food in main inventory
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isFood(stack)) {
                // Find empty hotbar slot or use slot 8 (last slot)
                int targetSlot = 8;
                for (int j = 0; j < 9; j++) {
                    if (player.getInventory().getStack(j).isEmpty()) {
                        targetSlot = j;
                        break;
                    }
                }
                
                // Swap food to hotbar
                if (mc.interactionManager != null) {
                    mc.interactionManager.clickSlot(player.currentScreenHandler.syncId, i, targetSlot, SlotActionType.SWAP, player);
                    return targetSlot;
                }
            }
        }
        return -1;
    }
    
    private boolean isEssential(ItemStack s) {
        if (s.isEmpty()) return false;
        String id = Registries.ITEM.getId(s.getItem()).toString().toLowerCase();
        return id.contains("pickaxe") || id.contains("shulker") || id.contains("ender_chest") ||
               id.contains("sword") || id.contains("axe") || id.contains("shovel") ||
               id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots") ||
               isFood(s);
    }
    
    private boolean isFood(ItemStack s) {
        if (s.isEmpty()) return false;
        // Check if item has food component (1.21+ way)
        if (s.getItem().getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)) {
            return true;
        }
        // Fallback: check common food item names
        String id = Registries.ITEM.getId(s.getItem()).toString().toLowerCase();
        return id.contains("apple") || id.contains("bread") || id.contains("beef") || id.contains("pork") ||
               id.contains("chicken") || id.contains("mutton") || id.contains("rabbit") || id.contains("cod") ||
               id.contains("salmon") || id.contains("potato") || id.contains("carrot") || id.contains("beetroot") ||
               id.contains("melon") || id.contains("berry") || id.contains("stew") || id.contains("pie") ||
               id.contains("cookie") || id.contains("cake") || id.contains("kelp") || id.contains("chorus");
    }
    
    private boolean isShulkerItem(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).toString().contains("shulker");
    }
    
    private boolean hasShulkerItem(ClientPlayerEntity p) {
        for (int i = 0; i < 36; i++) {
            if (isShulkerItem(p.getInventory().getStack(i))) return true;
        }
        return false;
    }
    
    private boolean isContainerOpen(MinecraftClient mc) {
        return mc.currentScreen instanceof HandledScreen &&
               mc.player.currentScreenHandler != null &&
               mc.player.currentScreenHandler.slots.size() > 36;
    }
    
    private void msg(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(text), false);
        }
    }
    
    private void stop(MinecraftClient mc) {
        releaseAll(mc);
        running = false;
        packing = false;
        shopping = false;
        storingPickaxe = false;
        retrievingPickaxe = false;
        checkingGhosts = false;
        status = "stopped";
        
        // Clear path rendering
        PathRenderer.clearAll();
        
        // Log stop event
        BotLogger.botStopped(sessionSpawnersMined, sessionShulkersPacked);
        
        // Save stats to config
        BotConfig cfg = BotConfig.get();
        cfg.totalSpawnersMined += sessionSpawnersMined;
        cfg.totalShulkersPacked += sessionShulkersPacked;
        cfg.totalMiningTimeMs += sessionMiningTime;
        cfg.totalPackingTimeMs += sessionPackingTime;
        cfg.save();
        
        // Reset session stats
        sessionSpawnersMined = 0;
        sessionShulkersPacked = 0;
        sessionMiningTime = 0;
        sessionPackingTime = 0;
        miningStartTime = 0;
        packingStartTime = 0;
    }
    
    
    public void addWhitelistedPlayer(String uuidString) {
        try {
            UUID.fromString(uuidString); // Validate format
            BotConfig.get().addWhitelist(uuidString);
            msg("§aAdded player to whitelist: " + uuidString);
        } catch (Exception e) {
            msg("§cInvalid UUID: " + uuidString);
        }
    }
    
    public void toggleDefenseMode() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (defenseEnabled) {
            if (defensePausedForConfirm) return;
            defensePausedForConfirm = true;
            msg("§eDefense paused - confirm disable within 5s");
            mc.setScreen(new ConfirmStopScreen(this));
        } else {
            msg("§aDefense mode ENABLED - watching for players");
            defenseEnabled = true;
            defensePausedForConfirm = false;
            observerInitialized = false;
            observerPosition = null;
            trackedBlocks.clear();
            blockScanCount.clear();
            syncDefenseToConfig();
        }
    }
    
    private void syncDefenseToConfig() {
        BotConfig cfg = BotConfig.get();
        cfg.defenseEnabled = defenseEnabled;
        cfg.save();
    }
    
    public void confirmStop() {
        msg("§cDefense mode DISABLED - not watching for players");
        defenseEnabled = false;
        defensePausedForConfirm = false;
        observerInitialized = false;
        observerPosition = null;
        trackedBlocks.clear();
        blockScanCount.clear();
        syncDefenseToConfig();
    }
    
    public void cancelStop() {
        msg("§aDefense mode remains ENABLED");
        defensePausedForConfirm = false;
    }
    
    public boolean isDefenseEnabled() {
        return defenseEnabled;
    }
    
    public void setDefenseEnabled(boolean enabled) {
        this.defenseEnabled = enabled;
        if (enabled) {
            observerInitialized = false;
            observerPosition = null;
            trackedBlocks.clear();
            blockScanCount.clear();
        }
    }
    
    public void startPackingUp() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (running) {
            msg("§cAlready running!");
            return;
        }
        running = true;
        if (countNonEssential(mc.player) == 0) {
            msg("§aStarting mining...");
            packing = false;
            shopping = false;
            retrievingPickaxe = false;
            checkingGhosts = false;
            noSpawnerTicks = 0;
            didGhostCheck = false;
            minedLocations.clear();
            status = "starting";
            miningStartTime = System.currentTimeMillis();
        } else {
            msg("§aStarting packing...");
            startPacking(mc);
        }
    }
    
    public void emergencyStop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        stop(mc);
        checkingGhosts = false;
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        msg("§c[EMERGENCY STOP]");
    }
    
    public void printStatus() {
        MinecraftClient mc = MinecraftClient.getInstance();
        BotConfig cfg = BotConfig.get();
        msg("§6Running: §f" + running);
        msg("§6Status: §f" + status);
        msg("§6Threat: §f" + (threatDetected ? lastThreatName : "None"));
        msg("§6Nearby players: §f" + nearbyPlayerCount);
        msg("§6Whitelisted: §f" + cfg.whitelistedUUIDs.size());
        msg("§6Inventory: §f" + countNonEssential(mc.player) + " non-essentials");
        msg("§6Session spawners: §f" + sessionSpawnersMined);
        msg("§6Total spawners: §f" + (cfg.totalSpawnersMined + sessionSpawnersMined));
    }
}
