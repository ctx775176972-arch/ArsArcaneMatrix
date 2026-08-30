package dev.arsmatrix.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.documentation.ReloadDocumentationEvent;
import com.hollingsworth.arsnouveau.api.documentation.DocCategory;
import com.hollingsworth.arsnouveau.api.documentation.builder.DocEntryBuilder;
import com.hollingsworth.arsnouveau.api.registry.DocumentationRegistry;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.FeatureFlags;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.spell.EffectCapture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Registers Ars Arcane Matrix entries in Ars Nouveau's current spell-book documentation system. */
public final class ModDocumentation {

    private ModDocumentation() {
    }

    public static void addEntries(ReloadDocumentationEvent.AddEntries event) {
        Item matrixCore = ModItems.MATRIX_CORE.get();
        DocCategory root = new DocCategory(
                ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, ArsArcaneMatrix.MOD_ID),
                matrixCore.getDefaultInstance(),
                1100
        );
        DocCategory gettingStarted = sub(root, "getting_started", ModItems.MATRIX_CONSTRUCTION_WAND.get(), 0);
        DocCategory source = sub(root, "source_network", matrixCore, 10);
        DocCategory minerals = sub(root, "mineral_processing", ModItems.ARCANE_MINE_CORE.get(), 20);
        DocCategory crafting = sub(root, "crafting_automation", ModItems.WIXIE_ORDER_TERMINAL.get(), 30);
        DocCategory storage = sub(root, "storage_logistics", ModItems.STORAGE_GRID_DIRECTORY.get(), 40);
        DocCategory creatures = sub(root, "magical_creatures", ItemsRegistry.AMETHYST_GOLEM_CHARM.get(), 50);
        DocCategory adventure = sub(root, "adventure_rituals", ModItems.RARE_CREATURE_SUMMONING_TABLET.get(), 60);
        DocCategory misc = sub(root, "tools_misc", ModItems.ARCANE_AMPLIFIER.get(), 70);
        DocumentationRegistry.registerMainCategory(root);

