package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Registers the {@code /chunkloader} command (replaces Fabric API's
 * {@code CommandRegistrationCallback}) by hooking the {@code CommandManager}
 * constructor after the dispatcher is built.
 */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {

	@Inject(method = "<init>", at = @At("TAIL"))
	private void chunkloaderRegisterCommands(CommandManager.RegistrationEnvironment environment,
			CommandRegistryAccess registryAccess, CallbackInfo ci) {
		CommandDispatcher<ServerCommandSource> dispatcher = ((CommandManager) (Object) this).getDispatcher();
		ChunkLoaderMod.registerCommands(dispatcher);
	}
}
