package dev.arsmatrix.data;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public record ArcaneReactionIngredient(ResourceLocation id, boolean tag, int count) {
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return tag ? stack.is(TagKey.create(Registries.ITEM, id))
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    public List<ItemStack> displayStacks() {
        if (!tag) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            return item == Items.AIR ? List.of() : List.of(new ItemStack(item, count));
        }
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).stream()
                .flatMap(set -> set.stream().map(Holder::value))
                .filter(item -> item != Items.AIR)
                .map(item -> new ItemStack(item, count)).toList();
    }
}
