package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;

import net.minecraft.world.SpawnHelper;

/**
 * Gives the loaders' chunks a share of the world's hostile-spawn budget.
 *
 * <p>Vanilla's cap test is {@code SpawnHelper.Info.isBelowCap}: a spawn is allowed while the
 * group's count is below {@code capacity * spawningChunkCount / 289}, where {@code 289} is the
 * number of chunks in one player's 17x17 bubble and {@code spawningChunkCount} is how many
 * chunks currently lie near players. A forceloaded chunk is not near any player, so a loader
 * adds nothing to that number.
 *
 * <p>Since the exemption from the count is gone for {@code crop}-mode loaders, the mobs living
 * in them do consume cap slots, and without this they consume slots that belong to the rest of
 * the world — the loader would be borrowing every player's budget. Adding the loaders' chunk
 * counts here makes each loader pay for its own contents, exactly as the chunks around a real
 * player do.
 *
 * <p>{@code SpawnHelper.setupSpawn} is called once per world per tick with that world's count,
 * immediately before the count is used to build the spawn info — it is the only place this
 * number enters the cap.
 */
@Mixin(SpawnHelper.class)
public abstract class SpawnHelperMixin {

	@ModifyVariable(method = "setupSpawn", at = @At("HEAD"), argsOnly = true)
	private static int chunkloaderAddLoaderChunkBudget(int spawningChunkCount) {
		return spawningChunkCount + ChunkLoaderMod.MANAGER.getCapBonusChunks();
	}
}
