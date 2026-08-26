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

/** One exact, non-consumed pedestal requirement for a Source Stone Generator recipe. */
public record SourceStoneGeneratorCatalyst(
        ResourceLocation ingredient,
        boolean tag,
        int count
) {

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag) {
            return stack.is(TagKey.create(Registries.ITEM, ingredient));
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ingredient);
    }

    public List<ItemStack> displayStacks() {
        if (!tag) {
            Item item = BuiltInRegistries.ITEM.getOptional(ingredient).orElse(Items.AIR);
            return item == Items.AIR ? List.of() : List.of(new ItemStack(item, count));
        }
        TagKey<Item> key = TagKey.create(Registries.ITEM, ingredient);
        return BuiltInRegistries.ITEM.getTag(key).stream()
                .flatMap(named -> named.stream().map(Holder::value))
                .filter(item -> item != Items.AIR)
                .map(item -> new ItemStack(item, count))
                .toList();
    }
}
