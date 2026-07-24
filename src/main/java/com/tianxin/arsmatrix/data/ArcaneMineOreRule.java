package com.tianxin.arsmatrix.data;

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

/** One data-driven Arcane Mine output rule. */
public record ArcaneMineOreRule(
        ResourceLocation id,
        ResourceLocation output,
        boolean outputIsTag,
        int outputCount,
        int requiredLayers,
        int materialPoints,
        int sourceCost,
        int weight,
        boolean enabled
) {

    public String outputKey() {
        return (outputIsTag ? "#" : "") + output;
    }

    public ItemStack createOutput(RandomSource random) {
        Item item;
        if (outputIsTag) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, output);
            List<Item> candidates = BuiltInRegistries.ITEM.getTag(tag)
                    .stream()
                    .flatMap(named -> named.stream().map(Holder::value))
                    .filter(candidate -> candidate != Items.AIR)
                    .toList();
            if (candidates.isEmpty()) {
                return ItemStack.EMPTY;
            }
            item = candidates.get(random.nextInt(candidates.size()));
        } else {
            item = BuiltInRegistries.ITEM.getOptional(output).orElse(Items.AIR);
        }
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, outputCount);
    }
}
