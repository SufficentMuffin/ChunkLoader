package com.example.chunkloader.block;

import com.example.chunkloader.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A block that force-loads chunks around itself and manually drives random
 * ticks in that area (vanilla forceload does NOT do this on its own -
 * random ticks normally require a real player nearby).
 *
 * NOTE: Minecraft's exact Block interaction method names/signatures have
 * shifted a couple of times across 1.21.x point releases (e.g.
 * onStateReplaced gained/changed its newState parameter). If this doesn't
 * compile as-is against 1.21.11+build.4, check net.minecraft.block.Block /
 * AbstractBlock in your local mapped sources for the current signature -
 * the logic inside each method body doesn't need to change, just the
 * method header.
 */
public class ChunkLoaderBlock extends Block implements BlockEntityProvider {

    public ChunkLoaderBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient() && world instanceof ServerWorld serverWorld
                && world.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be) {
            be.applyForceLoad(serverWorld, true);
        }
    }

    // NOTE: as of 1.21.5+, onStateReplaced runs AFTER the block entity has
    // already been removed, and is passed the OLD state rather than the new
    // one - so it's no longer useful for per-instance cleanup here. The
    // actual "un-forceload on removal" logic lives in
    // ChunkLoaderBlockEntity#onBlockReplaced instead. This override is kept
    // only because AbstractBlock still requires/expects it to exist.
    @Override
    public void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return interact(state, world, pos, player);
    }

    @Override
    public ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient()) {
            player.sendMessage(Text.literal("DEBUG: onUseWithItem was called!"), true);
        }
        return interact(state, world, pos, player);
    }

    private ActionResult interact(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (world.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be) {
            if (!world.isClient() && world instanceof ServerWorld serverWorld) {
                if (player.isCreativeLevelTwoOp()) {
                    be.cycleRadius(serverWorld);
                    player.sendMessage(Text.literal("Chunk Loader radius set to " + be.getRadius()
                            + " chunk(s) (" + (be.getRadius() * 16) + " blocks)."), true);
                } else {
                    player.sendMessage(Text.literal("Chunk Loader radius: " + be.getRadius()
                            + " chunk(s). Ask OP to change radiua."), true);
                }
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return validateTicker(type, ModBlockEntities.CHUNK_LOADER_BE, ChunkLoaderBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    protected static <T extends BlockEntity, E extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType == givenType ? (BlockEntityTicker<T>) ticker : null;
    }
}
