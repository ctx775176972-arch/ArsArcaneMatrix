package dev.arsmatrix.data;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;

/** One container-fed bulk expedition performed by a native Alakarkinos. */
public record AlakarkinosExpeditionRule(
        ResourceLocation id,
        List<IngredientCost> inputs,
        ResourceLocation proof,
        int workTicks,
        int sourceCost,
        List<LootTableChoice> lootTables,
        ResourceLocation fixedOutput,
        int fixedOutputCount,
        List<DisplayResult> displayResults,
        Set<ResourceLocation> excludedOutputs,
        boolean enabled
) {
    public AlakarkinosExpeditionRule {
        inputs = List.copyOf(inputs);
        lootTables = List.copyOf(lootTables);
        displayResults = List.copyOf(displayResults);
        excludedOutputs = Set.copyOf(excludedOutputs);
    }

    public boolean requiresProof() { return proof != null; }
    public boolean isFixedOutput() { return fixedOutput != null; }

    /** Selects the real chest table before generation so table-id loot modifiers see it. */
    public ResourceLocation chooseLootTable(RandomSource random) {
        if (lootTables.isEmpty()) return null;
        int totalWeight = 0;
        for (LootTableChoice choice : lootTables) {
            totalWeight = Math.addExact(totalWeight, choice.weight());
        }
        int selected = random.nextInt(totalWeight);
        for (LootTableChoice choice : lootTables) {
            selected -= choice.weight();
            if (selected < 0) return choice.id();
        }
        return lootTables.getLast().id();
    }

    public List<List<ItemStack>> inputDisplayStacks() {
        return inputs.stream().map(IngredientCost::displayStacks).toList();
    }

    public ItemStack proofStack() {
        if (proof == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(proof).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public List<ItemStack> displayOutputStacks() {
        if (isFixedOutput()) {
            Item item = BuiltInRegistries.ITEM.getOptional(fixedOutput).orElse(Items.AIR);
            return item == Items.AIR ? List.of() : List.of(new ItemStack(item, fixedOutputCount));
        }
        return displayResults.stream().map(DisplayResult::createStack)
                .filter(stack -> !stack.isEmpty()).toList();
    }

    public record DisplayResult(ResourceLocation item, int count) {
        public ItemStack createStack() {
            Item resolved = BuiltInRegistries.ITEM.getOptional(item).orElse(Items.AIR);
            return resolved == Items.AIR ? ItemStack.EMPTY : new ItemStack(resolved, count);
        }
    }

    public record LootTableChoice(ResourceLocation id, int weight) {}

    public record IngredientCost(ResourceLocation value, boolean tag, int count) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (tag) return stack.is(TagKey.create(Registries.ITEM, value));
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(value);
        }

        public List<ItemStack> displayStacks() {
            if (!tag) {
                Item item = BuiltInRegistries.ITEM.getOptional(value).orElse(Items.AIR);
                return item == Items.AIR ? List.of() : List.of(new ItemStack(item, count));
            }
            return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, value)).stream()
                    .flatMap(named -> named.stream().map(Holder::value))
                    .filter(item -> item != Items.AIR)
                    .map(item -> new ItemStack(item, count))
                    .toList();
        }
    }
}
