package com.example.chunkloader.block;

import com.example.chunkloader.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class ChunkLoaderBlockEntity extends BlockEntity {

    // Preset radii (in chunks) the player cycles through with sneak + right-click.
    private static final int[] RADIUS_OPTIONS = {1, 2, 3, 4, 6, 8};

    // How many random-tick "rolls" to simulate per loaded chunk each cycle.
    // Vanilla's default random_tick_speed gamerule is 3 - this mirrors that.
    private static final int RANDOM_TICKS_PER_CHUNK = 3;

    // Run the simulation once per second (20 game ticks) instead of every
    // single tick, to keep this cheap even with a large radius.
    private static final int SIMULATION_INTERVAL_TICKS = 20;

    private int radiusIndex = 0;
    private int tickCounter = 0;

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_LOADER_BE, pos, state);
    }

    public int getRadius() {
        return RADIUS_OPTIONS[radiusIndex];
    }

    /** Un-forces the old radius, advances to the next preset, forces the new radius. */
    public void cycleRadius(ServerWorld world) {
        applyForceLoad(world, false);
        radiusIndex = (radiusIndex + 1) % RADIUS_OPTIONS.length;
        applyForceLoad(world, true);
        markDirty();
    }

    /** Adds or removes forceload tickets for every chunk within the current radius. */
    public void applyForceLoad(ServerWorld world, boolean forced) {
        ChunkPos center = new ChunkPos(getPos());
        int r = getRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                world.setChunkForced(center.x + dx, center.z + dz, forced);
            }
        }
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, ChunkLoaderBlockEntity be) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        be.tickCounter++;
        if (be.tickCounter < SIMULATION_INTERVAL_TICKS) return;
        be.tickCounter = 0;

        be.simulateRandomTicks(serverWorld);
    }

    /**
     * Manually reproduces vanilla's random-tick behaviour: pick random block
     * positions inside each force-loaded chunk and call randomTick on them
     * if the block is one that reacts to random ticks (crops, cactus,
     * sugar cane, saplings, leaves, ice, etc).
     */
    private void simulateRandomTicks(ServerWorld world) {
        ChunkPos center = new ChunkPos(getPos());
        int r = getRadius();
        Random random = world.getRandom();

        int minY = world.getBottomY();
        int height = world.getHeight();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;

                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;

                for (int i = 0; i < RANDOM_TICKS_PER_CHUNK; i++) {
                    int x = (chunkX << 4) + random.nextInt(16);
                    int z = (chunkZ << 4) + random.nextInt(16);
                    int y = minY + random.nextInt(height);
                    BlockPos randomPos = new BlockPos(x, y, z);

                    BlockState targetState = world.getBlockState(randomPos);
                    if (targetState.hasRandomTicks()) {
                        targetState.randomTick(world, randomPos, random);
                    }
                }
            }
        }
    }

    /**
     * Called right before this block is actually replaced/removed - unlike
     * ChunkLoaderBlock#onStateReplaced (which now runs afterward, once the
     * block entity is already gone), this is the correct place to release
     * the forceload tickets while we can still reach world/pos cleanly.
     */
    @Override
    public void onBlockReplaced(BlockPos pos, BlockState oldState) {
        if (getWorld() instanceof ServerWorld serverWorld) {
            applyForceLoad(serverWorld, false);
        }
        super.onBlockReplaced(pos, oldState);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putInt("RadiusIndex", radiusIndex);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        radiusIndex = view.getInt("RadiusIndex", 0);
    }
}
