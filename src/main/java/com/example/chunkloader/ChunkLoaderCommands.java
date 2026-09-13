package com.example.chunkloader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;

public final class ChunkLoaderCommands {
    private ChunkLoaderCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("chunkloader")
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                                .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("radius", IntegerArgumentType.integer())
                                                .executes(ChunkLoaderCommands::add)))))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                                .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(ChunkLoaderCommands::remove)))));
    }

    private static int add(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        return ChunkLoaderManager.add(world, chunkX, chunkZ, radius, source);
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
        return ChunkLoaderManager.remove(world, chunkX, chunkZ, source);
    }
}
