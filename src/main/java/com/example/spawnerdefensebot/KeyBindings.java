package com.example.spawnerdefensebot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    
    private static KeyBinding toggleBotKey;
    private static KeyBinding statusKey;
    private static KeyBinding emergencyStopKey;
    private static KeyBinding packUpKey;
    private static KeyBinding configKey;
    
    private static SpawnerDefenseBot botInstance;
    
    public static void register(SpawnerDefenseBot bot) {
        botInstance = bot;
        
        toggleBotKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.keeper.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.keeper"
        ));
        
        statusKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.keeper.status", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.keeper"
        ));
        
        emergencyStopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.keeper.stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.keeper"
        ));
        
        packUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.keeper.packup", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.keeper"
        ));
        
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.keeper.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.keeper"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            while (toggleBotKey.wasPressed()) botInstance.toggleDefenseMode();
            while (statusKey.wasPressed()) botInstance.printStatus();
            while (emergencyStopKey.wasPressed()) botInstance.emergencyStop();
            while (packUpKey.wasPressed()) botInstance.startPackingUp();
            
            while (configKey.wasPressed()) {
                client.execute(() -> {
                    if (client.player != null && client.currentScreen == null) {
                        client.setScreen(new ConfigScreen(null));
                    }
                });
            }
        });
        
        System.out.println("[Keeper] Keybindings: P=Toggle defense, U=Pack up, I=Stop, O=Status, Y=Config");
    }
}
