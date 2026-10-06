package dev.SufficientMuffin.chunkloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.SufficientMuffin.chunkloader.ChunkLoaderMod;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

/**
 * Registers the {@code /chunkloader} command (replaces Fabric API's
 * {@code CommandRegistrationCallback}) by hooking the {@code Commands}
 * constructor after the dispatcher is built.
 */
@Mixin(Commands.class)
public abstract class CommandManagerMixin {

	@Inject(method = "<init>", at = @At("TAIL"))
	private void chunkloaderRegisterCommands(Commands.CommandSelection commandSelection,
			CommandBuildContext registryAccess, CallbackInfo ci) {
		CommandDispatcher<CommandSourceStack> dispatcher = ((Commands) (Object) this).getDispatcher();
		ChunkLoaderMod.registerCommands(dispatcher);
	}
}
