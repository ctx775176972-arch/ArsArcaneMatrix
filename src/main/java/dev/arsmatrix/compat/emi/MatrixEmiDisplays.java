package dev.arsmatrix.compat.emi;

import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.setup.registry.*;
import dev.arsmatrix.FeatureFlags;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.data.*;
import dev.arsmatrix.registry.*;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Unbreakable;
import java.util.*;

/** Mirrors every non-vanilla JEI production category without requiring JEI/JEMI. */
final class MatrixEmiDisplays {
    private MatrixEmiDisplays() {}
    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", path);
    }
    private static EmiRecipeCategory category(EmiRegistry registry, String name, ItemStack icon) {
        EmiRecipeCategory category = new EmiRecipeCategory(id(name), EmiStack.of(icon)) {
            @Override public Component getName() {
                return Component.translatable("jei.ars_arcane_matrix." + name);
            }
        };
        // Prefer our native category if the optional JEI bridge is present too.
        registry.removeRecipes(recipe -> recipe.getCategory().getId().equals(category.getId())
                && !(recipe instanceof MatrixDisplayEmiRecipe));
        registry.addCategory(category);
        registry.addWorkstation(category, EmiStack.of(icon));
        return category;
    }
    private static void add(EmiRegistry registry, EmiRecipeCategory category, ResourceLocation ruleId,
            List<EmiIngredient> inputs, List<EmiIngredient> catalysts,
            List<ItemStack> outputs, List<Component> details) {
        if (outputs.isEmpty()) return;
        registry.addRecipe(new MatrixDisplayEmiRecipe(category,
                id("emi/" + category.getId().getPath() + "/" + ruleId.getNamespace() + "/" + ruleId.getPath()),
                List.copyOf(inputs), List.copyOf(catalysts),
                outputs.stream().filter(s -> !s.isEmpty()).map(EmiStack::of).toList(), List.copyOf(details)));
    }
    private static List<ItemStack> tagged(ResourceLocation tag, int count) {
        return BuiltInRegistries.ITEM.getTag(ItemTags.create(tag)).stream()
                .flatMap(set -> set.stream()).map(holder -> new ItemStack(holder.value(), count)).toList();
    }
    private static List<ItemStack> mineMaterials(ArcaneMineOreRule rule) {
        List<ItemStack> result = new ArrayList<>();
        String[] names = {"sourcestone", "source_gem", "source_gem_block"};
        int[] points = {MatrixConfig.MINE_SOURCESTONE_POINTS.get(), MatrixConfig.MINE_SOURCE_GEM_POINTS.get(),
                MatrixConfig.MINE_SOURCE_GEM_BLOCK_POINTS.get()};
        for (int i = 0; i < names.length; i++) result.addAll(tagged(id("arcane_mine_material_" + names[i]),
                Math.max(1, Math.ceilDiv(rule.materialPoints(), Math.max(1, points[i])))));
        return result;
    }
    static void register(EmiRegistry registry) {
        registry.addRecipe(new dev.emi.emi.api.recipe.EmiInfoRecipe(
                List.of(EmiStack.of(ModItems.ENCHANTED_CRYSTAL.get())),
                List.of(Component.translatable("jei.ars_arcane_matrix.enchanted_crystal.source"),
                        Component.translatable("jei.ars_arcane_matrix.enchanted_crystal.pity")), id("emi/info/enchanted_crystal")));
        registry.addRecipe(new dev.emi.emi.api.recipe.EmiInfoRecipe(
                List.of(EmiStack.of(ModItems.CASTING_CRYSTAL.get())),
                List.of(Component.translatable("jei.ars_arcane_matrix.casting_crystal.source"),
                        Component.translatable("jei.ars_arcane_matrix.casting_crystal.pity")), id("emi/info/casting_crystal")));
        registry.addRecipe(new dev.emi.emi.api.recipe.EmiInfoRecipe(
                List.of(EmiStack.of(ModItems.ENRICHED_MINERAL_CRYSTAL.get())),
                List.of(Component.translatable("jei.ars_arcane_matrix.enriched_mineral_crystal.source")),
                id("emi/info/enriched_mineral_crystal")));
        var mine = category(registry, "arcane_mining", ModBlocks.ARCANE_MINE_CORE.toStack());
        for (var rule : ArcaneMineOreManager.allRules()) {
            List<ItemStack> outputs = rule.outputIsTag() ? tagged(rule.output(), rule.outputCount())
                    : List.of(new ItemStack(BuiltInRegistries.ITEM.get(rule.output()), rule.outputCount()));
            outputs = outputs.stream().filter(s -> ArcaneMineOreRule.isAllowedOutput(s.getItem())).toList();
            var details = new ArrayList<Component>();
            details.add(Component.translatable("jei.ars_arcane_matrix.arcane_mining.requirements",
                    rule.requiredLayers(), rule.materialPoints()));
            details.add(Component.translatable("jei.ars_arcane_matrix.arcane_mining.source", rule.sourceCost()));
            if (MatrixConfig.MINE_REQUIRE_MODDED_ORE_WHITELIST.get() && outputs.stream().anyMatch(s ->
                    !BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("minecraft"))) {
                details.add(Component.translatable("jei.ars_arcane_matrix.arcane_mining.whitelist_required"));
            }
            add(registry, mine, rule.id(), List.of(MatrixDisplayEmiRecipe.alternatives(mineMaterials(rule))),
                    List.of(), outputs, details);
        }
        var generator = category(registry, "source_stone_generation", ModBlocks.SOURCE_STONE_GENERATOR.toStack());
        for (var rule : SourceStoneGeneratorRecipeManager.allRecipes()) {
            add(registry, generator, rule.id(), List.of(), rule.catalysts().stream()
                    .map(c -> MatrixDisplayEmiRecipe.alternatives(c.displayStacks())).toList(),
                    List.of(rule.createOutput()), List.of(Component.translatable(
                            "jei.ars_arcane_matrix.source_stone_generation.source_cost", rule.processingCost())));
        }
        var expedition = category(registry, "alakarkinos_expedition", new ItemStack(ItemsRegistry.ALAKARKINOS_CHARM.get()));
        for (var rule : AlakarkinosExpeditionManager.allRules()) {
            List<EmiIngredient> catalysts = new ArrayList<>();
            catalysts.add(EmiStack.of(Items.BRUSH));
            if (rule.requiresProof()) catalysts.add(EmiStack.of(rule.proofStack()));
            List<Component> details = new ArrayList<>();
            details.add(Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition.route." + rule.id().getPath()));
            details.add(Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition.materials",
                    rule.inputs().stream().mapToInt(AlakarkinosExpeditionRule.IngredientCost::count).sum()));
            details.add(Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition.time", Math.ceilDiv(rule.workTicks(), 20)));
            details.add(Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition.source", rule.sourceCost()));
            if (!rule.isFixedOutput()) details.add(Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition.examples"));
            for (var table : rule.lootTables()) {
                String key = "jei.ars_arcane_matrix.expedition_chest." + table.id().getPath().replace('/', '.');
                details.add(net.minecraft.client.resources.language.I18n.exists(key)
                        ? Component.translatable(key) : Component.literal(table.id().toString()));
            }
            add(registry, expedition, rule.id(), rule.inputDisplayStacks().stream()
                    .map(MatrixDisplayEmiRecipe::alternatives).toList(), catalysts, rule.displayOutputStacks(), details);
        }
        var crusher = category(registry, "arcane_crushing", ModBlocks.ARCANE_CRUSHER_CORE.toStack());
        for (var rule : CrusherRecipeResolver.allRules()) {
            List<Component> details = new ArrayList<>(List.of(
                    Component.translatable("jei.ars_arcane_matrix.arcane_crushing.base.tooltip"),
                    Component.translatable("jei.ars_arcane_matrix.arcane_crushing.air_bonus.tooltip"),
                    Component.translatable("jei.ars_arcane_matrix.arcane_crushing.water_bonus.tooltip")));
            if (rule.input().is(Items.ANCIENT_DEBRIS)) details.add(Component.translatable(
                    "jei.ars_arcane_matrix.arcane_crushing.water_bonus.ancient_debris"));
            add(registry, crusher, rule.id(), List.of(EmiStack.of(rule.input())), List.of(),
                    List.of(rule.baseOutput(), rule.airBonusOutput(), new ItemStack(ModItems.ENRICHED_MINERAL_CRYSTAL.get())),
                    details);
        }
        if (FeatureFlags.ARCANE_ARENA) {
            var hunting = category(registry, "arcane_hunting", ModBlocks.DRYGMY_ARENA.toStack());
            for (var rule : ArcaneHuntingRuleManager.allRules()) {
                ItemStack jar = new ItemStack(BlockRegistry.MOB_JAR.get());
                CompoundTag entity = new CompoundTag();
                entity.putString("id", rule.entityId().toString());
                jar.set(DataComponentRegistry.MOB_JAR.get(), new MobJarData(entity, new CompoundTag()));
                add(registry, hunting, rule.id(), List.of(MatrixDisplayEmiRecipe.alternatives(List.of(
                        new ItemStack(ItemsRegistry.CONJURATION_ESSENCE.get(), Math.ceilDiv(rule.pointCost(), 10)),
                        new ItemStack(ModItems.CONDENSED_SUMMONING_CATALYST.get(), Math.ceilDiv(rule.pointCost(), 100))))),
                        List.of(EmiStack.of(jar)), rule.createOutputs(),
                        List.of(Component.translatable("jei.ars_arcane_matrix.arcane_hunting.time",
                                Math.ceilDiv(MatrixConfig.DRYGMY_ARENA_CYCLE_TICKS.get(), 20))));
            }
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var upgrade = category(registry, "arcane_machine_upgrade", ModBlocks.ARCANE_ORDER_PEDESTAL.toStack());
        registry.addWorkstation(upgrade, EmiStack.of(BlockRegistry.ENCHANTING_APP_BLOCK.get()));
        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ARCANE_MACHINE_UPGRADE_TYPE.get())) {
            var recipe = holder.value();
            List<EmiIngredient> inputs = new ArrayList<>();
            inputs.add(EmiIngredient.of(recipe.reagent()));
            recipe.pedestalItems().forEach(i -> inputs.add(EmiIngredient.of(i)));
            add(registry, upgrade, holder.id(), inputs, List.of(), List.of(recipe.result()),
                    List.of(Component.translatable("ars_nouveau.source", recipe.sourceCost())));
        }
        var unbreakable = category(registry, "unbreakable_refinement", new ItemStack(ModItems.SOURCEBOUND_COPPER_ALLOY.get()));
        registry.addWorkstation(unbreakable, EmiStack.of(BlockRegistry.ENCHANTING_APP_BLOCK.get()));
        level.getRecipeManager().byKey(id("unbreakable_item")).ifPresent(holder -> {
            if (!(holder.value() instanceof dev.arsmatrix.recipe.UnbreakableApparatusRecipe recipe)) return;
            // Keep each equipment variant paired with its own before/after stack.
            int index = 0;
            for (ItemStack input : recipe.reagent().getItems()) {
                ItemStack output = input.copy();
                output.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
                List<EmiIngredient> inputs = new ArrayList<>();
                inputs.add(EmiStack.of(input));
                recipe.pedestalItems().forEach(i -> inputs.add(EmiIngredient.of(i)));
                add(registry, unbreakable, id("unbreakable_item/" + index++), inputs, List.of(),
                        List.of(output), List.of(Component.translatable("ars_nouveau.source", recipe.sourceCost())));
            }
        });
    }
}
