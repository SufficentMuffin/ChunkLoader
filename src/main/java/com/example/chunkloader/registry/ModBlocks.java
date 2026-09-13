package com.example.chunkloader.registry;

import com.example.chunkloader.block.ChunkLoaderBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModBlocks {

    private static final Identifier ID = Identifier.of("chunkloader", "chunk_loader");
    private static final RegistryKey<Block> BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, ID);
    private static final RegistryKey<Item> ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, ID);

    public static final Block CHUNK_LOADER = new ChunkLoaderBlock(
            AbstractBlock.Settings.create()
                    .registryKey(BLOCK_KEY)
                    .mapColor(MapColor.IRON_GRAY)
                    .strength(3.0f, 6.0f)
                    .nonOpaque()
    );

    public static final Item CHUNK_LOADER_ITEM = new BlockItem(CHUNK_LOADER, new Item.Settings().registryKey(ITEM_KEY).useBlockPrefixedTranslationKey());

    public static void init() {
        Registry.register(Registries.BLOCK, BLOCK_KEY, CHUNK_LOADER);
        // No recipe file exists anywhere in data/ - only /give or creative can obtain this.
        Registry.register(Registries.ITEM, ITEM_KEY, CHUNK_LOADER_ITEM);
    }
}