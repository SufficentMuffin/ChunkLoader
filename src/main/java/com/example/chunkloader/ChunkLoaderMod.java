package com.example.chunkloader;

import com.example.chunkloader.registry.ModBlockEntities;
import com.example.chunkloader.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkLoaderMod implements ModInitializer {
    public static final String MOD_ID = "chunkloader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModBlockEntities.init();
        LOGGER.info("Chunk Loader mod initialized");
    }
}
