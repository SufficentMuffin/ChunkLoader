package dev.SufficientMuffin.chunkloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Tracks dragon-egg chunk loaders, drives forceloading + growth simulation,
 * and persists the list to a plain-text file ({@code claimed_locations.txt})
 * inside the world save folder.
 *
 * <p>Design notes:
 * <ul>
 *   <li>No custom registries — the vanilla {@code minecraft:dragon_egg} block is reused.</li>
 *   <li>A loader is claimed optimistically when a player places an egg (detected via
 *       {@code UseBlockCallback}); the per-second tick verifies the egg still exists and
 *       self-heals any false positive, and unclaims on any destruction.</li>
 *   <li>Growth is simulated by invoking {@link BlockState#randomTick} directly — the same
 *       method vanilla's own engine calls — so every crop follows its real, unmodified
 *       growth mechanic and we can never exceed legitimate vanilla growth rates.</li>
 *   <li>We deliberately skip chunks that are already being random-ticked by vanilla
 *       (those within any online player's simulation distance) to avoid double-ticking.</li>
 *   <li>A loader has a mode: {@code crop} (default) leaves the mobs in its radius alone —
 *       they despawn and count against the world's mob cap like any other mob — while
 *       {@code entity} additionally holds them (see MobEntityMixin). Crop loaders pay their
 *       chunks into the world's spawn budget via SpawnHelperMixin.</li>
 * </ul>
 */
public final class LoaderManager {

	static final Text CHUNK_LOADER_NAME = Text.literal("Chunk Loader");

	static final int MAX_RADIUS = 8;
	private static final int TICKS_PER_SECOND = 20;

	private MinecraftServer server;
	/** dimension id -> (block pos -> loader) */
	private final Map<String, Map<BlockPos, LoaderEntry>> loaders = new HashMap<>();
	private int tickCounter = 0;
	/** Cached set of chunks already random-ticked by vanilla (near online players).
	 *  Rebuilt once per second in {@link #tickOncePerSecond()} and reused across
	 *  all 20 growth ticks in that second — player positions barely move within 1s. */
	private Set<Long> cachedTickingChunks = Collections.emptySet();

	/** The world currently running its spawn cycle, set by {@code ServerWorldMixin}
	 *  around {@code tickSpawners}. Read by {@code SpawnHelperMixin} to attribute
	 *  chunk-budget bonuses to the correct dimension. Null outside a spawn cycle. */
	private ServerWorld currentSpawnDimension = null;

	/** Called from {@code ServerWorldMixin} — sets the dimension {@code setupSpawn}
	 *  is about to run for, or null to clear it after the cycle. */
	public void setCurrentSpawnDimension(ServerWorld world) {
		this.currentSpawnDimension = world;
	}

	// ------------------------------------------------------------------ lifecycle

	public void onServerStarted(MinecraftServer server) {
		this.server = server;
		this.loaders.clear();
		loadFromDisk();
		ChunkLoaderMod.LOGGER.info("[ChunkLoader] loaded {} tracked loader(s)", count());
	}

	public void onServerTick(MinecraftServer server) {
		// Growth simulation is smoothed across every tick (vanilla's own cadence) so
		// large radii don't cause a per-second stutter.
		simulateGrowthTick();
		if (++tickCounter < TICKS_PER_SECOND) {
			return;
		}
		tickCounter = 0;
		cachedTickingChunks = collectAlreadyTickingChunks();
		tickOncePerSecond();
	}

	private void tickOncePerSecond() {
		if (server == null) {
			return;
		}
		for (Map.Entry<String, Map<BlockPos, LoaderEntry>> dimEntry : loaders.entrySet()) {
			ServerWorld world = resolveWorld(dimEntry.getKey());
			if (world == null) {
				continue;
			}
			List<LoaderEntry> toUnclaim = new ArrayList<>();
			for (LoaderEntry entry : dimEntry.getValue().values()) {
				if (!world.getBlockState(entry.pos).isOf(Blocks.DRAGON_EGG)) {
					// Egg is gone (mined, exploded, pushed, …) -> release.
					toUnclaim.add(entry);
					continue;
				}
				setForced(world, entry, true);               // (re-)assert forceload
				spawnAmbientParticles(world, entry);
			}
			for (LoaderEntry entry : toUnclaim) {
				unclaim(world, entry);
			}
		}
	}

	/** Chunks already being random-ticked by vanilla (near online players) — we must not double-tick them. */
	private Set<Long> collectAlreadyTickingChunks() {
		Set<Long> set = new HashSet<>();
		int simDist = server.getPlayerManager().getSimulationDistance();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			int cx = p.getBlockPos().getX() >> 4;
			int cz = p.getBlockPos().getZ() >> 4;
			for (int dx = -simDist; dx <= simDist; dx++) {
				for (int dz = -simDist; dz <= simDist; dz++) {
					set.add(pack(cx + dx, cz + dz));
				}
			}
		}
		return set;
	}

	// ------------------------------------------------------------------ growth

	/** Runs every game tick; distributes vanilla-density random ticks across the second. */
	private void simulateGrowthTick() {
		if (server == null) {
			return;
		}
		Set<Long> alreadyTicking = cachedTickingChunks;
		for (Map.Entry<String, Map<BlockPos, LoaderEntry>> dimEntry : loaders.entrySet()) {
			ServerWorld world = resolveWorld(dimEntry.getKey());
			if (world == null) {
				continue;
			}
			for (LoaderEntry entry : dimEntry.getValue().values()) {
				if (!world.getBlockState(entry.pos).isOf(Blocks.DRAGON_EGG)) {
					continue; // absence is handled by the per-second verification
				}
				simulateColumnTicks(world, entry, alreadyTicking);
			}
		}
	}

	private int simulateColumnTicks(ServerWorld world, LoaderEntry entry, Set<Long> alreadyTicking) {
		int cx = entry.pos.getX() >> 4;
		int cz = entry.pos.getZ() >> 4;
		int height = world.getHeight();
		int bottomY = world.getBottomY();
		int subChunks = Math.max(1, height / 16);
		// Vanilla performs 3 random ticks per sub-chunk per game tick — the exact
		// density we reproduce, so growth is authentic and never exceeds vanilla.
		int perColumnPerTick = 3 * subChunks;
		Random random = world.getRandom();
		int count = 0;
		for (int dx = -entry.radius; dx <= entry.radius; dx++) {
			for (int dz = -entry.radius; dz <= entry.radius; dz++) {
				if (alreadyTicking.contains(pack(cx + dx, cz + dz))) {
					continue; // vanilla already random-ticks this chunk
				}
				for (int i = 0; i < perColumnPerTick; i++) {
					int bx = (cx + dx) * 16 + random.nextInt(16);
					int bz = (cz + dz) * 16 + random.nextInt(16);
					int by = bottomY + random.nextInt(height);
					BlockPos bp = new BlockPos(bx, by, bz);
					BlockState state = world.getBlockState(bp);
					state.randomTick(world, bp, random);
					count++;
				}
			}
		}
		return count;
	}

	private void spawnAmbientParticles(ServerWorld world, LoaderEntry entry) {
		world.spawnParticles(ParticleTypes.COMPOSTER,
				entry.pos.getX() + 0.5, entry.pos.getY() + 1.0, entry.pos.getZ() + 0.5,
				8, 0.375, 0.4375, 0.3125, 0.004);
	}

	// ------------------------------------------------------------------ claim / unclaim

	/** Called from {@code UseBlockCallback} when a dragon egg is about to be placed (crop mode, mineable). */
	public void onPlace(ServerWorld world, BlockPos pos) {
		onPlace(world, pos, LoaderEntry.Mode.CROP);
	}

	/** Same, for a loader claimed in an explicit mode (mineable — player-placed). */
	public void onPlace(ServerWorld world, BlockPos pos, LoaderEntry.Mode mode) {
		String dim = world.getRegistryKey().getValue().toString();
		BlockPos imm = pos.toImmutable();
		LoaderEntry entry = new LoaderEntry(dim, imm, mode.defaultRadius, mode, true);
		loaders.computeIfAbsent(dim, k -> new LinkedHashMap<>()).put(imm, entry);
		setForced(world, entry, true);
		world.spawnParticles(ParticleTypes.COMPOSTER,
				imm.getX() + 0.5, imm.getY() + 1.0625, imm.getZ() + 0.5625,
				24, 0.46875, 0.53125, 0.40625, 0.012);
		save();
		ChunkLoaderMod.LOGGER.info("[ChunkLoader] claimed {} at {} ({}, radius {})", dim, imm, mode.id, entry.radius);
	}

	/** {@code /chunkloader claim} — claims the player's current position as a crop loader and places a dragon egg there. */
	public int claimAt(ServerPlayerEntity player) {
		return claimAt(player, LoaderEntry.Mode.CROP);
	}

	/** {@code /chunkloader claim <crop|entity>} — claims in that mode, using the mode's default radius. */
	public int claimAt(ServerPlayerEntity player, LoaderEntry.Mode mode) {
		return claimAt(player, mode, mode.defaultRadius);
	}

	/** {@code /chunkloader claim <crop|entity> <radius>} — claims in that mode with an explicit radius. */
	public int claimAt(ServerPlayerEntity player, LoaderEntry.Mode mode, int radius) {
		radius = clampRadius(radius);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		BlockPos playerPos = player.getBlockPos();
		// Place the egg at the player's feet; if that slot is occupied, place it one block above.
		BlockPos eggPos = world.getBlockState(playerPos).isReplaceable() ? playerPos : playerPos.up();
		world.setBlockState(eggPos, Blocks.DRAGON_EGG.getDefaultState());
		onPlace(world, eggPos, mode);
		LoaderEntry entry = loaders.get(world.getRegistryKey().getValue().toString()).get(eggPos.toImmutable());
		if (entry != null) {
			entry.mineable = false;  // op-claimed loaders are permanent (unmineable)
			if (entry.radius != radius) {
				setForced(world, entry, false);
				entry.radius = radius;
				setForced(world, entry, true);
			}
			save();
		}
		player.sendMessage(Text.literal("Claimed " + mode.id + " chunk loader at "
				+ eggPos.getX() + ", " + eggPos.getY() + ", " + eggPos.getZ()
				+ " (radius " + radius + ")."), false);
		return 1;
	}

	/** {@code /chunkloader unclaim} — releases the loader whose radius contains the player and removes its egg. */
	public int unclaimAt(ServerPlayerEntity player) {
		LoaderEntry best = loaderNearPlayer(player);
		if (best == null) {
			player.sendMessage(Text.literal("You are not standing inside any chunk loader's radius."), false);
			return 0;
		}
		ServerWorld world = resolveWorld(best.dimension);
		if (world == null) {
			player.sendMessage(Text.literal("That loader's dimension is not loaded."), false);
			return 0;
		}
		// Remove the egg block if it's still there.
		if (world.getBlockState(best.pos).isOf(Blocks.DRAGON_EGG)) {
			world.setBlockState(best.pos, Blocks.AIR.getDefaultState());
		}
		unclaim(world, best);
		player.sendMessage(Text.literal("Released chunk loader at "
				+ best.pos.getX() + ", " + best.pos.getY() + ", " + best.pos.getZ() + "."), false);
		return 1;
	}

	private void unclaim(ServerWorld world, LoaderEntry entry) {
		setForced(world, entry, false);
		Map<BlockPos, LoaderEntry> byDim = loaders.get(entry.dimension);
		if (byDim != null) {
			byDim.remove(entry.pos);
			if (byDim.isEmpty()) {
				loaders.remove(entry.dimension);
			}
		}
		save();
		ChunkLoaderMod.LOGGER.info("[ChunkLoader] released {} at {}", entry.dimension, entry.pos);
	}

	boolean isTracked(ServerWorld world, BlockPos pos) {
		Map<BlockPos, LoaderEntry> byDim = loaders.get(world.getRegistryKey().getValue().toString());
		return byDim != null && byDim.containsKey(pos.toImmutable());
	}

	/** The loader whose radius contains {@code pos} (nearest one wins where radii overlap), or {@code null}. */
	private LoaderEntry loaderAt(World world, BlockPos pos) {
		if (server == null) {
			return null;
		}
		Map<BlockPos, LoaderEntry> byDim = loaders.get(world.getRegistryKey().getValue().toString());
		if (byDim == null) {
			return null;
		}
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		LoaderEntry best = null;
		int bestDist = Integer.MAX_VALUE;
		for (LoaderEntry e : byDim.values()) {
			int dist = Math.max(Math.abs(cx - (e.pos.getX() >> 4)), Math.abs(cz - (e.pos.getZ() >> 4)));
			if (dist <= e.radius && dist < bestDist) {
				best = e;
				bestDist = dist;
			}
		}
		return best;
	}

	/** The loader whose radius contains the player (nearest one wins where radii overlap), or {@code null}.
	 *  Only searches the dimension the player is currently in. */
	private LoaderEntry loaderNearPlayer(ServerPlayerEntity player) {
		int cx = player.getBlockPos().getX() >> 4;
		int cz = player.getBlockPos().getZ() >> 4;
		Map<BlockPos, LoaderEntry> byDim = loaders.get(player.getEntityWorld().getRegistryKey().getValue().toString());
		if (byDim == null) {
			return null;
		}
		LoaderEntry best = null;
		int bestDist = Integer.MAX_VALUE;
		for (LoaderEntry e : byDim.values()) {
			int dist = Math.max(Math.abs(cx - (e.pos.getX() >> 4)), Math.abs(cz - (e.pos.getZ() >> 4)));
			if (dist <= e.radius && dist < bestDist) {
				best = e;
				bestDist = dist;
			}
		}
		return best;
	}

	/** True if the position lies inside any loader's forceload radius (exempts spawners from the player-proximity check). */
	public boolean isInsideAnyLoader(World world, BlockPos pos) {
		return loaderAt(world, pos) != null;
	}

	/**
	 * True only inside an {@code entity}-mode loader's radius — the loaders that hold the mobs
	 * living in them. A {@code crop}-mode loader answers false, so its mobs stay ordinary.
	 */
	public boolean isInsideEntityModeLoader(World world, BlockPos pos) {
		LoaderEntry e = loaderAt(world, pos);
		return e != null && e.mode == LoaderEntry.Mode.ENTITY;
	}

	/**
	 * The chunks the loaders contribute to the given world's hostile-spawn budget, in the unit vanilla
	 * uses for it: {@code isBelowCap} is {@code count < capacity * chunks / 289}, with 289 being
	 * one player's 17x17 bubble of spawn-eligible chunks.
	 *
	 * <p>Crop-mode loaders pay in, because their mobs count against the cap again; entity-mode mobs
	 * are exempt from that count, so those loaders would only be handing out free slots if they did.
	 * <p>Only loaders in the world currently running its spawn cycle are counted —
	 * {@code setupSpawn} is called per world, so the bonus must be per-world too.
	 * The current dimension is tracked via {@code ServerWorldMixin} around
	 * {@code tickSpawners}.
	 */
	public int getCapBonusChunks() {
		if (currentSpawnDimension == null) {
			return 0;
		}
		String dim = currentSpawnDimension.getRegistryKey().getValue().toString();
		Map<BlockPos, LoaderEntry> byDim = loaders.get(dim);
		if (byDim == null) {
			return 0;
		}
		int chunks = 0;
		for (LoaderEntry e : byDim.values()) {
			if (e.mode == LoaderEntry.Mode.CROP) {
				int side = 2 * e.radius + 1;
				chunks += side * side;
			}
		}
		return chunks;
	}

	/** {@code /chunkloader radius <n>} — the loader whose forceload radius contains the player. */
	public int setRadius(ServerPlayerEntity player, int radius) {
		radius = clampRadius(radius);
		LoaderEntry best = loaderNearPlayer(player);
		if (best == null) {
			player.sendMessage(Text.literal("You are not standing inside any chunk loader's radius."), false);
			return 0;
		}
		ServerWorld world = resolveWorld(best.dimension);
		if (world == null) {
			player.sendMessage(Text.literal("That loader's dimension is not loaded."), false);
			return 0;
		}
		setForced(world, best, false);
		best.radius = radius;
		setForced(world, best, true);
		save();
		player.sendMessage(Text.literal("Updated chunk loader at "
				+ best.pos.getX() + ", " + best.pos.getY() + ", " + best.pos.getZ()
				+ " (" + best.dimension + ", " + best.mode.id + " mode) to radius " + radius + "."), false);
		return 1;
	}

	/** {@code /chunkloader mode <crop|entity>} — switches the loader whose radius contains the player.
	 *  Available to all players, but permanent (non-mineable) loaders can only be changed by ops. */
	public int setMode(ServerPlayerEntity player, LoaderEntry.Mode mode) {
		LoaderEntry best = loaderNearPlayer(player);
		if (best == null) {
			player.sendMessage(Text.literal("You are not standing inside any chunk loader's radius."), false);
			return 0;
		}
		if (!best.mineable && !player.hasPermissionLevel(2)) {
			player.sendMessage(Text.literal("That chunk loader is permanent — only operators can change its mode."), false);
			return 0;
		}
		if (best.mode == mode) {
			player.sendMessage(Text.literal("That chunk loader is already in " + mode.id + " mode."), false);
			return 1;
		}
		best.mode = mode;
		save();
		player.sendMessage(Text.literal("Chunk loader at "
				+ best.pos.getX() + ", " + best.pos.getY() + ", " + best.pos.getZ()
				+ " (" + best.dimension + ") is now in " + mode.id + " mode."), false);
		return 1;
	}

	/** {@code /chunkloader mineable <true|false>} — toggles whether the loader's egg can be mined. */
	public int setMineable(ServerPlayerEntity player, boolean mineable) {
		LoaderEntry best = loaderNearPlayer(player);
		if (best == null) {
			player.sendMessage(Text.literal("You are not standing inside any chunk loader's radius."), false);
			return 0;
		}
		best.mineable = mineable;
		save();
		player.sendMessage(Text.literal("Chunk loader at "
				+ best.pos.getX() + ", " + best.pos.getY() + ", " + best.pos.getZ()
				+ " (" + best.dimension + ") is now " + (mineable ? "mineable" : "permanent") + "."), false);
		return 1;
	}

	/** Returns the loader whose egg is at the given position, or {@code null} if not a loader egg. */
	public LoaderEntry findLoaderAt(ServerWorld world, BlockPos pos) {
		Map<BlockPos, LoaderEntry> byDim = loaders.get(world.getRegistryKey().getValue().toString());
		if (byDim == null) {
			return null;
		}
		return byDim.get(pos.toImmutable());
	}

	private static int clampRadius(int radius) {
		return Math.min(Math.max(radius, 0), MAX_RADIUS);
	}

	private void setForced(ServerWorld world, LoaderEntry entry, boolean forced) {
		int cx = entry.pos.getX() >> 4;
		int cz = entry.pos.getZ() >> 4;
		for (int dx = -entry.radius; dx <= entry.radius; dx++) {
			for (int dz = -entry.radius; dz <= entry.radius; dz++) {
				world.setChunkForced(cx + dx, cz + dz, forced);
			}
		}
	}

	private ServerWorld resolveWorld(String dim) {
		RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dim));
		return server.getWorld(key);
	}

	private int count() {
		int c = 0;
		for (Map<BlockPos, LoaderEntry> byDim : loaders.values()) {
			c += byDim.size();
		}
		return c;
	}

	private static long pack(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	// ------------------------------------------------------------------ persistence

	private Path filePath() {
		return server.getSavePath(WorldSavePath.ROOT).resolve("claimed_locations.txt");
	}

	private void loadFromDisk() {
		try {
			Path f = filePath();
			if (!Files.exists(f)) {
				return;
			}
			for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String line = raw.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split(",");
				try {
					String dim;
					int x, y, z, radius;
					LoaderEntry.Mode mode;
					boolean mineable;
					if (parts.length == 7) {
						dim = parts[0];
						x = Integer.parseInt(parts[1]);
						y = Integer.parseInt(parts[2]);
						z = Integer.parseInt(parts[3]);
						radius = Integer.parseInt(parts[4]);
						mode = LoaderEntry.Mode.byId(parts[5]);
						mineable = Boolean.parseBoolean(parts[6]);
					} else if (parts.length == 6) {
						// 6-field line (dimension,x,y,z,radius,mode) -> mineable (player-placed default).
						dim = parts[0];
						x = Integer.parseInt(parts[1]);
						y = Integer.parseInt(parts[2]);
						z = Integer.parseInt(parts[3]);
						radius = Integer.parseInt(parts[4]);
						mode = LoaderEntry.Mode.byId(parts[5]);
						mineable = true;
					} else if (parts.length == 5) {
						// 5-field line (dimension,x,y,z,radius) -> crop mode, mineable.
						dim = parts[0];
						x = Integer.parseInt(parts[1]);
						y = Integer.parseInt(parts[2]);
						z = Integer.parseInt(parts[3]);
						radius = Integer.parseInt(parts[4]);
						mode = LoaderEntry.Mode.CROP;
						mineable = true;
					} else if (parts.length == 4) {
						// Legacy 4-field line (x,y,z,radius) -> assume overworld, crop mode, mineable.
						dim = "minecraft:overworld";
						x = Integer.parseInt(parts[0]);
						y = Integer.parseInt(parts[1]);
						z = Integer.parseInt(parts[2]);
						radius = Integer.parseInt(parts[3]);
						mode = LoaderEntry.Mode.CROP;
						mineable = true;
					} else {
						continue;
					}
					radius = clampRadius(radius);
					loaders.computeIfAbsent(dim, k -> new LinkedHashMap<>())
							.put(new BlockPos(x, y, z).toImmutable(), new LoaderEntry(dim, new BlockPos(x, y, z), radius, mode, mineable));
				} catch (NumberFormatException ignored) {
					// skip malformed line
				}
			}
		} catch (IOException e) {
			ChunkLoaderMod.LOGGER.error("[ChunkLoader] Failed to read claimed_locations.txt", e);
		}
	}

	private void save() {
		List<String> lines = new ArrayList<>();
		lines.add("# Chunk Loader locations — one loader per line: dimension,x,y,z,radius,mode,mineable");
		for (Map<BlockPos, LoaderEntry> byDim : loaders.values()) {
			for (LoaderEntry e : byDim.values()) {
				lines.add(e.dimension + "," + e.pos.getX() + "," + e.pos.getY() + "," + e.pos.getZ()
						+ "," + e.radius + "," + e.mode.id + "," + e.mineable);
			}
		}
		try {
			Files.createDirectories(filePath().getParent());
			Files.write(filePath(), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			ChunkLoaderMod.LOGGER.error("[ChunkLoader] Failed to write claimed_locations.txt", e);
		}
	}
}
