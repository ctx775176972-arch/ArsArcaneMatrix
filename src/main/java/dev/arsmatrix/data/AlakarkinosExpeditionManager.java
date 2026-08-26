package dev.arsmatrix.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads data/&lt;namespace&gt;/alakarkinos_expedition/*.json. */
public final class AlakarkinosExpeditionManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final AlakarkinosExpeditionManager INSTANCE = new AlakarkinosExpeditionManager();
    private volatile Map<ResourceLocation, AlakarkinosExpeditionRule> rules = Map.of();

    private AlakarkinosExpeditionManager() { super(GSON, "alakarkinos_expedition"); }
    public static void registerReloadListener(AddReloadListenerEvent event) { event.addListener(INSTANCE); }

    @Override protected void apply(Map<ResourceLocation, JsonElement> entries,
            ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, AlakarkinosExpeditionRule> loaded = new LinkedHashMap<>();
        entries.forEach((id, json) -> {
            try {
                AlakarkinosExpeditionRule rule = parse(id, GsonHelper.convertToJsonObject(json, id.toString()));
                if (rule.enabled()) loaded.put(id, rule);
            } catch (RuntimeException exception) {
                ArsArcaneMatrix.LOGGER.error("Could not load Alakarkinos expedition {}", id, exception);
            }
        });
        rules = Map.copyOf(loaded);
        ArsArcaneMatrix.LOGGER.info("Loaded {} Alakarkinos expedition recipes.", rules.size());
    }

    private static AlakarkinosExpeditionRule parse(ResourceLocation id, JsonObject json) {
        List<AlakarkinosExpeditionRule.IngredientCost> inputs = new ArrayList<>();
        JsonArray inputArray;
        if (json.has("inputs")) inputArray = GsonHelper.getAsJsonArray(json, "inputs");
        else {
            inputArray = new JsonArray();
            inputArray.add(GsonHelper.getAsJsonObject(json, "input"));
        }
        for (JsonElement element : inputArray) {
            JsonObject input = GsonHelper.convertToJsonObject(element, "expedition input");
            boolean hasItem = input.has("item");
            boolean hasTag = input.has("tag");
            if (hasItem == hasTag) throw new IllegalArgumentException("input needs exactly one item or tag");
            inputs.add(new AlakarkinosExpeditionRule.IngredientCost(
                    ResourceLocation.parse(GsonHelper.getAsString(input, hasTag ? "tag" : "item")),
                    hasTag, positive(GsonHelper.getAsInt(input, "count", 1), "input.count")));
        }
        if (inputs.isEmpty()) throw new IllegalArgumentException("inputs cannot be empty");
        ResourceLocation proof = json.has("proof")
                ? ResourceLocation.parse(GsonHelper.getAsString(json, "proof")) : null;
        int workTicks = positive(GsonHelper.getAsInt(json, "work_ticks"), "work_ticks");
        int sourceCost = positive(GsonHelper.getAsInt(json, "source_cost"), "source_cost");
        boolean hasTable = json.has("loot_table");
        boolean hasOutput = json.has("output");
        if (hasTable == hasOutput) throw new IllegalArgumentException("recipe needs exactly one loot_table or output");
        ResourceLocation table = hasTable
                ? ResourceLocation.parse(GsonHelper.getAsString(json, "loot_table")) : null;
        ResourceLocation outputId = null;
        int outputCount = 0;
        if (hasOutput) {
            JsonObject output = GsonHelper.getAsJsonObject(json, "output");
            outputId = ResourceLocation.parse(GsonHelper.getAsString(output, "item"));
            outputCount = positive(GsonHelper.getAsInt(output, "count", 1), "output.count");
        }
        List<AlakarkinosExpeditionRule.DisplayResult> display = new ArrayList<>();
        JsonArray displayArray = GsonHelper.getAsJsonArray(json, "display_outputs", new JsonArray());
        for (JsonElement element : displayArray) {
            JsonObject result = GsonHelper.convertToJsonObject(element, "display output");
            display.add(new AlakarkinosExpeditionRule.DisplayResult(
                    ResourceLocation.parse(GsonHelper.getAsString(result, "item")),
                    positive(GsonHelper.getAsInt(result, "count", 1), "display output count")));
        }
        Set<ResourceLocation> excluded = new LinkedHashSet<>();
        JsonArray excludedArray = GsonHelper.getAsJsonArray(json, "excluded_outputs", new JsonArray());
        excludedArray.forEach(element -> excluded.add(ResourceLocation.parse(GsonHelper.convertToString(element, "excluded output"))));
        return new AlakarkinosExpeditionRule(id, inputs, proof,
                workTicks, sourceCost, table, outputId, outputCount, display, excluded,
                GsonHelper.getAsBoolean(json, "enabled", true));
    }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    public static List<AlakarkinosExpeditionRule> allRules() {
        return INSTANCE.rules.values().stream()
                .sorted(Comparator.comparing(rule -> rule.id().toString())).toList();
    }
}
