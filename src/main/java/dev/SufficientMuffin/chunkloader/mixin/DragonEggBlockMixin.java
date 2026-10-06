package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DragonEggBlock;

/**
 * Neutralises the dragon egg's left-click teleport. Vanilla teleports the egg in
 * {@code attack} (when the player starts mining it). We cancel that
 * method so mineable loader eggs can be mined and drop normally, without
 * teleporting away. This applies to ALL dragon eggs — non-loader eggs are
 * unaffected because vanilla's teleport is a cosmetic novelty for those.
 */
@Mixin(DragonEggBlock.class)
public abstract class DragonEggBlockMixin {

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void chunkloaderPreventTeleport(BlockState state, Level world, BlockPos pos,
			Player player, CallbackInfo ci) {
		// Only suppress teleport for tracked loader eggs — vanilla eggs keep their behavior.
		if (world instanceof net.minecraft.server.level.ServerLevel serverWorld
				&& dev.SufficientMuffin.chunkloader.ChunkLoaderMod.MANAGER.findLoaderAt(serverWorld, pos) != null) {
			ci.cancel(); // suppress left-click teleport; mining proceeds normally
		}
	}
}
