package dev.arsmatrix.data;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves c:ores/material and c:raw_materials/material into c:dusts/material. */
public final class CrusherRecipeResolver {
    private CrusherRecipeResolver() {}

    public static Optional<CrusherRecipeRule> find(ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        if (input.is(Items.ANCIENT_DEBRIS)) return Optional.of(ancientDebrisRule());
        List<ResourceLocation> candidates = input.getTags().map(TagKey::location)
                .filter(CrusherRecipeResolver::isSupportedInputTag)
                .sorted(Comparator.comparingInt(CrusherRecipeResolver::tagPriority)
                        .thenComparing(ResourceLocation::toString))
                .toList();
        for (ResourceLocation tag : candidates) {
            Optional<Item> dust = preferredDust(material(tag), input.getItem());
            if (dust.isPresent()) return Optional.of(createRule(input.getItem(), tag, dust.get()));
        }
        return Optional.empty();
    }

    public static List<CrusherRecipeRule> allRules() {
        Map<ResourceLocation, CrusherRecipeRule> rules = new LinkedHashMap<>();
        CrusherRecipeRule ancientDebris = ancientDebrisRule();
        rules.put(ancientDebris.id(), ancientDebris);
        BuiltInRegistries.ITEM.getTags()
                .filter(pair -> isSupportedInputTag(pair.getFirst().location()))
                .sorted(Comparator.comparing(pair -> pair.getFirst().location().toString()))
                .forEach(pair -> pair.getSecond().stream().map(Holder::value).forEach(input -> {
                    Optional<Item> dust = preferredDust(material(pair.getFirst().location()), input);
                    if (dust.isEmpty()) return;
                    CrusherRecipeRule rule = createRule(input, pair.getFirst().location(), dust.get());
                    rules.putIfAbsent(rule.id(), rule);
                }));
        return List.copyOf(rules.values());
    }

    private static CrusherRecipeRule ancientDebrisRule() {
        return new CrusherRecipeRule(
                ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "special/ancient_debris"),
                new ItemStack(Items.ANCIENT_DEBRIS), new ItemStack(ModItems.ANCIENT_DEBRIS_DUST.get()), 2, 3);
    }

    private static CrusherRecipeRule createRule(Item input, ResourceLocation inputTag, Item dust) {
        boolean raw = inputTag.getPath().startsWith("raw_materials/");
        ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(input);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID,
                "auto_crushing/" + (raw ? "raw/" : "ore/")
                        + inputId.getNamespace() + "/" + inputId.getPath());
        return new CrusherRecipeRule(id, new ItemStack(input), new ItemStack(dust),
                raw ? 2 : 1, raw ? 3 : 2);
    }

    private static Optional<Item> preferredDust(String material, Item input) {
        TagKey<Item> ingotTag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "ingots/" + material));
        if (BuiltInRegistries.ITEM.getTag(ingotTag).isEmpty()) {
            return Optional.empty();
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "dusts/" + material));
        List<Item> options = BuiltInRegistries.ITEM.getTag(tag).stream()
                .flatMap(named -> named.stream().map(Holder::value))
                .filter(item -> item != Items.AIR)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
        String inputNamespace = BuiltInRegistries.ITEM.getKey(input).getNamespace();
        return options.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace()
                        .equals(ArsArcaneMatrix.MOD_ID)).findFirst()
                .or(() -> options.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace()
                        .equals(inputNamespace)).findFirst())
                .or(() -> options.stream().findFirst());
    }

    private static boolean isSupportedInputTag(ResourceLocation id) {
        return id.getNamespace().equals("c")
                && (id.getPath().startsWith("ores/") || id.getPath().startsWith("raw_materials/"));
    }

    private static int tagPriority(ResourceLocation id) {
        return id.getPath().startsWith("raw_materials/") ? 0 : 1;
    }

    private static String material(ResourceLocation id) {
        int slash = id.getPath().indexOf('/');
        return slash < 0 ? id.getPath() : id.getPath().substring(slash + 1);
    }
}
