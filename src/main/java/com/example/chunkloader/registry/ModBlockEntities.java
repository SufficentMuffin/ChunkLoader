package com.example.chunkloader.registry;

import java.util.Set;
import com.example.chunkloader.block.ChunkLoaderBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<ChunkLoaderBlockEntity> CHUNK_LOADER_BE;

    public static void init() {
        // Vanilla BlockEntityType.Builder's build() takes a DataFixerUpper
        // Type<?> argument, which mods almost always just pass null for
        // (that's what Fabric API's own wrapper did internally too). The
        // BlockEntityType$BlockEntityFactory nested interface this relies on
        // is private in these mappings - that's what
        // src/main/resources/chunkloader.accesswidener widens open, so no
        // Fabric API is needed for any of this.
        CHUNK_LOADER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of("chunkloader", "chunk_loader"),
                new BlockEntityType<>(ChunkLoaderBlockEntity::new, Set.of(ModBlocks.CHUNK_LOADER))
        );
    }
}
