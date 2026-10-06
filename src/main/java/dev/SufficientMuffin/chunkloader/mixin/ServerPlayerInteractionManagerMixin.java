package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;
import dev.SufficientMuffin.chunkloader.LoaderEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Handles dragon egg interaction and mining.
 * <ul>
 *   <li>{@code useItemOn} — cancels the right-click teleport and detects a
 *       dragon egg being placed (optimistically claiming it as mineable).</li>
 *   <li>{@code handleBlockBreakAction} — cancels START_DESTROY_BLOCK on a dragon
 *       egg ONLY if that loader is not mineable (permanent). Mineable eggs can be
 *       broken and picked up; the teleport-on-break is suppressed separately in
 *       {@link DragonEggBlockMixin}.</li>
 * </ul>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerInteractionManagerMixin {

	@Shadow
	private ServerLevel level;

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void chunkloaderOnInteractBlock(ServerPlayer player, Level world, ItemStack stack,
			InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		BlockPos clicked = hitResult.getBlockPos();
		if (world.getBlockState(clicked).is(Blocks.DRAGON_EGG)) {
			cir.setReturnValue(InteractionResult.FAIL); // suppress right-click teleport
			return;
		}
		// Placement detection: holding a dragon egg and clicking a placeable face.
		if (stack.is(Items.DRAGON_EGG) && world instanceof ServerLevel serverLevel) {
			BlockPos placePos = clicked.relative(hitResult.getDirection());
			BlockState target = world.getBlockState(placePos);
			if (target.isAir() || target.canBeReplaced()) {
				ChunkLoaderMod.MANAGER.onPlace(serverLevel, placePos);
			}
		}
	}

	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
	private void chunkloaderOnHandleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action,
			Direction direction, int i, int j, CallbackInfo ci) {
		if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
				&& level.getBlockState(pos).is(Blocks.DRAGON_EGG)) {
			LoaderEntry loader = ChunkLoaderMod.MANAGER.findLoaderAt(level, pos);
			if (loader != null && !loader.mineable) {
				ci.cancel(); // permanent loader — suppress mining + teleport
			}
			// mineable loaders: don't cancel — mining proceeds, teleport handled by DragonEggBlockMixin
		}
	}
}
