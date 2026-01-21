package com.example.spawnerdefensebot;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class PathRenderer {
    
    private static BlockPos targetBlock = null;
    private static List<BlockPos> ghostCheckPositions = new ArrayList<>();
    private static int currentGhostIndex = 0;
    private static boolean enabled = true;
    
    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PathRenderer::onWorldRender);
    }
    
    public static void setTargetBlock(BlockPos pos) { targetBlock = pos; }
    public static void setNavTarget(BlockPos pos) { targetBlock = pos; }
    
    public static void setGhostCheckPositions(List<BlockPos> positions, int currentIndex) {
        ghostCheckPositions = new ArrayList<>(positions);
        currentGhostIndex = currentIndex;
    }
    
    public static void clearAll() {
        targetBlock = null;
        ghostCheckPositions.clear();
        Pathfinder.clearPath();
    }
    
    public static void setEnabled(boolean e) { enabled = e; }
    
    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled) return;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        
        MatrixStack matrices = context.matrixStack();
        Vec3d cam = context.camera().getPos();
        
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        
        MatrixStack.Entry entry = matrices.peek();
        
        // Draw path
        List<BlockPos> path = Pathfinder.getCurrentPath();
        int pathIndex = Pathfinder.getPathIndex();
        
        if (path != null && path.size() > 1) {
            // Completed segments (green)
            for (int i = 0; i < pathIndex && i < path.size() - 1; i++) {
                drawPathSegment(entry, lines, path.get(i), path.get(i + 1), 0.2f, 1.0f, 0.2f, 0.6f);
            }
            // Remaining segments (cyan)
            for (int i = Math.max(0, pathIndex); i < path.size() - 1; i++) {
                drawPathSegment(entry, lines, path.get(i), path.get(i + 1), 0.0f, 1.0f, 1.0f, 0.8f);
            }
        }
        
        // Target block (red outline)
        if (targetBlock != null) {
            drawBlockOutline(entry, lines, targetBlock, 1.0f, 0.3f, 0.3f, 1.0f);
        }
        
        // Ghost check positions
        for (int i = 0; i < ghostCheckPositions.size(); i++) {
            BlockPos pos = ghostCheckPositions.get(i);
            if (i < currentGhostIndex) {
                drawBlockOutline(entry, lines, pos, 0.2f, 1.0f, 0.2f, 0.5f);
            } else if (i == currentGhostIndex) {
                drawBlockOutline(entry, lines, pos, 1.0f, 1.0f, 0.2f, 1.0f);
            } else {
                drawBlockOutline(entry, lines, pos, 0.2f, 0.7f, 0.7f, 0.4f);
            }
        }
        
        matrices.pop();
    }
    
    private static void drawPathSegment(MatrixStack.Entry entry, VertexConsumer buffer,
                                        BlockPos from, BlockPos to,
                                        float r, float g, float b, float a) {
        Matrix4f matrix = entry.getPositionMatrix();
        
        double x1 = from.getX() + 0.5;
        double y1 = from.getY() + 0.5;
        double z1 = from.getZ() + 0.5;
        
        double x2 = to.getX() + 0.5;
        double y2 = to.getY() + 0.5;
        double z2 = to.getZ() + 0.5;
        
        drawLine(buffer, matrix, entry, (float)x1, (float)y1, (float)z1, (float)x2, (float)y2, (float)z2, r, g, b, a);
    }
    
    private static void drawBlockOutline(MatrixStack.Entry entry, VertexConsumer buffer, 
                                         BlockPos pos, float r, float g, float b, float a) {
        Matrix4f matrix = entry.getPositionMatrix();
        
        float x1 = pos.getX();
        float y1 = pos.getY();
        float z1 = pos.getZ();
        float x2 = x1 + 1;
        float y2 = y1 + 1;
        float z2 = z1 + 1;
        
        // Bottom
        drawLine(buffer, matrix, entry, x1, y1, z1, x2, y1, z1, r, g, b, a);
        drawLine(buffer, matrix, entry, x2, y1, z1, x2, y1, z2, r, g, b, a);
        drawLine(buffer, matrix, entry, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawLine(buffer, matrix, entry, x1, y1, z2, x1, y1, z1, r, g, b, a);
        
        // Top
        drawLine(buffer, matrix, entry, x1, y2, z1, x2, y2, z1, r, g, b, a);
        drawLine(buffer, matrix, entry, x2, y2, z1, x2, y2, z2, r, g, b, a);
        drawLine(buffer, matrix, entry, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawLine(buffer, matrix, entry, x1, y2, z2, x1, y2, z1, r, g, b, a);
        
        // Verticals
        drawLine(buffer, matrix, entry, x1, y1, z1, x1, y2, z1, r, g, b, a);
        drawLine(buffer, matrix, entry, x2, y1, z1, x2, y2, z1, r, g, b, a);
        drawLine(buffer, matrix, entry, x2, y1, z2, x2, y2, z2, r, g, b, a);
        drawLine(buffer, matrix, entry, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }
    
    private static void drawLine(VertexConsumer buffer, Matrix4f matrix, MatrixStack.Entry entry,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        
        if (len > 0.001f) { dx /= len; dy /= len; dz /= len; }
        else { dx = 0; dy = 1; dz = 0; }
        
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(entry, dx, dy, dz);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(entry, dx, dy, dz);
    }
}
