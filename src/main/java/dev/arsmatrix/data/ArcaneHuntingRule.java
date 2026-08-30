package dev.arsmatrix.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Data-driven special reward produced for one Mob Jar target. */
public record ArcaneHuntingRule(
        ResourceLocation id,
        ResourceLocation entityId,
        int pointCost,
        List<Result> results,
        boolean enabled
) {
    public boolean matches(EntityType<?> type) {
        return entityId.equals(EntityType.getKey(type));
    }

    public List<ItemStack> createOutputs() {
        return results.stream().map(Result::createStack).filter(stack -> !stack.isEmpty()).toList();
    }

    public record Result(ResourceLocation itemId, int count) {
        public ItemStack createStack() {
            return BuiltInRegistries.ITEM.getOptional(itemId)
                    .map(item -> new ItemStack(item, count))
                    .orElse(ItemStack.EMPTY);
        }
    }
}
