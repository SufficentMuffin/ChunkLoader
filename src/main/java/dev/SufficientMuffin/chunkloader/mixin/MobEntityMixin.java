package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Keeps mobs alive inside a loader's forceload radius, by making the loader area answer
 * "yes" to vanilla's "may this mob stay?" question. This is the mod's stand-in for a
 * player being present.
 *
 * <p>Vanilla's mob lifecycle is defined relative to players: a mob further than its spawn
 * group's immediate despawn range (128 blocks for monsters) from the nearest player is
 * discarded outright, and the mob cap is scaled by how many chunks lie near players. With
 * nobody around, vanilla therefore has no mobs at all — which is what made an unattended
 * spawner look broken.
 *
 * <p>The switch vanilla itself uses for "this mob is exempt from both of those rules" is
 * {@code Mob.requiresCustomPersistence()}, not {@code canImmediatelyDespawn}. {@code checkDespawn}
 * returns early while it is true (after the Peaceful-difficulty cleanup, which therefore
 * still runs), and {@code NaturalSpawner}'s per-tick count loop skips the mob because it tests
 * {@code isPersistent() || requiresCustomPersistence()}. So answering true here means an exempt mob
 * neither despawns nor occupies a slot in the global mob cap.
 *
 * <p>{@code canImmediatelyDespawn} only gates the discard itself: a mob exempted there
 * still got counted against the cap, so an unbounded exemption used to park the whole world
 * at its cap. That is the bug this replaced; the count is also why holding mobs is now a
 * per-loader mode ({@code entity}) instead of a rule every loader followed.
 *
 * <p>Because this is a live predicate and not saved state, nothing is written to disk, no
 * mob is turned into a pet, and the mob becomes an ordinary mob again the moment it leaves
 * every loader radius. The only behaviour reached beyond despawn handling is a warden
 * resetting its dig cooldown while inside a radius.
 *
 * <p>Only {@code entity}-mode loaders answer yes here. A {@code crop}-mode loader (the default
 * for placements and {@code /chunkloader claim}) leaves this method alone, so the mobs in its
 * radius stay ordinary mobs: they despawn by the rules above, they occupy mob-cap slots, and
 * they are discarded outright the moment a player is online further than 128 blocks away. That
 * is why the holding behaviour is a mode and not a permanent deletion.
 */
@Mixin(Mob.class)
public abstract class MobEntityMixin {

	@Inject(method = "requiresCustomPersistence", at = @At("HEAD"), cancellable = true)
	private void chunkloaderKeepMobsInLoader(CallbackInfoReturnable<Boolean> cir) {
		Mob self = (Mob) (Object) this;
		Level world = self.level();
		if (world.isClientSide()) {
			return;
		}
		if (ChunkLoaderMod.MANAGER.isInsideEntityModeLoader(world, self.blockPosition())) {
			// Inside an entity-mode loader -> like a mob near a player: never despawned, never counted.
			cir.setReturnValue(true);
		}
	}
}