        DocEntryBuilder roadmap = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, gettingStarted, ModItems.MATRIX_CONSTRUCTION_WAND.get())
                .withSortNum(0)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.roadmap.summary"),
                        Component.translatable("documentation.ars_arcane_matrix.roadmap.title"),
                        ModItems.MATRIX_CONSTRUCTION_WAND.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.roadmap.overview"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.roadmap.progression.text"),
                        Component.translatable("documentation.ars_arcane_matrix.roadmap.progression.title"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.construction_wand.text"),
                        Component.translatable("documentation.ars_arcane_matrix.construction_wand.title"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.MATRIX_CONSTRUCTION_WAND.get());
        DocumentationRegistry.registerEntry(gettingStarted, roadmap.build());

        DocEntryBuilder builder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                source,
                matrixCore
        )
                .withSortNum(50)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.matrix_core.overview"),
                        Component.translatable("block.ars_arcane_matrix.matrix_core"),
                        matrixCore.getDefaultInstance()
                )
                .withCraftingPages()
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.multiblock.building"))
                .addConnectedSearch(matrixCore);

        DocumentationRegistry.registerEntry(source, builder.build());

        Item mineCore = ModItems.ARCANE_MINE_CORE.get();
        DocEntryBuilder mineBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                minerals,
                mineCore
        )
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_mine.overview"),
                        Component.translatable("block.ars_arcane_matrix.arcane_mine_core"),
                        mineCore.getDefaultInstance()
                )
                .withCraftingPages()
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.multiblock.building"))
                .addConnectedSearch(mineCore);

        DocumentationRegistry.registerEntry(minerals, mineBuilder.build());

        Item processor = ModItems.ARCANE_PROCESSOR_CORE.get();
        DocEntryBuilder processorBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, minerals, processor)
                .withSortNum(20)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_processor.overview"),
                        Component.translatable("block.ars_arcane_matrix.arcane_processor_core"),
                        processor.getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.arcane_processor.operation"))
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.multiblock.prototype"))
                .withCraftingPages()
                .addConnectedSearch(processor)
                .addConnectedSearch(ModItems.ENCHANTED_CRYSTAL.get());
        DocumentationRegistry.registerEntry(minerals, processorBuilder.build());

        Item amplifier = ModItems.ARCANE_AMPLIFIER.get();
        DocEntryBuilder amplifierBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                source,
                amplifier
        )
                .withSortNum(40)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_amplifier.overview"),
                        Component.translatable("block.ars_arcane_matrix.arcane_amplifier"),
                        amplifier.getDefaultInstance()
                )
                .withCraftingPages(
                        ResourceLocation.fromNamespaceAndPath(
                                ArsArcaneMatrix.MOD_ID,
                                "arcane_amplifier_recycling"
                        ),
                        amplifier
                )
                .addConnectedSearch(amplifier);

        DocumentationRegistry.registerEntry(source, amplifierBuilder.build());

        Item imbuementCore = ModItems.ARCANE_IMBUEMENT_CORE.get();
        DocEntryBuilder imbuementBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                source,
                imbuementCore
        )
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable(
                                "documentation.ars_arcane_matrix.arcane_imbuement_core.overview"
                        ),
                        Component.translatable("block.ars_arcane_matrix.arcane_imbuement_core"),
                        imbuementCore.getDefaultInstance()
                )
                .withCraftingPages()
                .addConnectedSearch(imbuementCore);

        DocumentationRegistry.registerEntry(source, imbuementBuilder.build());

        Item advancedImbuement = ModItems.ADVANCED_IMBUEMENT_CHAMBER.get();
        DocEntryBuilder advancedImbuementBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, source, advancedImbuement)
                .withSortNum(50)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.advanced_imbuement_chamber.overview"),
                        Component.translatable("block.ars_arcane_matrix.advanced_imbuement_chamber"),
                        advancedImbuement.getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.advanced_imbuement_chamber.formless"))
                .addConnectedSearch(advancedImbuement)
                .addConnectedSearch(ModItems.FORMLESS_ESSENCE.get());
        DocumentationRegistry.registerEntry(source, advancedImbuementBuilder.build());

        Item generator = ModItems.SOURCE_STONE_GENERATOR.get();
        DocEntryBuilder generatorBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                source,
                generator
        )
                .withSortNum(20)
                .withIntroPageNoIncrement(
                        Component.translatable(
                                "documentation.ars_arcane_matrix.source_stone_generator.overview"
                        ),
                        Component.translatable("block.ars_arcane_matrix.source_stone_generator"),
                        generator.getDefaultInstance()
                )
                .withCraftingPages()
                .addConnectedSearch(generator);

        DocumentationRegistry.registerEntry(source, generatorBuilder.build());

        Item reactionVessel = ModItems.ARCANE_REACTION_VESSEL.get();
        DocEntryBuilder reactionBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, gettingStarted, reactionVessel)
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_reaction_vessel"),
                        Component.translatable("block.ars_arcane_matrix.arcane_reaction_vessel"),
                        reactionVessel.getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.arcane_reaction_vessel.operation"))
                .withCraftingPages()
                .addConnectedSearch(reactionVessel)
                .addConnectedSearch(ModItems.ARCANE_FLUID_RESERVOIR.get())
                .addConnectedSearch(ModItems.ARCANE_FLUID_TANK.get());
        DocumentationRegistry.registerEntry(gettingStarted, reactionBuilder.build());

        Item smelter = ModItems.ARCANE_SMELTER_CORE.get();
        DocEntryBuilder smelterBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, minerals, smelter
        )
                .withSortNum(30)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_smelter.overview"),
                        Component.translatable("block.ars_arcane_matrix.arcane_smelter_core"),
                        smelter.getDefaultInstance()
                )
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.multiblock.prototype"))
                .withCraftingPages()
                .addConnectedSearch(smelter)
                .addConnectedSearch(ModItems.ENCHANTED_ARCHWOOD_CHARCOAL.get())
                .addConnectedSearch(ModItems.CASTING_CRYSTAL.get());
        DocumentationRegistry.registerEntry(minerals, smelterBuilder.build());

        Item crusher = ModItems.ARCANE_CRUSHER_CORE.get();
        DocEntryBuilder crusherBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, minerals, crusher
        )
                .withSortNum(40)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_crusher.overview"),
                        Component.translatable("block.ars_arcane_matrix.arcane_crusher_core"),
                        crusher.getDefaultInstance()
                )
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.multiblock.prototype"))
                .withCraftingPages()
                .addConnectedSearch(crusher)
                .addConnectedSearch(ModItems.ENRICHED_MINERAL_CRYSTAL.get());
        DocumentationRegistry.registerEntry(minerals, crusherBuilder.build());

        Item metalDust = ModItems.IRON_DUST.get();
        DocEntryBuilder metalDustBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, minerals, metalDust)
                .withSortNum(45)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.metal_dusts.summary"),
                        Component.translatable("documentation.ars_arcane_matrix.metal_dusts.title"),
                        metalDust.getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.metal_dusts.operation"))
                .addConnectedSearch(ModItems.IRON_DUST.get())
                .addConnectedSearch(ModItems.COPPER_DUST.get())
                .addConnectedSearch(ModItems.GOLD_DUST.get())
                .addConnectedSearch(ModItems.ANCIENT_DEBRIS_DUST.get());
        DocumentationRegistry.registerEntry(minerals, metalDustBuilder.build());

        Item sourceboundAlloy = ModItems.SOURCEBOUND_COPPER_ALLOY.get();
        DocEntryBuilder sourceboundAlloyBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, minerals, sourceboundAlloy)
                .withSortNum(5)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.sourcebound_alloy.summary"),
                        Component.translatable("item.ars_arcane_matrix.sourcebound_copper_alloy"),
                        sourceboundAlloy.getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.sourcebound_alloy.operation"))
                .withCraftingPages()
                .withCraftingPages(
                        ResourceLocation.fromNamespaceAndPath(
                                ArsArcaneMatrix.MOD_ID, "sourcebound_copper_alloy_dust"),
                        ModItems.SOURCEBOUND_COPPER_ALLOY_DUST.get())
                .addConnectedSearch(sourceboundAlloy)
                .addConnectedSearch(ModItems.SOURCEBOUND_COPPER_ALLOY_DUST.get());
        DocumentationRegistry.registerEntry(minerals, sourceboundAlloyBuilder.build());

        Item orderTerminal = ModItems.WIXIE_ORDER_TERMINAL.get();
        DocEntryBuilder craftingNetworkBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                crafting,
                orderTerminal
        )
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.crafting_network.summary"),
                        Component.translatable("block.ars_arcane_matrix.wixie_order_terminal"),
                        orderTerminal.getDefaultInstance()
                )
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.crafting_network.overview"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.crafting_network.minimum.text"),
                        Component.translatable("documentation.ars_arcane_matrix.crafting_network.minimum.title"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.crafting_network.workflow.text"),
                        Component.translatable("documentation.ars_arcane_matrix.crafting_network.workflow.title"))
                .withCraftingPages()
                .addConnectedSearch(orderTerminal)
                .addConnectedSearch(ModItems.CRAFTING_GUIDE.get())
                .addConnectedSearch(ModItems.WIXIE_PATTERN_PROVIDER.get())
                .addConnectedSearch(ModItems.ARCANE_ORDER_PEDESTAL.get());

        DocumentationRegistry.registerEntry(crafting, craftingNetworkBuilder.build());

        DocEntryBuilder orderPedestalBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, crafting, ModItems.ARCANE_ORDER_PEDESTAL.get())
                .withSortNum(20)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.order_pedestal.summary"),
                        Component.translatable("block.ars_arcane_matrix.arcane_order_pedestal"),
                        ModItems.ARCANE_ORDER_PEDESTAL.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.order_pedestal.operation"))
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.order_pedestal.upgrades"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.ARCANE_ORDER_PEDESTAL.get());
        DocumentationRegistry.registerEntry(crafting, orderPedestalBuilder.build());

        DocEntryBuilder guideBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, crafting, ModItems.CRAFTING_GUIDE.get())
                .withSortNum(30)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.crafting_guide.summary"),
                        Component.translatable("item.ars_arcane_matrix.crafting_guide"),
                        ModItems.CRAFTING_GUIDE.get().getDefaultInstance())
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.crafting_guide.encoding.text"),
                        Component.translatable("documentation.ars_arcane_matrix.crafting_guide.encoding.title"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.crafting_guide.matching.text"),
                        Component.translatable("documentation.ars_arcane_matrix.crafting_guide.matching.title"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.CRAFTING_GUIDE.get());
        DocumentationRegistry.registerEntry(crafting, guideBuilder.build());

        DocEntryBuilder providerBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, crafting, ModItems.WIXIE_PATTERN_PROVIDER.get())
                .withSortNum(40)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.pattern_provider.summary"),
                        Component.translatable("block.ars_arcane_matrix.wixie_pattern_provider"),
                        ModItems.WIXIE_PATTERN_PROVIDER.get().getDefaultInstance())
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.pattern_provider.setup.text"),
                        Component.translatable("documentation.ars_arcane_matrix.pattern_provider.setup.title"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.pattern_provider.upgrades.text"),
                        Component.translatable("documentation.ars_arcane_matrix.pattern_provider.upgrades.title"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.WIXIE_PATTERN_PROVIDER.get());
        DocumentationRegistry.registerEntry(crafting, providerBuilder.build());

        Item sourceStoneFurnace = ModItems.SOURCE_STONE_FURNACE.get();
        DocEntryBuilder sourceStoneFurnaceBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, crafting, sourceStoneFurnace)
                .withSortNum(60)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.source_stone_furnace.summary"),
                        Component.translatable("block.ars_arcane_matrix.source_stone_furnace"),
                        sourceStoneFurnace.getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.source_stone_furnace.operation"))
                .withCraftingPages()
                .addConnectedSearch(sourceStoneFurnace)
                .addConnectedSearch(ModItems.WIXIE_ORDER_TERMINAL.get())
                .addConnectedSearch(ModItems.WIXIE_PATTERN_PROVIDER.get());
        DocumentationRegistry.registerEntry(crafting, sourceStoneFurnaceBuilder.build());

        Item amethystGolemCharm = ItemsRegistry.AMETHYST_GOLEM_CHARM.get();
        DocEntryBuilder amethystGolemBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                creatures,
                amethystGolemCharm
        )
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.amethyst_golem_enhancement.overview"),
                        Component.translatable("documentation.ars_arcane_matrix.amethyst_golem_enhancement.title"),
                        amethystGolemCharm.getDefaultInstance()
                )
                .addConnectedSearch(amethystGolemCharm);
        DocumentationRegistry.registerEntry(creatures, amethystGolemBuilder.build());

        Item whirlisprigCharm = ItemsRegistry.WHIRLISPRIG_CHARM.get();
        DocEntryBuilder ancientGroveBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, creatures, whirlisprigCharm
        )
                .withSortNum(20)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.summary"),
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.title"),
                        whirlisprigCharm.getDefaultInstance()
                )
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.whirlisprig.overview"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.earth_layout.text"),
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.earth_layout.title")
                )
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.ancient_layout.text"),
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.ancient_layout.title")
                )
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.production_layout.text"),
                        Component.translatable("documentation.ars_arcane_matrix.whirlisprig.production_layout.title")
                )
                .addConnectedSearch(whirlisprigCharm)
                .addConnectedSearch(ModItems.ANCIENT_GROVE_CATALYST.get())
                .addConnectedSearch(ItemsRegistry.EARTH_ESSENCE.get());
        DocumentationRegistry.registerEntry(creatures, ancientGroveBuilder.build());

        Item alakarkinosCharm = ItemsRegistry.ALAKARKINOS_CHARM.get();
        DocEntryBuilder expeditionBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, creatures, alakarkinosCharm)
                .withSortNum(30)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.alakarkinos_expedition.summary"),
                        Component.translatable("documentation.ars_arcane_matrix.alakarkinos_expedition.title"),
                        alakarkinosCharm.getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.alakarkinos_expedition.overview"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.alakarkinos_expedition.layout.text"),
                        Component.translatable("documentation.ars_arcane_matrix.alakarkinos_expedition.layout.title"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.alakarkinos_expedition.routes.text"),
                        Component.translatable("documentation.ars_arcane_matrix.alakarkinos_expedition.routes.title"))
                .addConnectedSearch(alakarkinosCharm);
        DocumentationRegistry.registerEntry(creatures, expeditionBuilder.build());

        Item drygmyCharm = ItemsRegistry.DRYGMY_CHARM.get();
        DocEntryBuilder drygmyBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, creatures, drygmyCharm)
                .withSortNum(40)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.drygmy_enhancement.overview"),
                        Component.translatable("documentation.ars_arcane_matrix.drygmy_enhancement.title"),
                        drygmyCharm.getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.drygmy_enhancement.weapon"))
                .addConnectedSearch(drygmyCharm);
        DocumentationRegistry.registerEntry(creatures, drygmyBuilder.build());

        DocEntryBuilder requesterBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, crafting, ModItems.AUTOMATIC_STOCK_REQUESTER.get())
                .withSortNum(50)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.stock_requester.overview"),
                        Component.translatable("block.ars_arcane_matrix.automatic_stock_requester"),
                        ModItems.AUTOMATIC_STOCK_REQUESTER.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.stock_requester.setup"))
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.stock_requester.upgrades"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.AUTOMATIC_STOCK_REQUESTER.get());
        DocumentationRegistry.registerEntry(crafting, requesterBuilder.build());

        DocEntryBuilder storageBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, storage, ModItems.ADVANCED_STORAGE_LECTERN.get())
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.storage.summary"),
                        Component.translatable("block.ars_arcane_matrix.advanced_storage_lectern"),
                        ModItems.ADVANCED_STORAGE_LECTERN.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.storage.overview"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.storage.grid.text"),
                        Component.translatable("documentation.ars_arcane_matrix.storage.grid.title"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.storage.lectern.text"),
                        Component.translatable("documentation.ars_arcane_matrix.storage.lectern.title"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.ADVANCED_STORAGE_LECTERN.get())
                .addConnectedSearch(ModItems.STORAGE_GRID_DIRECTORY.get())
                .addConnectedSearch(ModItems.GRID_EXPANSION_WAREHOUSE.get());
        DocumentationRegistry.registerEntry(storage, storageBuilder.build());

        DocEntryBuilder gridBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, storage, ModItems.STORAGE_GRID_DIRECTORY.get())
                .withSortNum(20)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.grid_directory.summary"),
                        Component.translatable("block.ars_arcane_matrix.storage_grid_directory"),
                        ModItems.STORAGE_GRID_DIRECTORY.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.grid_directory.operation"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.STORAGE_GRID_DIRECTORY.get());
        DocumentationRegistry.registerEntry(storage, gridBuilder.build());

        DocEntryBuilder expansionBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, storage, ModItems.GRID_EXPANSION_WAREHOUSE.get())
                .withSortNum(30)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.grid_expansion.summary"),
                        Component.translatable("item.ars_arcane_matrix.grid_expansion_warehouse"),
                        ModItems.GRID_EXPANSION_WAREHOUSE.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.grid_expansion.operation"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.GRID_EXPANSION_WAREHOUSE.get());
        DocumentationRegistry.registerEntry(storage, expansionBuilder.build());

        DocEntryBuilder logisticsBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, storage, ModItems.STARBUNCLE_LOGISTICS_HUB.get())
                .withSortNum(40)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.logistics.overview"),
                        Component.translatable("block.ars_arcane_matrix.starbuncle_logistics_hub"),
                        ModItems.STARBUNCLE_LOGISTICS_HUB.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.logistics.filters"))
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.logistics.upgrades"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.STARBUNCLE_LOGISTICS_HUB.get());
        DocumentationRegistry.registerEntry(storage, logisticsBuilder.build());

        Item fluidController = ModItems.ARCANE_FLUID_RESERVOIR.get();
        DocEntryBuilder fluidControllerBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, storage, fluidController)
                .withSortNum(50)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_fluid_controller.summary"),
                        Component.translatable("block.ars_arcane_matrix.arcane_fluid_reservoir"),
                        fluidController.getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.arcane_fluid_controller.operation"))
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.arcane_fluid_controller.upgrades"))
                .withCraftingPages()
                .withCraftingPages(
                        ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "fluid_capacity_upgrade"),
                        ModItems.FLUID_CAPACITY_UPGRADE.get())
                .withCraftingPages(
                        ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "fluid_range_upgrade"),
                        ModItems.FLUID_RANGE_UPGRADE.get())
                .withCraftingPages(
                        ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "fluid_speed_upgrade"),
                        ModItems.FLUID_SPEED_UPGRADE.get())
                .addConnectedSearch(fluidController)
                .addConnectedSearch(ModItems.ARCANE_FLUID_TANK.get())
                .addConnectedSearch(ModItems.FLUID_CAPACITY_UPGRADE.get())
                .addConnectedSearch(ModItems.FLUID_RANGE_UPGRADE.get())
                .addConnectedSearch(ModItems.FLUID_SPEED_UPGRADE.get())
                .addConnectedSearch(ModItems.ARCANE_REACTION_VESSEL.get());
        DocumentationRegistry.registerEntry(storage, fluidControllerBuilder.build());

        Item fluidTank = ModItems.ARCANE_FLUID_TANK.get();
        DocEntryBuilder fluidTankBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, storage, fluidTank)
                .withSortNum(55)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_fluid_tank.summary"),
                        Component.translatable("block.ars_arcane_matrix.arcane_fluid_tank"),
                        fluidTank.getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.arcane_fluid_tank.operation"))
                .withCraftingPages(
                        ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_fluid_tank"),
                        fluidTank)
                .addConnectedSearch(fluidTank)
                .addConnectedSearch(fluidController);
        DocumentationRegistry.registerEntry(storage, fluidTankBuilder.build());

        Item vacuumHopper = ModItems.ARCANE_VACUUM_HOPPER.get();
        DocEntryBuilder vacuumBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, storage, vacuumHopper)
                .withSortNum(60)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_vacuum_hopper.summary"),
                        Component.translatable("block.ars_arcane_matrix.arcane_vacuum_hopper"),
                        vacuumHopper.getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.arcane_vacuum_hopper.operation"))
                .withCraftingPages()
                .addConnectedSearch(vacuumHopper);
        DocumentationRegistry.registerEntry(storage, vacuumBuilder.build());

        DocEntryBuilder sourceNetworkBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, source, ModItems.SUPER_SOURCE_JAR_CORE.get())
                .withSortNum(60)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.source_network"),
                        Component.translatable("documentation.ars_arcane_matrix.source_network.title"),
                        ModItems.SUPER_SOURCE_JAR_CORE.get().getDefaultInstance())
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.source_network.nodes.text"),
                        Component.translatable("documentation.ars_arcane_matrix.source_network.nodes.title"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.SUPER_SOURCE_JAR_CORE.get())
                .addConnectedSearch(ModItems.INTEGRATED_SOURCE_RELAY.get())
                .addConnectedSearch(ModItems.DIMENSION_ANCHOR.get());
        DocumentationRegistry.registerEntry(source, sourceNetworkBuilder.build());

        Item arcaneSourceJar = ModItems.ARCANE_SOURCE_JAR.get();
        DocEntryBuilder arcaneSourceJarBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, source, arcaneSourceJar)
                .withSortNum(55)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_source_jar.summary"),
                        Component.translatable("block.ars_arcane_matrix.arcane_source_jar"),
                        arcaneSourceJar.getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.arcane_source_jar.operation"))
                .withCraftingPages()
                .addConnectedSearch(arcaneSourceJar);
        DocumentationRegistry.registerEntry(source, arcaneSourceJarBuilder.build());

        DocEntryBuilder relayBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, source, ModItems.INTEGRATED_SOURCE_RELAY.get())
                .withSortNum(65)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.integrated_source_relay.summary"),
                        Component.translatable("block.ars_arcane_matrix.integrated_source_relay"),
                        ModItems.INTEGRATED_SOURCE_RELAY.get().getDefaultInstance())
                .withTextPage(Component.translatable(
                        "documentation.ars_arcane_matrix.integrated_source_relay.operation"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.INTEGRATED_SOURCE_RELAY.get())
                .addConnectedSearch(ModItems.SUPER_SOURCE_JAR_CORE.get())
                .addConnectedSearch(ModItems.ADVANCED_STORAGE_LECTERN.get());
        DocumentationRegistry.registerEntry(source, relayBuilder.build());

        DocEntryBuilder anchorBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, source, ModItems.DIMENSION_ANCHOR.get())
                .withSortNum(70)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.dimension_anchor.summary"),
                        Component.translatable("block.ars_arcane_matrix.dimension_anchor"),
                        ModItems.DIMENSION_ANCHOR.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.dimension_anchor"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.DIMENSION_ANCHOR.get());
        DocumentationRegistry.registerEntry(source, anchorBuilder.build());

        DocEntryBuilder ritualBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, adventure, ModItems.RARE_CREATURE_SUMMONING_TABLET.get())
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.rare_ritual.overview"),
                        Component.translatable("item.ars_arcane_matrix.rare_creature_summoning_tablet"),
                        ModItems.RARE_CREATURE_SUMMONING_TABLET.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.rare_ritual.operation"))
                .withCraftingPages()
                .addConnectedSearch(ModItems.RARE_CREATURE_SUMMONING_TABLET.get());
        DocumentationRegistry.registerEntry(adventure, ritualBuilder.build());

        Item captureGlyph = EffectCapture.INSTANCE.getGlyph();
        DocEntryBuilder captureGlyphBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, adventure, captureGlyph)
                .withSortNum(5)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.capture_glyph.summary"),
                        Component.translatable("documentation.ars_arcane_matrix.capture_glyph.title"),
                        captureGlyph.getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.capture_glyph.operation"))
                .withCraftingPages()
                .addConnectedSearch(captureGlyph)
                .addConnectedSearch(BlockRegistry.MOB_JAR.asItem());
        DocumentationRegistry.registerEntry(adventure, captureGlyphBuilder.build());

        DocEntryBuilder troubleshooting = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID, misc, "troubleshooting")
                .withIcon(ModItems.ARCANE_AMPLIFIER.get())
                .withTitle(Component.translatable("documentation.ars_arcane_matrix.troubleshooting.title"))
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.troubleshooting.summary"),
                        Component.translatable("documentation.ars_arcane_matrix.troubleshooting.title"),
                        ModItems.ARCANE_AMPLIFIER.get().getDefaultInstance())
                .withTextPage(Component.translatable("documentation.ars_arcane_matrix.troubleshooting.overview"))
                .withHeaderPage(
                        Component.translatable("documentation.ars_arcane_matrix.troubleshooting.links.text"),
                        Component.translatable("documentation.ars_arcane_matrix.troubleshooting.links.title"));
        DocumentationRegistry.registerEntry(misc, troubleshooting.build());

        if (FeatureFlags.ARCANE_ARENA) {
            Item drygmyArena = ModItems.DRYGMY_ARENA.get();
            DocEntryBuilder arenaBuilder = new DocEntryBuilder(
                    ArsArcaneMatrix.MOD_ID,
                    adventure,
                    drygmyArena
            )
                    .withSortNum(20)
                    .withIntroPageNoIncrement(
                            Component.translatable("documentation.ars_arcane_matrix.drygmy_arena.overview"),
                            Component.translatable("block.ars_arcane_matrix.drygmy_arena"),
                            drygmyArena.getDefaultInstance()
                    )
                    .withCraftingPages()
                    .addConnectedSearch(drygmyArena);

            DocumentationRegistry.registerEntry(adventure, arenaBuilder.build());
        }
    }

    private static DocCategory sub(DocCategory parent, String path, Item icon, int order) {
        DocCategory category = new DocCategory(
                ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, path),
                icon.getDefaultInstance(), order);
        parent.addSubCategory(category);
        return category;
    }
}
