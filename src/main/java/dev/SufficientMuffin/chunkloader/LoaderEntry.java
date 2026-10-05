package dev.SufficientMuffin.chunkloader;

import net.minecraft.util.math.BlockPos;

/**
 * One tracked dragon-egg chunk loader.
 *
 * <p>{@code dimension} is the registry id of the world the egg lives in
 * (e.g. {@code minecraft:overworld}); {@code pos} is the egg's block position;
 * {@code radius} is the forceload radius in chunks (Chebyshev distance);
 * {@code mode} decides what the loader does about the mobs living in its radius.
 */
public final class LoaderEntry {

	/**
	 * What a loader does about the mobs inside its radius.
	 *
	 * <p>{@code CROP} keeps the area for growth simulation only: mobs in it are ordinary mobs
	 * (they despawn by vanilla rules and occupy mob-cap slots), so a crop loader never turns
	 * its contents into a permanent collection. {@code ENTITY} additionally holds them, the way
	 * a player standing there would.
	 *
	 * <p>{@code defaultRadius} is the radius a loader gets when it is created with that mode:
	 * crops spread over a field (3), machines sit in a compact box (1).
	 */
	public enum Mode {
		CROP("crop", 3),
		ENTITY("entity", 1);

		public final String id;
		public final int defaultRadius;

		Mode(String id, int defaultRadius) {
			this.id = id;
			this.defaultRadius = defaultRadius;
		}

		/** Lenient parse for the persisted file; anything unknown means crop (the default). */
		public static Mode byId(String id) {
			for (Mode m : values()) {
				if (m.id.equalsIgnoreCase(id)) {
					return m;
				}
			}
			return CROP;
		}
	}

	public final String dimension;
	public final BlockPos pos;
	public int radius;
	public Mode mode;
	public boolean mineable;

	public LoaderEntry(String dimension, BlockPos pos, int radius, Mode mode, boolean mineable) {
		this.dimension = dimension;
		this.pos = pos.toImmutable();
		this.radius = radius;
		this.mode = mode;
		this.mineable = mineable;
	}

	/** Convenience constructor — defaults to mineable (player-placed eggs are portable). */
	public LoaderEntry(String dimension, BlockPos pos, int radius, Mode mode) {
		this(dimension, pos, radius, mode, true);
	}
}
