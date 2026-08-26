package dev.arsmatrix.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.config.MatrixConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads data/&lt;namespace&gt;/source_stone_generator/*.json. */
public final class SourceStoneGeneratorRecipeManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SourceStoneGeneratorRecipeManager INSTANCE =
            new SourceStoneGeneratorRecipeManager();
    private static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID,
            "default_cobblestone"
    );

    private volatile Map<ResourceLocation, SourceStoneGeneratorRule> recipes = Map.of();

    private SourceStoneGeneratorRecipeManager() {
        super(GSON, "source_stone_generator");
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> entries,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, SourceStoneGeneratorRule> loaded = new LinkedHashMap<>();
        entries.forEach((id, json) -> {
            try {
                SourceStoneGeneratorRule rule = parse(id, GsonHelper.convertToJsonObject(json, id.toString()));
                if (rule.enabled()) {
                    loaded.put(id, rule);
                }
            } catch (RuntimeException exception) {
                ArsArcaneMatrix.LOGGER.error("Could not load Source Stone Generator recipe {}", id, exception);
            }
        });
        recipes = Map.copyOf(loaded);
        ArsArcaneMatrix.LOGGER.info("Loaded {} Source Stone Generator recipes.", recipes.size());
    }

    private static SourceStoneGeneratorRule parse(ResourceLocation id, JsonObject json) {
        List<SourceStoneGeneratorCatalyst> catalysts = new ArrayList<>();
        JsonArray entries = GsonHelper.getAsJsonArray(json, "pedestal_items", new JsonArray());
        if (entries.size() > 8) {
            throw new IllegalArgumentException("pedestal_items may contain at most eight entries");
        }
        for (JsonElement element : entries) {
            JsonObject catalyst = GsonHelper.convertToJsonObject(element, "pedestal item");
            boolean hasItem = catalyst.has("item");
            boolean hasTag = catalyst.has("tag");
            if (hasItem == hasTag) {
                throw new IllegalArgumentException("pedestal item must contain exactly one of 'item' or 'tag'");
            }
            int count = positive(GsonHelper.getAsInt(catalyst, "count", 1), "pedestal item count");
            catalysts.add(new SourceStoneGeneratorCatalyst(
                    ResourceLocation.parse(GsonHelper.getAsString(catalyst, hasTag ? "tag" : "item")),
                    hasTag,
                    count
            ));
        }
        int catalystCount = catalysts.stream().mapToInt(SourceStoneGeneratorCatalyst::count).sum();
        if (catalystCount > 8) {
            throw new IllegalArgumentException("pedestal_items may require at most eight total items");
        }

        JsonObject result = GsonHelper.getAsJsonObject(json, "result");
        String resultId = result.has("id")
                ? GsonHelper.getAsString(result, "id")
                : GsonHelper.getAsString(result, "item");
        int outputCount = positive(GsonHelper.getAsInt(result, "count", 64), "result.count");
        int processingCost = positive(
                GsonHelper.getAsInt(json, "processing_cost", 200),
                "processing_cost"
        );
        boolean enabled = GsonHelper.getAsBoolean(json, "enabled", true);
        return new SourceStoneGeneratorRule(
                id,
                catalysts,
                ResourceLocation.parse(resultId),
                outputCount,
                processingCost,
                enabled
        );
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public static SourceStoneGeneratorRule findMatch(List<ItemStack> pedestalStacks) {
        return INSTANCE.recipes.values().stream()
                .filter(SourceStoneGeneratorRule::enabled)
                .filter(rule -> !rule.createOutput().isEmpty())
                .filter(rule -> rule.matches(pedestalStacks))
                .sorted(Comparator.comparingInt(SourceStoneGeneratorRule::catalystItemCount).reversed()
                        .thenComparing(rule -> rule.id().toString()))
                .findFirst()
                .orElseGet(SourceStoneGeneratorRecipeManager::defaultRule);
    }

    public static Optional<SourceStoneGeneratorRule> find(ResourceLocation id) {
        if (DEFAULT_ID.equals(id)) {
            return Optional.of(defaultRule());
        }
        return Optional.ofNullable(INSTANCE.recipes.get(id));
    }

    public static List<SourceStoneGeneratorRule> allRecipes() {
        List<SourceStoneGeneratorRule> result = new ArrayList<>();
        result.add(defaultRule());
        INSTANCE.recipes.values().stream()
                .filter(rule -> !rule.createOutput().isEmpty())
                .forEach(result::add);
        return List.copyOf(result);
    }

    public static SourceStoneGeneratorRule defaultRule() {
        return new SourceStoneGeneratorRule(
                DEFAULT_ID,
                List.of(),
                ResourceLocation.withDefaultNamespace("cobblestone"),
                64,
                MatrixConfig.GENERATOR_DEFAULT_PROCESSING_COST.get(),
                true
        );
    }
}
