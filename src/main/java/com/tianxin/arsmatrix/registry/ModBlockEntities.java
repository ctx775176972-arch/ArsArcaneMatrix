package com.tianxin.arsmatrix.registry;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.blockentity.MatrixCoreBlockEntity;
import com.tianxin.arsmatrix.blockentity.ArcaneMineCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    /**
     * BlockEntity Register
     */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArsArcaneMatrix.MOD_ID);

    /**
     * Matrix Core BlockEntity
     */
    // NeoForge uses null here when the mod does not provide a vanilla DataFixer type.
    //noinspection DataFlowIssue
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixCoreBlockEntity>> MATRIX_CORE =
            BLOCK_ENTITY_TYPES.register(
                    "matrix_core",
                    () -> BlockEntityType.Builder.of(
                            MatrixCoreBlockEntity::new,
                            ModBlocks.MATRIX_CORE.get()
                    ).build(null)
            );

    // NeoForge uses null here when the mod does not provide a vanilla DataFixer type.
    //noinspection DataFlowIssue
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneMineCoreBlockEntity>> ARCANE_MINE_CORE =
            BLOCK_ENTITY_TYPES.register(
                    "arcane_mine_core",
                    () -> BlockEntityType.Builder.of(
                            ArcaneMineCoreBlockEntity::new,
                            ModBlocks.ARCANE_MINE_CORE.get()
                    ).build(null)
            );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
