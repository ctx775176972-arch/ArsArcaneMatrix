package dev.arsmatrix.registry;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.item.CraftingGuideItem;
import dev.arsmatrix.item.AdvancedImbuementChamberItem;
import dev.arsmatrix.item.EnchantedArchwoodCharcoalItem;
import dev.arsmatrix.item.EnchantedArchwoodCharcoalBlockItem;
import dev.arsmatrix.item.MatrixConstructionWandItem;
import dev.arsmatrix.item.WixiePatternProviderItem;
import dev.arsmatrix.item.HubFilterScrollItem;
import dev.arsmatrix.item.StarbuncleLogisticsHubItem;
import dev.arsmatrix.item.ArcaneOrderPedestalItem;
import dev.arsmatrix.item.AutomaticStockRequesterItem;
import dev.arsmatrix.ritual.ModRituals;
import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registrations for Ars Arcane Matrix. */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ArsArcaneMatrix.MOD_ID);

    public static final DeferredItem<BlockItem> MATRIX_CORE =
            ITEMS.registerSimpleBlockItem("matrix_core", ModBlocks.MATRIX_CORE, new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_MINE_CORE =
            ITEMS.registerSimpleBlockItem("arcane_mine_core", ModBlocks.ARCANE_MINE_CORE, new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_AMPLIFIER =
            ITEMS.registerSimpleBlockItem("arcane_amplifier", ModBlocks.ARCANE_AMPLIFIER, new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_STRUCTURAL_FRAME = ITEMS.registerSimpleBlockItem(
            "arcane_structural_frame", ModBlocks.ARCANE_STRUCTURAL_FRAME, new Item.Properties());

    public static final DeferredItem<MatrixConstructionWandItem> MATRIX_CONSTRUCTION_WAND = ITEMS.register(
            "matrix_construction_wand", () -> new MatrixConstructionWandItem(new Item.Properties()));

    public static final DeferredItem<BlockItem> ARCANE_PROCESSOR_CORE = ITEMS.registerSimpleBlockItem(
            "arcane_processor_core", ModBlocks.ARCANE_PROCESSOR_CORE, new Item.Properties());

    public static final DeferredItem<Item> ENCHANTED_CRYSTAL = ITEMS.registerSimpleItem(
            "enchanted_crystal", new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_SMELTER_CORE = ITEMS.registerSimpleBlockItem(
            "arcane_smelter_core", ModBlocks.ARCANE_SMELTER_CORE, new Item.Properties());

    public static final DeferredItem<Item> ENCHANTED_ARCHWOOD_CHARCOAL = ITEMS.register(
            "enchanted_archwood_charcoal",
            () -> new EnchantedArchwoodCharcoalItem(new Item.Properties()));

    public static final DeferredItem<BlockItem> ENCHANTED_ARCHWOOD_CHARCOAL_BLOCK = ITEMS.register(
            "enchanted_archwood_charcoal_block",
            () -> new EnchantedArchwoodCharcoalBlockItem(
                    ModBlocks.ENCHANTED_ARCHWOOD_CHARCOAL_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<Item> CASTING_CRYSTAL = ITEMS.registerSimpleItem(
            "casting_crystal", new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_CRUSHER_CORE = ITEMS.registerSimpleBlockItem(
            "arcane_crusher_core", ModBlocks.ARCANE_CRUSHER_CORE, new Item.Properties());

    public static final DeferredItem<Item> ENRICHED_MINERAL_CRYSTAL = ITEMS.registerSimpleItem(
            "enriched_mineral_crystal", new Item.Properties());

    public static final DeferredItem<Item> ANCIENT_GROVE_CATALYST = ITEMS.registerSimpleItem(
            "ancient_grove_catalyst", new Item.Properties());

    public static final DeferredItem<Item> FORMLESS_ESSENCE = ITEMS.registerSimpleItem(
            "formless_essence", new Item.Properties());

    public static final DeferredItem<Item> CONDENSED_SUMMONING_CATALYST = ITEMS.registerSimpleItem(
            "condensed_summoning_catalyst", new Item.Properties());

    public static final DeferredItem<RitualTablet> RARE_CREATURE_SUMMONING_TABLET = ITEMS.register(
            "rare_creature_summoning_tablet",
            () -> new RitualTablet(ModRituals.RARE_CREATURE_SUMMONING));

    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem(
            "iron_dust", new Item.Properties());

    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem(
            "copper_dust", new Item.Properties());

    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem(
            "gold_dust", new Item.Properties());

    public static final DeferredItem<Item> ANCIENT_DEBRIS_DUST = ITEMS.registerSimpleItem(
            "ancient_debris_dust", new Item.Properties());

    public static final DeferredItem<Item> SOURCEBOUND_COPPER_ALLOY = ITEMS.registerSimpleItem(
            "sourcebound_copper_alloy", new Item.Properties());

    public static final DeferredItem<Item> SOURCEBOUND_COPPER_ALLOY_DUST = ITEMS.registerSimpleItem(
            "sourcebound_copper_alloy_dust", new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_IMBUEMENT_CORE =
            ITEMS.registerSimpleBlockItem(
                    "arcane_imbuement_core",
                    ModBlocks.ARCANE_IMBUEMENT_CORE,
                    new Item.Properties()
            );

    public static final DeferredItem<AdvancedImbuementChamberItem> ADVANCED_IMBUEMENT_CHAMBER = ITEMS.register(
            "advanced_imbuement_chamber",
            () -> new AdvancedImbuementChamberItem(
                    ModBlocks.ADVANCED_IMBUEMENT_CHAMBER.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SOURCE_STONE_GENERATOR =
            ITEMS.registerSimpleBlockItem(
                    "source_stone_generator",
                    ModBlocks.SOURCE_STONE_GENERATOR,
                    new Item.Properties()
            );

    public static final DeferredItem<BlockItem> DRYGMY_ARENA =
            ITEMS.registerSimpleBlockItem(
                    "drygmy_arena",
                    ModBlocks.DRYGMY_ARENA,
                    new Item.Properties()
            );

    public static final DeferredItem<ArcaneOrderPedestalItem> ARCANE_ORDER_PEDESTAL = ITEMS.register(
            "arcane_order_pedestal",
            () -> new ArcaneOrderPedestalItem(ModBlocks.ARCANE_ORDER_PEDESTAL.get(),
                    new Item.Properties().component(ModDataComponents.ORDER_PEDESTAL_TIER.get(), 0)));

    public static final DeferredItem<WixiePatternProviderItem> WIXIE_PATTERN_PROVIDER = ITEMS.register(
            "wixie_pattern_provider",
            () -> new WixiePatternProviderItem(ModBlocks.WIXIE_PATTERN_PROVIDER.get(),
                    new Item.Properties().component(ModDataComponents.PATTERN_PROVIDER_TIER.get(), 0))
    );

    public static final DeferredItem<BlockItem> WIXIE_ORDER_TERMINAL =
            ITEMS.registerSimpleBlockItem("wixie_order_terminal", ModBlocks.WIXIE_ORDER_TERMINAL,
                    new Item.Properties());

    public static final DeferredItem<AutomaticStockRequesterItem> AUTOMATIC_STOCK_REQUESTER = ITEMS.register(
            "automatic_stock_requester",
            () -> new AutomaticStockRequesterItem(ModBlocks.AUTOMATIC_STOCK_REQUESTER.get(),
                    new Item.Properties().component(ModDataComponents.STOCK_REQUESTER_TIER.get(), 0)));

    public static final DeferredItem<StarbuncleLogisticsHubItem> STARBUNCLE_LOGISTICS_HUB = ITEMS.register(
            "starbuncle_logistics_hub",
            () -> new StarbuncleLogisticsHubItem(ModBlocks.STARBUNCLE_LOGISTICS_HUB.get(),
                    new Item.Properties().component(ModDataComponents.LOGISTICS_HUB_TIER.get(), 0)));

    /** Internal native ItemScroll implementation used by the logistics hub. */
    public static final DeferredItem<HubFilterScrollItem> HUB_FILTER_SCROLL = ITEMS.register(
            "hub_filter_scroll", () -> new HubFilterScrollItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> ADVANCED_STORAGE_LECTERN =
            ITEMS.registerSimpleBlockItem("advanced_storage_lectern", ModBlocks.ADVANCED_STORAGE_LECTERN,
                    new Item.Properties());

    public static final DeferredItem<BlockItem> STORAGE_GRID_DIRECTORY =
            ITEMS.registerSimpleBlockItem("storage_grid_directory", ModBlocks.STORAGE_GRID_DIRECTORY,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> GRID_EXPANSION_WAREHOUSE = ITEMS.registerSimpleItem(
            "grid_expansion_warehouse", new Item.Properties().stacksTo(4));

    public static final DeferredItem<BlockItem> SUPER_SOURCE_JAR_CORE =
            ITEMS.registerSimpleBlockItem("super_source_jar_core", ModBlocks.SUPER_SOURCE_JAR_CORE,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> ARCANE_SOURCE_JAR =
            ITEMS.registerSimpleBlockItem("arcane_source_jar", ModBlocks.ARCANE_SOURCE_JAR,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> INTEGRATED_SOURCE_RELAY =
            ITEMS.registerSimpleBlockItem("integrated_source_relay", ModBlocks.INTEGRATED_SOURCE_RELAY,
                    new Item.Properties());

    public static final DeferredItem<BlockItem> DIMENSION_ANCHOR =
            ITEMS.registerSimpleBlockItem("dimension_anchor", ModBlocks.DIMENSION_ANCHOR,
                    new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_FLUID_RESERVOIR = ITEMS.registerSimpleBlockItem(
            "arcane_fluid_reservoir", ModBlocks.ARCANE_FLUID_RESERVOIR, new Item.Properties());
    public static final DeferredItem<BlockItem> ARCANE_REACTION_VESSEL = ITEMS.registerSimpleBlockItem(
            "arcane_reaction_vessel", ModBlocks.ARCANE_REACTION_VESSEL, new Item.Properties());
    public static final DeferredItem<Item> FLUID_CAPACITY_UPGRADE = ITEMS.registerSimpleItem(
            "fluid_capacity_upgrade", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> FLUID_RANGE_UPGRADE = ITEMS.registerSimpleItem(
            "fluid_range_upgrade", new Item.Properties().stacksTo(3));
    public static final DeferredItem<Item> FLUID_SPEED_UPGRADE = ITEMS.registerSimpleItem(
            "fluid_speed_upgrade", new Item.Properties().stacksTo(3));
    /** A placeable reservoir that may also be installed inside an Arcane Fluid Controller. */
    public static final DeferredItem<BlockItem> ARCANE_FLUID_TANK = ITEMS.registerSimpleBlockItem(
            "arcane_fluid_tank", ModBlocks.ARCANE_FLUID_TANK, new Item.Properties().stacksTo(2));
    public static final DeferredItem<BlockItem> ARCANE_VACUUM_HOPPER = ITEMS.registerSimpleBlockItem(
            "arcane_vacuum_hopper", ModBlocks.ARCANE_VACUUM_HOPPER, new Item.Properties());
    public static final DeferredItem<BlockItem> SOURCE_STONE_FURNACE = ITEMS.registerSimpleBlockItem(
            "source_stone_furnace", ModBlocks.SOURCE_STONE_FURNACE, new Item.Properties());

    public static final DeferredItem<CraftingGuideItem> CRAFTING_GUIDE = ITEMS.register(
            "crafting_guide", () -> new CraftingGuideItem(new Item.Properties().stacksTo(16))
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
