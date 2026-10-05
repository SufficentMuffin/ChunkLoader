package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;
import dev.SufficientMuffin.chunkloader.LoaderEntry;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Handles dragon egg interaction and mining.
 * <ul>
 *   <li>{@code interactBlock} — cancels the right-click teleport and detects a
 *       dragon egg being placed (optimistically claiming it as mineable).</li>
 *   <li>{@code processBlockBreakingAction} — cancels START_DESTROY_BLOCK on a dragon
 *       egg ONLY if that loader is not mineable (permanent). Mineable eggs can be
 *       broken and picked up; the teleport-on-break is suppressed separately in
 *       {@link DragonEggBlockMixin}.</li>
 * </ul>
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {

	@Shadow
	private ServerWorld world;

	@Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
	private void chunkloaderOnInteractBlock(ServerPlayerEntity player, World world, ItemStack stack,
			Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
		BlockPos clicked = hitResult.getBlockPos();
		if (world.getBlockState(clicked).isOf(Blocks.DRAGON_EGG)) {
			cir.setReturnValue(ActionResult.FAIL); // suppress right-click teleport
			return;
		}
		// Placement detection: holding a dragon egg and clicking a placeable face.
		if (stack.isOf(Items.DRAGON_EGG) && world instanceof ServerWorld serverWorld) {
			BlockPos placePos = clicked.offset(hitResult.getSide());
			BlockState target = world.getBlockState(placePos);
			if (target.isAir() || target.isReplaceable()) {
				ChunkLoaderMod.MANAGER.onPlace(serverWorld, placePos);
			}
		}
	}

	@Inject(method = "processBlockBreakingAction", at = @At("HEAD"), cancellable = true)
	private void chunkloaderOnProcessBlockBreakingAction(BlockPos pos, PlayerActionC2SPacket.Action action,
			Direction direction, int i, int j, CallbackInfo ci) {
		if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
				&& world.getBlockState(pos).isOf(Blocks.DRAGON_EGG)) {
			LoaderEntry loader = ChunkLoaderMod.MANAGER.findLoaderAt(world, pos);
			if (loader != null && !loader.mineable) {
				ci.cancel(); // permanent loader — suppress mining + teleport
			}
			// mineable loaders: don't cancel — mining proceeds, teleport handled by DragonEggBlockMixin
		}
	}
}
