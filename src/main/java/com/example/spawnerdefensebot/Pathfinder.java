package com.example.spawnerdefensebot;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;

import java.util.*;

public class Pathfinder {
    
    private static final int MAX_ITERATIONS = 1000;
    private static final int MAX_PATH_LENGTH = 100;
    
    private static List<BlockPos> currentPath = new ArrayList<>();
    private static BlockPos pathTarget = null;
    private static int pathIndex = 0;
    
    public static List<BlockPos> findPath(World world, BlockPos start, BlockPos goal) {
        if (world == null || start == null || goal == null) return Collections.emptyList();
        if (start.equals(goal)) return Collections.emptyList();
        
        if (goal.equals(pathTarget) && !currentPath.isEmpty() && pathIndex < currentPath.size()) {
            return currentPath;
        }
        
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();
        
        Node startNode = new Node(start, null, 0, heuristic(start, goal));
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        int iterations = 0;
        
        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();
            
            if (current.pos.isWithinDistance(goal, 1.5)) {
                List<BlockPos> path = reconstructPath(current);
                currentPath = path;
                pathTarget = goal;
                pathIndex = 0;
                return path;
            }
            
            closedSet.add(current.pos);
            
            for (BlockPos neighbor : getNeighbors(world, current.pos)) {
                if (closedSet.contains(neighbor)) continue;
                
                double tentativeG = current.gCost + moveCost(current.pos, neighbor);
                
                Node neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    neighborNode = new Node(neighbor, current, tentativeG, heuristic(neighbor, goal));
                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeG < neighborNode.gCost) {
                    openSet.remove(neighborNode);
                    neighborNode.parent = current;
                    neighborNode.gCost = tentativeG;
                    neighborNode.fCost = tentativeG + neighborNode.hCost;
                    openSet.add(neighborNode);
                }
            }
        }
        
        // Return partial path to closest node if no complete path found
        if (!allNodes.isEmpty()) {
            Node closest = allNodes.values().stream()
                .min(Comparator.comparingDouble(n -> heuristic(n.pos, goal)))
                .orElse(null);
            if (closest != null && !closest.pos.equals(start)) {
                List<BlockPos> partial = reconstructPath(closest);
                currentPath = partial;
                pathTarget = goal;
                pathIndex = 0;
                return partial;
            }
        }
        
        currentPath = Collections.emptyList();
        pathTarget = null;
        return Collections.emptyList();
    }
    
    public static BlockPos getNextWaypoint(BlockPos playerPos) {
        if (currentPath.isEmpty()) return null;
        
        double minDist = Double.MAX_VALUE;
        int closestIndex = pathIndex;
        
        for (int i = pathIndex; i < Math.min(pathIndex + 5, currentPath.size()); i++) {
            double dist = playerPos.getSquaredDistance(currentPath.get(i));
            if (dist < minDist) {
                minDist = dist;
                closestIndex = i;
            }
        }
        
        if (closestIndex < currentPath.size() && playerPos.isWithinDistance(currentPath.get(closestIndex), 1.0)) {
            pathIndex = closestIndex + 1;
        } else {
            pathIndex = closestIndex;
        }
        
        if (pathIndex >= currentPath.size()) return null;
        return currentPath.get(pathIndex);
    }
    
    public static List<BlockPos> getCurrentPath() { return new ArrayList<>(currentPath); }
    public static int getPathIndex() { return pathIndex; }
    
    public static void clearPath() {
        currentPath = new ArrayList<>();
        pathTarget = null;
        pathIndex = 0;
    }
    
    public static boolean needsRecalculation(BlockPos newTarget) {
        return pathTarget == null || !pathTarget.equals(newTarget) || currentPath.isEmpty();
    }
    
    private static List<BlockPos> reconstructPath(Node node) {
        List<BlockPos> path = new ArrayList<>();
        Node current = node;
        while (current != null && path.size() < MAX_PATH_LENGTH) {
            path.add(0, current.pos);
            current = current.parent;
        }
        return path;
    }
    
    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + 
               Math.abs(a.getY() - b.getY()) + 
               Math.abs(a.getZ() - b.getZ());
    }
    
    private static double moveCost(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dy = Math.abs(from.getY() - to.getY());
        int dz = Math.abs(from.getZ() - to.getZ());
        
        if (dy > 0) return 2.0;
        if (dx > 0 && dz > 0) return 1.414;
        return 1.0;
    }
    
    private static List<BlockPos> getNeighbors(World world, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        
        int[][] horizontal = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        
        for (int[] dir : horizontal) {
            // Prevent diagonal corner cutting
            if (Math.abs(dir[0]) == 1 && Math.abs(dir[1]) == 1) {
                BlockPos card1 = pos.add(dir[0], 0, 0);
                BlockPos card2 = pos.add(0, 0, dir[1]);
                if (world.getBlockState(card1).blocksMovement() || world.getBlockState(card2).blocksMovement()) {
                    continue;
                }
            }

            BlockPos neighbor = pos.add(dir[0], 0, dir[1]);
            if (isWalkable(world, neighbor)) neighbors.add(neighbor);
            
            BlockPos stepUp = pos.add(dir[0], 1, dir[1]);
            if (isWalkable(world, stepUp) && canStepUp(world, pos)) neighbors.add(stepUp);
            
            BlockPos stepDown = pos.add(dir[0], -1, dir[1]);
            if (isWalkable(world, stepDown)) neighbors.add(stepDown);
        }
        
        return neighbors;
    }
    
    private static boolean isWalkable(World world, BlockPos pos) {
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());
        BlockState ground = world.getBlockState(pos.down());
        
        boolean feetClear = !feet.blocksMovement();
        boolean headClear = !head.blocksMovement();
        boolean groundSolid = ground.blocksMovement();
        boolean notLiquid = !feet.getFluidState().isStill();
        
        return feetClear && headClear && groundSolid && notLiquid;
    }
    
    private static boolean canStepUp(World world, BlockPos from) {
        BlockState aboveHead = world.getBlockState(from.up().up());
        return !aboveHead.blocksMovement();
    }
    
    private static class Node {
        BlockPos pos;
        Node parent;
        double gCost;
        double hCost;
        double fCost;
        
        Node(BlockPos pos, Node parent, double gCost, double hCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }
    }
}
