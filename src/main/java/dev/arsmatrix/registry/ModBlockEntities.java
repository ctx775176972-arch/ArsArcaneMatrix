package dev.arsmatrix.registry;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.blockentity.ArcaneImbuementCoreBlockEntity;
import dev.arsmatrix.blockentity.MatrixCoreBlockEntity;
import dev.arsmatrix.blockentity.DrygmyArenaBlockEntity;
import dev.arsmatrix.blockentity.ArcaneMineCoreBlockEntity;
import dev.arsmatrix.blockentity.SourceStoneGeneratorBlockEntity;
import dev.arsmatrix.blockentity.ArcaneOrderPedestalBlockEntity;
import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity;
import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import dev.arsmatrix.blockentity.AdvancedStorageLecternBlockEntity;
import dev.arsmatrix.blockentity.ArcaneProcessorCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneSmelterCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneCrusherCoreBlockEntity;
import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import dev.arsmatrix.blockentity.StorageGridDirectoryBlockEntity;
import dev.arsmatrix.blockentity.SuperSourceJarCoreBlockEntity;
import dev.arsmatrix.blockentity.IntegratedSourceRelayBlockEntity;
import dev.arsmatrix.blockentity.DimensionAnchorBlockEntity;
import dev.arsmatrix.blockentity.ArcaneFluidReservoirBlockEntity;
import dev.arsmatrix.blockentity.ArcaneFluidTankBlockEntity;
import dev.arsmatrix.blockentity.ArcaneReactionVesselBlockEntity;
import dev.arsmatrix.blockentity.ArcaneVacuumHopperBlockEntity;
import dev.arsmatrix.blockentity.SourceStoneFurnaceBlockEntity;
import dev.arsmatrix.blockentity.ArcaneSourceJarBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneProcessorCoreBlockEntity>>
            ARCANE_PROCESSOR_CORE = BLOCK_ENTITY_TYPES.register(
                    "arcane_processor_core",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneProcessorCoreBlockEntity::new,
                            ModBlocks.ARCANE_PROCESSOR_CORE.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneSmelterCoreBlockEntity>>
            ARCANE_SMELTER_CORE = BLOCK_ENTITY_TYPES.register(
                    "arcane_smelter_core",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneSmelterCoreBlockEntity::new,
                            ModBlocks.ARCANE_SMELTER_CORE.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneCrusherCoreBlockEntity>>
            ARCANE_CRUSHER_CORE = BLOCK_ENTITY_TYPES.register(
                    "arcane_crusher_core",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneCrusherCoreBlockEntity::new,
                            ModBlocks.ARCANE_CRUSHER_CORE.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneImbuementCoreBlockEntity>>
            ARCANE_IMBUEMENT_CORE = BLOCK_ENTITY_TYPES.register(
                    "arcane_imbuement_core",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneImbuementCoreBlockEntity::new,
                            ModBlocks.ARCANE_IMBUEMENT_CORE.get()
                    ))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SourceStoneGeneratorBlockEntity>>
            SOURCE_STONE_GENERATOR = BLOCK_ENTITY_TYPES.register(
                    "source_stone_generator",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            SourceStoneGeneratorBlockEntity::new,
                            ModBlocks.SOURCE_STONE_GENERATOR.get()
                    ))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrygmyArenaBlockEntity>> DRYGMY_ARENA =
            BLOCK_ENTITY_TYPES.register(
                    "drygmy_arena",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            DrygmyArenaBlockEntity::new,
                            ModBlocks.DRYGMY_ARENA.get()
                    ))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneOrderPedestalBlockEntity>>
            ARCANE_ORDER_PEDESTAL = BLOCK_ENTITY_TYPES.register(
                    "arcane_order_pedestal",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneOrderPedestalBlockEntity::new, ModBlocks.ARCANE_ORDER_PEDESTAL.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WixiePatternProviderBlockEntity>>
            WIXIE_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "wixie_pattern_provider",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            WixiePatternProviderBlockEntity::new, ModBlocks.WIXIE_PATTERN_PROVIDER.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WixieOrderTerminalBlockEntity>>
            WIXIE_ORDER_TERMINAL = BLOCK_ENTITY_TYPES.register(
                    "wixie_order_terminal",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            WixieOrderTerminalBlockEntity::new, ModBlocks.WIXIE_ORDER_TERMINAL.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomaticStockRequesterBlockEntity>>
            AUTOMATIC_STOCK_REQUESTER = BLOCK_ENTITY_TYPES.register(
                    "automatic_stock_requester",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            AutomaticStockRequesterBlockEntity::new,
                            ModBlocks.AUTOMATIC_STOCK_REQUESTER.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarbuncleLogisticsHubBlockEntity>>
            STARBUNCLE_LOGISTICS_HUB = BLOCK_ENTITY_TYPES.register(
                    "starbuncle_logistics_hub",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            StarbuncleLogisticsHubBlockEntity::new,
                            ModBlocks.STARBUNCLE_LOGISTICS_HUB.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedStorageLecternBlockEntity>>
            ADVANCED_STORAGE_LECTERN = BLOCK_ENTITY_TYPES.register(
                    "advanced_storage_lectern",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            AdvancedStorageLecternBlockEntity::new,
                            ModBlocks.ADVANCED_STORAGE_LECTERN.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageGridDirectoryBlockEntity>>
            STORAGE_GRID_DIRECTORY = BLOCK_ENTITY_TYPES.register(
                    "storage_grid_directory",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            StorageGridDirectoryBlockEntity::new,
                            ModBlocks.STORAGE_GRID_DIRECTORY.get()))
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperSourceJarCoreBlockEntity>>
            SUPER_SOURCE_JAR_CORE = BLOCK_ENTITY_TYPES.register(
                    "super_source_jar_core",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            SuperSourceJarCoreBlockEntity::new,
                            ModBlocks.SUPER_SOURCE_JAR_CORE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneSourceJarBlockEntity>>
            ARCANE_SOURCE_JAR = BLOCK_ENTITY_TYPES.register(
                    "arcane_source_jar",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneSourceJarBlockEntity::new,
                            ModBlocks.ARCANE_SOURCE_JAR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IntegratedSourceRelayBlockEntity>>
            INTEGRATED_SOURCE_RELAY = BLOCK_ENTITY_TYPES.register(
                    "integrated_source_relay",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            IntegratedSourceRelayBlockEntity::new,
                            ModBlocks.INTEGRATED_SOURCE_RELAY.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DimensionAnchorBlockEntity>>
            DIMENSION_ANCHOR = BLOCK_ENTITY_TYPES.register(
                    "dimension_anchor",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            DimensionAnchorBlockEntity::new,
                    ModBlocks.DIMENSION_ANCHOR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneFluidReservoirBlockEntity>>
            ARCANE_FLUID_RESERVOIR = BLOCK_ENTITY_TYPES.register(
                    "arcane_fluid_reservoir",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneFluidReservoirBlockEntity::new,
                            ModBlocks.ARCANE_FLUID_RESERVOIR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneFluidTankBlockEntity>>
            ARCANE_FLUID_TANK = BLOCK_ENTITY_TYPES.register(
                    "arcane_fluid_tank",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneFluidTankBlockEntity::new,
                            ModBlocks.ARCANE_FLUID_TANK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneReactionVesselBlockEntity>>
            ARCANE_REACTION_VESSEL = BLOCK_ENTITY_TYPES.register(
                    "arcane_reaction_vessel",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneReactionVesselBlockEntity::new,
                            ModBlocks.ARCANE_REACTION_VESSEL.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneVacuumHopperBlockEntity>>
            ARCANE_VACUUM_HOPPER = BLOCK_ENTITY_TYPES.register(
                    "arcane_vacuum_hopper",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ArcaneVacuumHopperBlockEntity::new,
                    ModBlocks.ARCANE_VACUUM_HOPPER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SourceStoneFurnaceBlockEntity>>
            SOURCE_STONE_FURNACE = BLOCK_ENTITY_TYPES.register(
                    "source_stone_furnace",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            SourceStoneFurnaceBlockEntity::new,
                            ModBlocks.SOURCE_STONE_FURNACE.get())));

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
