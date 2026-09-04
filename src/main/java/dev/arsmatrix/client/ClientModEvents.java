package dev.arsmatrix.client;

import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.registry.ModMenus;
import dev.arsmatrix.blockentity.ArcaneFluidReservoirBlockEntity;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientModEvents {

    private ClientModEvents() {
    }

    public static void clientSetup(FMLClientSetupEvent event) {
        // Reserved for client-only setup that cannot run directly on the mod event thread.
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MatrixCoreRenderer.MODEL_LAYER, MatrixCoreRenderer::createCubeLayer);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MATRIX_CORE.get(), MatrixCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_MINE_CORE.get(), ArcaneMineCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_PROCESSOR_CORE.get(), ArcaneProcessorCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_SMELTER_CORE.get(), ArcaneSmelterCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_CRUSHER_CORE.get(), ArcaneCrusherCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_FLUID_RESERVOIR.get(),
                ArcaneFluidReservoirRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SUPER_SOURCE_JAR_CORE.get(),
                SuperSourceJarCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DRYGMY_ARENA.get(), DrygmyArenaRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DIMENSION_ANCHOR.get(), DimensionAnchorRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.ARCANE_IMBUEMENT_CORE.get(),
                ArcaneImbuementCoreRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.ARCANE_ORDER_PEDESTAL.get(),
                ArcaneOrderPedestalRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.AUTOMATIC_STOCK_REQUESTER.get(),
                AutomaticStockRequesterRenderer::new
        );
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0
                        ? level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : 0x3F76E4
                        : 0xFFFFFF,
                ModBlocks.SOURCE_STONE_GENERATOR.get()
        );
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (tintIndex != 0) return 0xFFFFFF;
                    if (level != null && pos != null
                            && level.getBlockEntity(pos) instanceof ArcaneFluidReservoirBlockEntity reservoir) {
                        var fluid = reservoir.outputTankFluid();
                        if (fluid == Fluids.EMPTY) return 0x28212F;
                        return IClientFluidTypeExtensions.of(fluid).getTintColor(
                                fluid.defaultFluidState(), level, pos) & 0x00FFFFFF;
                    }
                    return 0x28212F;
                },
                ModBlocks.ARCANE_FLUID_RESERVOIR.get()
        );
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0x3F76E4 : 0xFFFFFF,
                ModItems.SOURCE_STONE_GENERATOR.get()
        );
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0x6B4A86 : 0xFFFFFF,
                ModItems.ARCANE_FLUID_RESERVOIR.get()
        );
    }

    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WIZARDS_POCKET_WATCH.get(), WizardsPocketWatchScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.WIXIE_ORDER_TERMINAL.get(),
                WixieOrderTerminalScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.WIXIE_PATTERN_PROVIDER.get(),
                WixiePatternProviderScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.AUTOMATIC_STOCK_REQUESTER.get(),
                AutomaticStockRequesterScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.STARBUNCLE_LOGISTICS_HUB.get(),
                StarbuncleLogisticsHubScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.STORAGE_GRID_DIRECTORY.get(),
                StorageGridDirectoryScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.ARCANE_FLUID_RESERVOIR.get(),
                ArcaneFluidReservoirScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.ARCANE_REACTION_VESSEL.get(),
                ArcaneReactionVesselScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.ARCANE_VACUUM_HOPPER.get(),
                ArcaneVacuumHopperScreen::new);
        event.register(dev.arsmatrix.registry.ModMenus.SOURCE_STONE_FURNACE.get(),
                SourceStoneFurnaceScreen::new);
    }
}
