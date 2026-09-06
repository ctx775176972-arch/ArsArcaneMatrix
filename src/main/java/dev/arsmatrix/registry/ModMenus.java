package dev.arsmatrix.registry;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.menu.WixiePatternProviderMenu;
import dev.arsmatrix.menu.AutomaticStockRequesterMenu;
import dev.arsmatrix.menu.StarbuncleLogisticsHubMenu;
import dev.arsmatrix.menu.StorageGridDirectoryMenu;
import dev.arsmatrix.menu.ArcaneFluidReservoirMenu;
import dev.arsmatrix.menu.ArcaneReactionVesselMenu;
import dev.arsmatrix.menu.ArcaneVacuumHopperMenu;
import dev.arsmatrix.menu.SourceStoneFurnaceMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ArsArcaneMatrix.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<dev.arsmatrix.menu.WizardsPocketWatchMenu>>
            WIZARDS_POCKET_WATCH = MENUS.register("wizards_pocket_watch",
                    () -> IMenuTypeExtension.create(dev.arsmatrix.menu.WizardsPocketWatchMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WixieOrderTerminalMenu>> WIXIE_ORDER_TERMINAL =
            MENUS.register("wixie_order_terminal",
                    () -> IMenuTypeExtension.create(WixieOrderTerminalMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WixiePatternProviderMenu>> WIXIE_PATTERN_PROVIDER =
            MENUS.register("wixie_pattern_provider",
                    () -> IMenuTypeExtension.create(WixiePatternProviderMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AutomaticStockRequesterMenu>>
            AUTOMATIC_STOCK_REQUESTER = MENUS.register(
                    "automatic_stock_requester",
                    () -> IMenuTypeExtension.create(AutomaticStockRequesterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<StarbuncleLogisticsHubMenu>>
            STARBUNCLE_LOGISTICS_HUB = MENUS.register(
                    "starbuncle_logistics_hub",
                    () -> IMenuTypeExtension.create(StarbuncleLogisticsHubMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<StorageGridDirectoryMenu>>
            STORAGE_GRID_DIRECTORY = MENUS.register(
                    "storage_grid_directory",
                    () -> IMenuTypeExtension.create(StorageGridDirectoryMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneFluidReservoirMenu>>
            ARCANE_FLUID_RESERVOIR = MENUS.register(
                    "arcane_fluid_reservoir",
                    () -> IMenuTypeExtension.create(ArcaneFluidReservoirMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneReactionVesselMenu>>
            ARCANE_REACTION_VESSEL = MENUS.register(
                    "arcane_reaction_vessel",
                    () -> IMenuTypeExtension.create(ArcaneReactionVesselMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneVacuumHopperMenu>>
            ARCANE_VACUUM_HOPPER = MENUS.register(
                    "arcane_vacuum_hopper",
                    () -> IMenuTypeExtension.create(ArcaneVacuumHopperMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SourceStoneFurnaceMenu>>
            SOURCE_STONE_FURNACE = MENUS.register(
                    "source_stone_furnace",
                    () -> IMenuTypeExtension.create(SourceStoneFurnaceMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
