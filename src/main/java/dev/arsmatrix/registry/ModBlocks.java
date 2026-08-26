package dev.arsmatrix.registry;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.block.ArcaneImbuementCoreBlock;
import dev.arsmatrix.block.DrygmyArenaBlock;
import dev.arsmatrix.block.MatrixCoreBlock;
import dev.arsmatrix.block.ArcaneMineCoreBlock;
import dev.arsmatrix.block.SourceStoneGeneratorBlock;
import dev.arsmatrix.block.ArcaneOrderPedestalBlock;
import dev.arsmatrix.block.WixieOrderTerminalBlock;
import dev.arsmatrix.block.WixiePatternProviderBlock;
import dev.arsmatrix.block.AdvancedStorageLecternBlock;
import dev.arsmatrix.block.ArcaneProcessorCoreBlock;
import dev.arsmatrix.block.ArcaneSmelterCoreBlock;
import dev.arsmatrix.block.ArcaneCrusherCoreBlock;
import dev.arsmatrix.block.AdvancedImbuementChamberBlock;
import dev.arsmatrix.block.AutomaticStockRequesterBlock;
import dev.arsmatrix.block.StarbuncleLogisticsHubBlock;
import dev.arsmatrix.block.StorageGridDirectoryBlock;
import dev.arsmatrix.block.SuperSourceJarCoreBlock;
import dev.arsmatrix.block.ArcaneFluidTankBlock;
import dev.arsmatrix.block.IntegratedSourceRelayBlock;
import dev.arsmatrix.block.DimensionAnchorBlock;
import dev.arsmatrix.block.ArcaneFluidReservoirBlock;
import dev.arsmatrix.block.ArcaneReactionVesselBlock;
import dev.arsmatrix.block.ArcaneVacuumHopperBlock;
import dev.arsmatrix.block.SourceStoneFurnaceBlock;
import dev.arsmatrix.block.ArcaneSourceJarBlock;
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
                                    .noOcclusion()
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

    /** Rare multiblock amplifier produced by a full Arcane Mine. */
    public static final DeferredBlock<Block> ARCANE_AMPLIFIER =
            BLOCKS.registerSimpleBlock(
                    "arcane_amplifier",
                    BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 10)
                            .sound(SoundType.AMETHYST)
            );

    /** Shared alloy frame used by the Matrix and staged mineral multiblocks. */
    public static final DeferredBlock<Block> ARCANE_STRUCTURAL_FRAME = BLOCKS.registerSimpleBlock(
            "arcane_structural_frame",
            BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
    );

    /** Dense enchanted fuel block used by the high-throughput Arcane Smelter. */
    public static final DeferredBlock<Block> ENCHANTED_ARCHWOOD_CHARCOAL_BLOCK = BLOCKS.registerSimpleBlock(
            "enchanted_archwood_charcoal_block",
            BlockBehaviour.Properties.of()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.WOOD)
    );

    /** First controller in the staged mineral-processing line. */
    public static final DeferredBlock<Block> ARCANE_PROCESSOR_CORE = BLOCKS.register(
            "arcane_processor_core",
            () -> new ArcaneProcessorCoreBlock(BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST))
    );

    /** Second controller in the staged mineral-processing line. */
    public static final DeferredBlock<Block> ARCANE_SMELTER_CORE = BLOCKS.register(
            "arcane_smelter_core",
            () -> new ArcaneSmelterCoreBlock(BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ArcaneSmelterCoreBlock.LIT) ? 13 : 0)
                    .sound(SoundType.STONE))
    );

    /** Third controller in the staged mineral-processing line. */
    public static final DeferredBlock<Block> ARCANE_CRUSHER_CORE = BLOCKS.register(
            "arcane_crusher_core",
            () -> new ArcaneCrusherCoreBlock(BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE))
    );

    /** Controller that strengthens an Ars Nouveau Imbuement Chamber above it. */
    public static final DeferredBlock<Block> ARCANE_IMBUEMENT_CORE =
            BLOCKS.register(
                    "arcane_imbuement_core",
                    () -> new ArcaneImbuementCoreBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(8.0F, 1200.0F)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()
                                    .lightLevel(state -> 8)
                                    .sound(SoundType.AMETHYST)
                    )
            );

    /** Faster native-compatible chamber and the only chamber that can produce Formless Essence. */
    public static final DeferredBlock<Block> ADVANCED_IMBUEMENT_CHAMBER = BLOCKS.register(
            "advanced_imbuement_chamber", AdvancedImbuementChamberBlock::new);

    /** Single controller for pedestal-selected bulk stone and soil generation. */
    public static final DeferredBlock<Block> SOURCE_STONE_GENERATOR =
            BLOCKS.register(
                    "source_stone_generator",
                    () -> new SourceStoneGeneratorBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(5.0F, 12.0F)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()
                                    .sound(SoundType.STONE)
                    )
            );

    /** Displays a virtual requested stack for the nearby crafting network. */
    public static final DeferredBlock<Block> ARCANE_ORDER_PEDESTAL = BLOCKS.register(
            "arcane_order_pedestal",
            () -> new ArcaneOrderPedestalBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST))
    );

    /** Holds recipe guides and binds a crafting table plus Wixie as a worker. */
    public static final DeferredBlock<Block> WIXIE_PATTERN_PROVIDER = BLOCKS.register(
            "wixie_pattern_provider",
            () -> new WixiePatternProviderBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST))
    );

    /** Plans recursive orders and coordinates all nearby pattern providers. */
    public static final DeferredBlock<Block> WIXIE_ORDER_TERMINAL = BLOCKS.register(
            "wixie_order_terminal",
            () -> new WixieOrderTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST))
    );

    /** Maintains a configured item count by submitting catalyst-paid Wixie orders. */
    public static final DeferredBlock<Block> AUTOMATIC_STOCK_REQUESTER = BLOCKS.register(
            "automatic_stock_requester",
            () -> new AutomaticStockRequesterBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST))
    );

    /** Local recovery and output station for the owner's Starbuncles. */
    public static final DeferredBlock<Block> STARBUNCLE_LOGISTICS_HUB = BLOCKS.register(
            "starbuncle_logistics_hub",
            () -> new StarbuncleLogisticsHubBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST))
    );

    /** Late-game storage lectern with integrated Wixie crafting requests. */
    public static final DeferredBlock<Block> ADVANCED_STORAGE_LECTERN = BLOCKS.register(
            "advanced_storage_lectern",
            () -> new AdvancedStorageLecternBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST))
    );

    /** Compact item network expanded by warehouse modules installed in its GUI. */
    public static final DeferredBlock<Block> STORAGE_GRID_DIRECTORY = BLOCKS.register(
            "storage_grid_directory",
            () -> new StorageGridDirectoryBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST))
    );

    /** Core for the hollow 7x7x7 Matrix Source Reservoir. */
    public static final DeferredBlock<Block> SUPER_SOURCE_JAR_CORE = BLOCKS.register(
            "super_source_jar_core",
            () -> new SuperSourceJarCoreBlock(BlockBehaviour.Properties.of()
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    /** Pre-Matrix one-million Source buffer with active nearby producer collection. */
    public static final DeferredBlock<Block> ARCANE_SOURCE_JAR = BLOCKS.register(
            "arcane_source_jar",
            () -> new ArcaneSourceJarBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    /** Local network outlet that exposes remote Source to nearby Ars machines. */
    public static final DeferredBlock<Block> INTEGRATED_SOURCE_RELAY = BLOCKS.register(
            "integrated_source_relay",
            () -> new IntegratedSourceRelayBlock(BlockBehaviour.Properties.of()
                    .strength(6.0F, 20.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 6)
                    .sound(SoundType.AMETHYST)));

    /** Earth Essence-powered loader for the chunk containing the block. */
    public static final DeferredBlock<Block> DIMENSION_ANCHOR = BLOCKS.register(
            "dimension_anchor",
            () -> new DimensionAnchorBlock(BlockBehaviour.Properties.of()
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 5)
                    .sound(SoundType.DEEPSLATE)));

    /** Upgradeable multi-fluid storage, world pump, and bound-container pump. */
    public static final DeferredBlock<Block> ARCANE_FLUID_RESERVOIR = BLOCKS.register(
            "arcane_fluid_reservoir",
            () -> new ArcaneFluidReservoirBlock(BlockBehaviour.Properties.of()
                    .strength(6.0F, 20.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    /** Placeable single-fluid storage that also serves as a controller tank module. */
    public static final DeferredBlock<Block> ARCANE_FLUID_TANK = BLOCKS.register(
            "arcane_fluid_tank",
            () -> new ArcaneFluidTankBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    /** Source-powered item/fluid processor with data-driven reactions. */
    public static final DeferredBlock<Block> ARCANE_REACTION_VESSEL = BLOCKS.register(
            "arcane_reaction_vessel",
            () -> new ArcaneReactionVesselBlock(BlockBehaviour.Properties.of()
                    .strength(6.0F, 20.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    /** Wide-area item and experience collector with gem conversion. */
    public static final DeferredBlock<Block> ARCANE_VACUUM_HOPPER = BLOCKS.register(
            "arcane_vacuum_hopper",
            () -> new ArcaneVacuumHopperBlock(BlockBehaviour.Properties.of()
                    .strength(6.0F, 20.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    /** Two-slot Source-powered furnace for smelting and smoking recipes. */
    public static final DeferredBlock<Block> SOURCE_STONE_FURNACE = BLOCKS.register(
            "source_stone_furnace",
            () -> new SourceStoneFurnaceBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(SourceStoneFurnaceBlock.LIT) ? 13 : 0)
                    .sound(SoundType.STONE)));

    /** Single-jar boss-drop simulator created by using a Drygmy Charm on a Netherite Block. */
    public static final DeferredBlock<Block> DRYGMY_ARENA =
            BLOCKS.register(
                    "drygmy_arena",
                    () -> new DrygmyArenaBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(50.0F, 1200.0F)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()
                                    .sound(SoundType.NETHERITE_BLOCK)
                    )
            );

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
