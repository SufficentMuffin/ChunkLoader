package dev.SufficientMuffin.chunkloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * Entry point. No Fabric API is used — all behaviour is wired through Mixins
 * (see {@code dev.SufficientMuffin.chunkloader.mixin}). This class only hosts the shared
 * {@link LoaderManager} singleton, the logger, and the command tree.
 *
 * <p>Commands (all operator-only):
 * <pre>
 *   /chunkloader claim [crop|entity] [radius]   claim where you stand (default: crop, radius 3, permanent)
 *   /chunkloader unclaim                        release the loader you are standing in
 *   /chunkloader mode crop|entity               switch the loader you are standing in
 *   /chunkloader radius &lt;n&gt;                     change the radius of the loader you are standing in
 *   /chunkloader mineable &lt;true|false&gt;          toggle whether the loader's egg can be mined
 * </pre>
 */
public class ChunkLoaderMod implements ModInitializer {

	public static final String MOD_ID = "chunkloader";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Shared state holder; driven by the mixins. */
	public static final LoaderManager MANAGER = new LoaderManager();

	@Override
	public void onInitialize() {
		LOGGER.info("[ChunkLoader] initialized.");
	}

	/** Called from {@code CommandManagerMixin} after the dispatcher is built. */
	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> claim = Commands.literal("claim")
				.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> MANAGER.claimAt(ctx.getSource().getPlayerOrException()))
				.then(claimInMode(LoaderEntry.Mode.CROP))
				.then(claimInMode(LoaderEntry.Mode.ENTITY));

		LiteralArgumentBuilder<CommandSourceStack> mode = Commands.literal("mode");
		for (LoaderEntry.Mode m : LoaderEntry.Mode.values()) {
			mode = mode.then(Commands.literal(m.id)
					.executes(ctx -> MANAGER.setMode(ctx.getSource().getPlayerOrException(), m)));
		}

		dispatcher.register(Commands.literal("chunkloader")
				.then(claim)
				.then(mode)
				.then(Commands.literal("unclaim")
						.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.executes(ctx -> MANAGER.unclaimAt(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("radius")
						.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, LoaderManager.MAX_RADIUS))
								.executes(ctx -> MANAGER.setRadius(
										ctx.getSource().getPlayerOrException(),
										IntegerArgumentType.getInteger(ctx, "radius")))))
				.then(Commands.literal("mineable")
						.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("mineable", BoolArgumentType.bool())
								.executes(ctx -> MANAGER.setMineable(
										ctx.getSource().getPlayerOrException(),
										BoolArgumentType.getBool(ctx, "mineable"))))));
	}

	/** {@code claim <mode>} uses the mode's default radius; {@code claim <mode> <radius>} overrides it. */
	private static LiteralArgumentBuilder<CommandSourceStack> claimInMode(LoaderEntry.Mode mode) {
		return Commands.literal(mode.id)
				.executes(ctx -> MANAGER.claimAt(ctx.getSource().getPlayerOrException(), mode))
				.then(Commands.argument("radius", IntegerArgumentType.integer(0, LoaderManager.MAX_RADIUS))
						.executes(ctx -> MANAGER.claimAt(
								ctx.getSource().getPlayerOrException(), mode,
								IntegerArgumentType.getInteger(ctx, "radius"))));
	}
}
