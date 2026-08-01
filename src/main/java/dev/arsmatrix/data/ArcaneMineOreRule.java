package dev.arsmatrix.data;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
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
    public static final TagKey<Item> DEFAULT_OUTPUT_BLACKLIST = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "arcane_mine_output_blacklist")
    );

    public static boolean isAllowedOutput(Item item) {
        return item != Items.AIR && !item.builtInRegistryHolder().is(DEFAULT_OUTPUT_BLACKLIST);
    }

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
                    .filter(ArcaneMineOreRule::isAllowedOutput)
                    .toList();
            if (candidates.isEmpty()) {
                return ItemStack.EMPTY;
            }
            item = candidates.get(random.nextInt(candidates.size()));
        } else {
            item = BuiltInRegistries.ITEM.getOptional(output).orElse(Items.AIR);
        }
        return isAllowedOutput(item) ? new ItemStack(item, outputCount) : ItemStack.EMPTY;
    }

    public boolean matchesTuningSample(ItemStack sample) {
        if (sample.isEmpty()) {
            return false;
        }
        if (!isAllowedOutput(sample.getItem())) {
            return false;
        }
        if (outputIsTag) {
            return sample.is(TagKey.create(Registries.ITEM, output));
        }
        return BuiltInRegistries.ITEM.getKey(sample.getItem()).equals(output);
    }
}
