package dev.arsmatrix.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public record ArcaneReactionRule(ResourceLocation id, List<ArcaneReactionIngredient> ingredients,
        ResourceLocation inputFluid, int inputFluidAmount, ResourceLocation outputItem,
        int outputItemCount, ResourceLocation outputFluid, int outputFluidAmount,
        int sourceCost, int processingTicks, boolean enabled) {

    public ArcaneReactionRule { ingredients = List.copyOf(ingredients); }

    public boolean matches(List<ItemStack> stacks, FluidStack fluid) {
        if (inputFluidAmount > 0) {
            Fluid required = BuiltInRegistries.FLUID.getOptional(inputFluid).orElse(Fluids.EMPTY);
            if (required == Fluids.EMPTY || fluid.getFluid() != required || fluid.getAmount() < inputFluidAmount) return false;
        }
        List<ItemStack> remaining = stacks.stream().map(ItemStack::copy).toList();
        for (ArcaneReactionIngredient ingredient : ingredients) {
            int needed = ingredient.count();
            for (ItemStack stack : remaining) {
                if (!ingredient.matches(stack)) continue;
                int used = Math.min(needed, stack.getCount());
                stack.shrink(used);
                needed -= used;
                if (needed == 0) break;
            }
            if (needed > 0) return false;
        }
        for (ItemStack stack : remaining) {
            if (!stack.isEmpty() && ingredients.stream().noneMatch(i -> i.matches(stack))) return false;
        }
        return true;
    }

    public void consumeItems(List<ItemStack> stacks) {
        for (ArcaneReactionIngredient ingredient : ingredients) {
            int needed = ingredient.count();
            for (ItemStack stack : stacks) {
                if (!ingredient.matches(stack)) continue;
                int used = Math.min(needed, stack.getCount());
                stack.shrink(used);
                needed -= used;
                if (needed == 0) break;
            }
        }
    }

    public ItemStack createItemOutput() {
        if (outputItemCount <= 0) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(outputItem).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, outputItemCount);
    }

    public FluidStack createFluidOutput() {
        if (outputFluidAmount <= 0) return FluidStack.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(outputFluid).orElse(Fluids.EMPTY);
        return fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, outputFluidAmount);
    }
}
