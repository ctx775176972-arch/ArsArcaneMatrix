package com.tianxin.arsmatrix.registry;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.block.MatrixCoreBlock;
import com.tianxin.arsmatrix.block.ArcaneMineCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private ModBlocks() {
    }

    /**
     * Block Register
     */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ArsArcaneMatrix.MOD_ID);

    /**
     * Arcane Matrix Core
     */
    public static final DeferredBlock<Block> MATRIX_CORE =
            BLOCKS.register("matrix_core",
                    () -> new MatrixCoreBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(8.0F, 1200.0F)
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.AMETHYST)
                    ));

    /** Arcane Mine Core. */
    public static final DeferredBlock<Block> ARCANE_MINE_CORE =
            BLOCKS.register("arcane_mine_core",
                    () -> new ArcaneMineCoreBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(8.0F, 1200.0F)
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.AMETHYST)
                    ));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
