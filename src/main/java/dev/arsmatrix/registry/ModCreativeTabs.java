package dev.arsmatrix.registry;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.FeatureFlags;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative mode tab registrations for Ars Arcane Matrix. */
public final class ModCreativeTabs {

    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArsArcaneMatrix.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARS_ARCANE_MATRIX =
            CREATIVE_MODE_TABS.register("ars_arcane_matrix", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ars_arcane_matrix"))
                    .icon(() -> ModItems.MATRIX_CORE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MATRIX_CORE.get());
                        output.accept(ModItems.ARCANE_MINE_CORE.get());
                        output.accept(ModItems.ARCANE_AMPLIFIER.get());
                        output.accept(ModItems.ARCANE_STRUCTURAL_FRAME.get());
                        output.accept(ModItems.MATRIX_CONSTRUCTION_WAND.get());
                        output.accept(ModItems.ARCANE_PROCESSOR_CORE.get());
                        output.accept(ModItems.ENCHANTED_CRYSTAL.get());
                        output.accept(ModItems.ARCANE_SMELTER_CORE.get());
                        output.accept(ModItems.ENCHANTED_ARCHWOOD_CHARCOAL.get());
                        output.accept(ModItems.ENCHANTED_ARCHWOOD_CHARCOAL_BLOCK.get());
                        output.accept(ModItems.CASTING_CRYSTAL.get());
                        output.accept(ModItems.ARCANE_CRUSHER_CORE.get());
                        output.accept(ModItems.ENRICHED_MINERAL_CRYSTAL.get());
                        output.accept(ModItems.ANCIENT_GROVE_CATALYST.get());
                        output.accept(ModItems.FORMLESS_ESSENCE.get());
                        output.accept(ModItems.CONDENSED_SUMMONING_CATALYST.get());
                        output.accept(ModItems.RARE_CREATURE_SUMMONING_TABLET.get());
                        output.accept(ModItems.IRON_DUST.get());
                        output.accept(ModItems.COPPER_DUST.get());
                        output.accept(ModItems.GOLD_DUST.get());
                        output.accept(ModItems.ANCIENT_DEBRIS_DUST.get());
                        output.accept(ModItems.SOURCEBOUND_COPPER_ALLOY.get());
                        output.accept(ModItems.SOURCEBOUND_COPPER_ALLOY_DUST.get());
                        output.accept(ModItems.ARCANE_IMBUEMENT_CORE.get());
                        output.accept(ModItems.ADVANCED_IMBUEMENT_CHAMBER.get());
                        output.accept(ModItems.SOURCE_STONE_GENERATOR.get());
                        output.accept(ModItems.CRAFTING_GUIDE.get());
                        output.accept(ModItems.ARCANE_ORDER_PEDESTAL.get());
                        output.accept(ModItems.WIXIE_PATTERN_PROVIDER.get());
                        output.accept(ModItems.WIXIE_ORDER_TERMINAL.get());
                        output.accept(ModItems.AUTOMATIC_STOCK_REQUESTER.get());
                        output.accept(ModItems.STARBUNCLE_LOGISTICS_HUB.get());
                        output.accept(ModItems.ADVANCED_STORAGE_LECTERN.get());
                        output.accept(ModItems.STORAGE_GRID_DIRECTORY.get());
                        output.accept(ModItems.GRID_EXPANSION_WAREHOUSE.get());
                        output.accept(ModItems.SUPER_SOURCE_JAR_CORE.get());
                        output.accept(ModItems.INTEGRATED_SOURCE_RELAY.get());
                        output.accept(ModItems.DIMENSION_ANCHOR.get());
                        output.accept(ModItems.ARCANE_FLUID_RESERVOIR.get());
                        output.accept(ModItems.FLUID_CAPACITY_UPGRADE.get());
                        output.accept(ModItems.FLUID_RANGE_UPGRADE.get());
                        output.accept(ModItems.FLUID_SPEED_UPGRADE.get());
                        output.accept(ModItems.VOLCANIC_CONDENSATION_UPGRADE.get());
                        output.accept(ModItems.ADDITIONAL_FLUID_TANK_MODULE.get());
                        output.accept(ModItems.ARCANE_VACUUM_HOPPER.get());
                        output.accept(ModItems.SOURCE_STONE_FURNACE.get());
                        if (FeatureFlags.ARCANE_ARENA) {
                            output.accept(ModItems.DRYGMY_ARENA.get());
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
