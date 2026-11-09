package dev.arsmatrix.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Informational production displays, not executable crafting-tree steps. */
record MatrixDisplayEmiRecipe(EmiRecipeCategory category, ResourceLocation id,
        List<EmiIngredient> inputs, List<EmiIngredient> catalysts,
        List<EmiStack> outputs, List<Component> details) implements EmiRecipe {
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public ResourceLocation getId() { return id; }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiIngredient> getCatalysts() { return catalysts; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public boolean supportsRecipeTree() { return false; }
    @Override public int getDisplayWidth() { return 172; }
    private int slotHeight() {
        return Math.max(1, Math.max(Math.ceilDiv(inputs.size() + catalysts.size(), 4),
                Math.ceilDiv(outputs.size(), 3))) * 20;
    }
    @Override public int getDisplayHeight() {
        return slotHeight() + 8 + details.stream().mapToInt(line ->
                Minecraft.getInstance().font.split(line, 168).size() * 10).sum();
    }
    @Override public void addWidgets(WidgetHolder widgets) {
        int index = 0;
        for (EmiIngredient input : inputs) {
            widgets.add(new CompactAmountSlot(input, index % 4 * 19, index / 4 * 20));
            index++;
        }
        for (EmiIngredient catalyst : catalysts) {
            widgets.add(new CompactAmountSlot(catalyst, index % 4 * 19, index / 4 * 20))
                    .appendTooltip(Component.translatable("emi.ars_arcane_matrix.catalyst"));
            index++;
        }
        widgets.addText(Component.literal("→"), 82, 5, 0x74509A, false);
        for (int i = 0; i < outputs.size(); i++) {
            widgets.add(new CompactAmountSlot(outputs.get(i), 110 + i % 3 * 20, i / 3 * 20)).recipeContext(this);
        }
        int y = slotHeight() + 6;
        for (Component detail : details) {
            for (var line : Minecraft.getInstance().font.split(detail, 168)) {
                widgets.addText(line, 2, y, 0x404040, false);
                y += 10;
            }
        }
    }
    static EmiIngredient alternatives(List<ItemStack> stacks) {
        var nonEmpty = stacks.stream().filter(s -> !s.isEmpty()).toList();
        if (!nonEmpty.isEmpty() && nonEmpty.stream().allMatch(s -> s.getCount() == nonEmpty.getFirst().getCount())) {
            return EmiIngredient.of(nonEmpty.stream().map(s -> EmiStack.of(s.copyWithCount(1))).toList(),
                    nonEmpty.getFirst().getCount());
        }
        return EmiIngredient.of(stacks.stream().filter(s -> !s.isEmpty()).map(EmiStack::of).toList());
    }
}
