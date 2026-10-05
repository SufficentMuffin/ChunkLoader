package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;

import net.minecraft.server.world.ServerWorld;

/**
 * Tracks which world is currently running its spawn cycle so that
 * {@link SpawnHelperMixin} can attribute chunk-budget bonuses to the
 * correct dimension. {@code setupSpawn} itself doesn't receive a
 * {@code World} parameter, so we capture it here — {@code tickSpawners}
 * is the per-world entry point that calls into {@code SpawnHelper}.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

	@Inject(method = "tickSpawners", at = @At("HEAD"))
	private void chunkloaderTrackSpawnDimension(boolean spawnMonsters, CallbackInfo ci) {
		ServerWorld self = (ServerWorld) (Object) this;
		ChunkLoaderMod.MANAGER.setCurrentSpawnDimension(self);
	}

	@Inject(method = "tickSpawners", at = @At("RETURN"))
	private void chunkloaderClearSpawnDimension(boolean spawnMonsters, CallbackInfo ci) {
		ChunkLoaderMod.MANAGER.setCurrentSpawnDimension(null);
	}
}
