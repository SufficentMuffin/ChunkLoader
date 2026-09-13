package com.example.chunkloader;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public final class ChunkLoaderManager {
    private static final Map<ChunkPos, Integer> ACTIVE_AREAS = new HashMap<>();

    private ChunkLoaderManager() {
    }

    public static int add(ServerWorld world, int chunkX, int chunkZ, int radius, ServerCommandSource source) {
        if (radius < 1) {
            source.sendError(Text.literal("Chunk loader radius must be at least 1."));
            return 0;
        }

        ChunkPos center = new ChunkPos(chunkX, chunkZ);
        int previousRadius = ACTIVE_AREAS.put(center, radius);
        if (previousRadius != 0) {
            removeArea(world, center, previousRadius);
        }

        applyArea(world, center, radius, true);
        source.sendFeedback(() -> Text.literal("Server chunk loader active at chunk (" + chunkX + ", " + chunkZ + ") radius " + radius + "."), true);
        return 1;
    }

    public static int remove(ServerWorld world, int chunkX, int chunkZ, ServerCommandSource source) {
        ChunkPos center = new ChunkPos(chunkX, chunkZ);
        Integer radius = ACTIVE_AREAS.remove(center);
        if (radius == null) {
            source.sendError(Text.literal("No active server chunk loader at chunk (" + chunkX + ", " + chunkZ + ")."));
            return 0;
        }

        removeArea(world, center, radius);
        source.sendFeedback(() -> Text.literal("Removed server chunk loader at chunk (" + chunkX + ", " + chunkZ + ")."), true);
        return 1;
    }

    private static void applyArea(ServerWorld world, ChunkPos center, int radius, boolean forced) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.setChunkForced(center.x + dx, center.z + dz, forced);
            }
        }
    }

    private static void removeArea(ServerWorld world, ChunkPos center, int radius) {
        applyArea(world, center, radius, false);
    }
}
