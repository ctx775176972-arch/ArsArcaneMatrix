package dev.arsmatrix.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Data-driven pedestal recipe used by the Source Stone Generator. */
public record SourceStoneGeneratorRule(
        ResourceLocation id,
        List<SourceStoneGeneratorCatalyst> catalysts,
        ResourceLocation output,
        int outputCount,
        int processingCost,
        boolean enabled
) {

    public static final TagKey<Item> ALLOWED_OUTPUTS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "source_stone_generator_outputs")
    );

    public SourceStoneGeneratorRule {
        catalysts = List.copyOf(catalysts);
    }

    public int catalystItemCount() {
        return catalysts.stream().mapToInt(SourceStoneGeneratorCatalyst::count).sum();
    }

    /**
     * Pedestal matching is unordered and exact. Empty pedestals are ignored;
     * every non-empty pedestal item must be claimed by one configured requirement.
     */
    public boolean matches(List<ItemStack> pedestalStacks) {
        int actualCount = pedestalStacks.stream().mapToInt(ItemStack::getCount).sum();
        if (actualCount != catalystItemCount()) {
            return false;
        }
        List<ItemStack> actualItems = new ArrayList<>();
        for (ItemStack stack : pedestalStacks) {
            for (int count = 0; count < stack.getCount(); count++) {
                actualItems.add(stack.copyWithCount(1));
            }
        }
        List<SourceStoneGeneratorCatalyst> requirements = new ArrayList<>();
        for (SourceStoneGeneratorCatalyst catalyst : catalysts) {
            for (int count = 0; count < catalyst.count(); count++) {
                requirements.add(new SourceStoneGeneratorCatalyst(
                        catalyst.ingredient(),
                        catalyst.tag(),
                        1
                ));
            }
        }
        return matchesExactly(actualItems, requirements, 0, new boolean[requirements.size()]);
    }

    private static boolean matchesExactly(
            List<ItemStack> actualItems,
            List<SourceStoneGeneratorCatalyst> requirements,
            int actualIndex,
            boolean[] usedRequirements
    ) {
        if (actualIndex >= actualItems.size()) {
            return true;
        }
        ItemStack actual = actualItems.get(actualIndex);
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            if (usedRequirements[requirementIndex]
                    || !requirements.get(requirementIndex).matches(actual)) {
                continue;
            }
            usedRequirements[requirementIndex] = true;
            if (matchesExactly(actualItems, requirements, actualIndex + 1, usedRequirements)) {
                return true;
            }
            usedRequirements[requirementIndex] = false;
        }
        return false;
    }

    public ItemStack createOutput() {
        Item item = BuiltInRegistries.ITEM.getOptional(output).orElse(Items.AIR);
        return item == Items.AIR
                || !(item instanceof BlockItem)
                || !item.builtInRegistryHolder().is(ALLOWED_OUTPUTS)
                ? ItemStack.EMPTY
                : new ItemStack(item, outputCount);
    }
}
