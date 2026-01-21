package com.example.keeper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.texture.NativeImage;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MojangAPI {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Map<String, Profile> CACHE = new HashMap<>();
    private static final Map<String, int[][]> FACE_PIXELS = new HashMap<>();
    
    public static class Profile {
        public String name;
        public String uuid;
        public String skinUrl;
        public boolean faceLoaded = false;
        public boolean faceLoading = false;
        
        public Profile(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }
    
    public static CompletableFuture<Profile> getProfileFromUsername(String username) {
        if (CACHE.containsKey(username.toLowerCase())) {
            return CompletableFuture.completedFuture(CACHE.get(username.toLowerCase()));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .GET().build();
                
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String name = json.get("name").getAsString();
                    String id = json.get("id").getAsString();
                    String uuid = id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
                    );
                    
                    Profile p = new Profile(name, uuid);
                    fetchSkinData(p);
                    CACHE.put(username.toLowerCase(), p);
                    CACHE.put(uuid, p);
                    return p;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }
    
    public static CompletableFuture<Profile> getProfileFromUUID(String uuid) {
        if (CACHE.containsKey(uuid)) {
            return CompletableFuture.completedFuture(CACHE.get(uuid));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replace("-", "")))
                    .GET().build();
                
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String name = json.get("name").getAsString();
                    String id = json.get("id").getAsString();
                    String dashedUuid = id.length() == 32 ? id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
                    ) : id;
                    
                    Profile p = new Profile(name, dashedUuid);
                    parseSkinData(p, json);
                    CACHE.put(name.toLowerCase(), p);
                    CACHE.put(dashedUuid, p);
                    return p;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new Profile("Unknown", uuid);
        });
    }
    
    private static void fetchSkinData(Profile p) {
        try {
            String undashed = p.uuid.replace("-", "");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + undashed))
                .GET().build();
            
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                parseSkinData(p, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void parseSkinData(Profile p, JsonObject json) {
        if (json.has("properties")) {
            json.getAsJsonArray("properties").forEach(e -> {
                JsonObject prop = e.getAsJsonObject();
                if (prop.get("name").getAsString().equals("textures")) {
                    String value = prop.get("value").getAsString();
                    String decoded = new String(Base64.getDecoder().decode(value));
                    JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
                    if (textures.has("textures") && textures.getAsJsonObject("textures").has("SKIN")) {
                        p.skinUrl = textures.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                    }
                }
            });
        }
    }
    
    public static void loadFacePixels(Profile p) {
        if (p == null || p.faceLoaded || p.faceLoading || p.skinUrl == null) return;
        if (FACE_PIXELS.containsKey(p.uuid)) {
            p.faceLoaded = true;
            return;
        }
        
        p.faceLoading = true;
        
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(p.skinUrl))
                    .GET().build();
                
                HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    NativeImage skin = NativeImage.read(response.body());
                    
                    // Extract 8x8 face pixels with hat overlay
                    int[][] facePixels = new int[8][8];
                    for (int x = 0; x < 8; x++) {
                        for (int y = 0; y < 8; y++) {
                            int pixel = skin.getColorArgb(8 + x, 8 + y);
                            int hatPixel = skin.getColorArgb(40 + x, 8 + y);
                            int hatAlpha = (hatPixel >> 24) & 0xFF;
                            
                            if (hatAlpha > 0) {
                                pixel = (hatAlpha == 255) ? hatPixel : blendPixels(pixel, hatPixel, hatAlpha);
                            }
                            facePixels[x][y] = pixel;
                        }
                    }
                    
                    skin.close();
                    FACE_PIXELS.put(p.uuid, facePixels);
                    p.faceLoaded = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            p.faceLoading = false;
        });
    }
    
    private static int blendPixels(int base, int overlay, int alpha) {
        float a = alpha / 255f;
        float invA = 1f - a;
        
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;
        
        int or = (overlay >> 16) & 0xFF;
        int og = (overlay >> 8) & 0xFF;
        int ob = overlay & 0xFF;
        
        int r = (int)(or * a + br * invA);
        int g = (int)(og * a + bg * invA);
        int b = (int)(ob * a + bb * invA);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    public static int[][] getFacePixels(String uuid) {
        return FACE_PIXELS.get(uuid);
    }
    
    public static boolean isFaceLoaded(Profile p) {
        return p != null && p.faceLoaded && FACE_PIXELS.containsKey(p.uuid);
    }
    
    public static int getColorForUUID(String uuid) {
        int hash = uuid.hashCode();
        int r = 100 + ((hash & 0xFF) % 100);
        int g = 100 + (((hash >> 8) & 0xFF) % 100);
        int b = 100 + (((hash >> 16) & 0xFF) % 100);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
