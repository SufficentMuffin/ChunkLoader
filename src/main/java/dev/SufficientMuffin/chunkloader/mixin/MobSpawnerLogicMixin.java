package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;

/**
 * Makes mob spawners inside a loader's forceload radius spawn even with no
 * player nearby. The loader already keeps the chunk loaded (so the spawner
 * ticks); this lifts the vanilla "player within range" requirement for spawners
 * that sit inside a loader radius. Every other check the spawner makes (spawn
 * area has room, maxNearbyEntities, spawnCount, difficulty, spawn restriction)
 * is untouched, and the global mob cap never gated a spawner in the first place.
 */
@Mixin(BaseSpawner.class)
public abstract class MobSpawnerLogicMixin {

	@Inject(method = "isNearPlayer", at = @At("HEAD"), cancellable = true)
	private void chunkloaderIgnorePlayerRange(Level world, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (ChunkLoaderMod.MANAGER.isInsideAnyLoader(world, pos)) {
			cir.setReturnValue(true); // pretend a player is nearby
		}
	}
}
