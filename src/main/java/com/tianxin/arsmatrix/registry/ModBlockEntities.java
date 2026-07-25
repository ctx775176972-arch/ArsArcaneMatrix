package com.tianxin.arsmatrix.registry;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.blockentity.MatrixCoreBlockEntity;
import com.tianxin.arsmatrix.blockentity.ArcaneMineCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixCoreBlockEntity>> MATRIX_CORE =
            BLOCK_ENTITY_TYPES.register(
                    "matrix_core",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            MatrixCoreBlockEntity::new,
                            ModBlocks.MATRIX_CORE.get()
                    ))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneMineCoreBlockEntity>> ARCANE_MINE_CORE =
            BLOCK_ENTITY_TYPES.register(
                    "arcane_mine_core",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneMineCoreBlockEntity::new,
                            ModBlocks.ARCANE_MINE_CORE.get()
                    ))
            );

    /**
     * Vanilla uses a null DataFixer type for modded block entities that do not
     * participate in the vanilla data-fixer schema.
     */
    @SuppressWarnings("DataFlowIssue")
    private static <T extends BlockEntity> BlockEntityType<T> buildWithoutDataFixer(
            BlockEntityType.Builder<T> builder
    ) {
        return builder.build(null);
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
