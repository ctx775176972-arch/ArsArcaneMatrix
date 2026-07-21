package com.tianxin.arsmatrix.registry;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.block.MatrixCoreBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
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
     * Item Register（用于注册方块物品）
     */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ArsArcaneMatrix.MOD_ID);

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

    /**
     * Block Item
     */
    public static final DeferredItem<BlockItem> MATRIX_CORE_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "matrix_core",
                    MATRIX_CORE,
                    new Item.Properties()
            );

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}