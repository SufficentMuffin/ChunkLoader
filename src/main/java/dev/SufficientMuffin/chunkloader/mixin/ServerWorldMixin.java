package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;

import net.minecraft.server.level.ServerLevel;

/**
 * Tracks which world is currently running its spawn cycle so that
 * {@link SpawnHelperMixin} can attribute chunk-budget bonuses to the
 * correct dimension. {@code createState} itself doesn't receive a
 * {@code World} parameter, so we capture it here — {@code tick}
 * is the per-world entry point whose chunk-source tick triggers the
 * natural spawn cycle that calls into {@code NaturalSpawner}.
 */
@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {

	@Inject(method = "tick", at = @At("HEAD"))
	private void chunkloaderTrackSpawnDimension(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		ServerLevel self = (ServerLevel) (Object) this;
		ChunkLoaderMod.MANAGER.setCurrentSpawnDimension(self);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void chunkloaderClearSpawnDimension(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		ChunkLoaderMod.MANAGER.setCurrentSpawnDimension(null);
	}
}
