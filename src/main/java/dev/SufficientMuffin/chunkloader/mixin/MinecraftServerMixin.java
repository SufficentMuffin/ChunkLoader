package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;

import net.minecraft.server.MinecraftServer;

/**
 * Drives the chunk-loader tick loop (replaces Fabric API's
 * {@code ServerTickEvents.END_SERVER_TICK}) and lazily initialises the manager
 * on the first tick (replaces {@code ServerLifecycleEvents.SERVER_STARTED}).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

	@Unique
	private boolean chunkloaderInitialized = false;

	@Inject(method = "tick", at = @At("HEAD"))
	private void chunkloaderOnTick(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		if (!chunkloaderInitialized) {
			chunkloaderInitialized = true;
			ChunkLoaderMod.MANAGER.onServerStarted(server);
		}
		ChunkLoaderMod.MANAGER.onServerTick(server);
	}
}
