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
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads data/&lt;namespace&gt;/arcane_hunting/*.json. */
public final class ArcaneHuntingRuleManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ArcaneHuntingRuleManager INSTANCE = new ArcaneHuntingRuleManager();
    private volatile Map<ResourceLocation, ArcaneHuntingRule> rules = Map.of();

    private ArcaneHuntingRuleManager() {
        super(GSON, "arcane_hunting");
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ArcaneHuntingRule> loaded = new LinkedHashMap<>();
        entries.forEach((id, element) -> {
            try {
                ArcaneHuntingRule rule = parse(id, GsonHelper.convertToJsonObject(element, id.toString()));
                if (rule.enabled()) loaded.put(id, rule);
            } catch (RuntimeException exception) {
                ArsArcaneMatrix.LOGGER.error("Could not load Arcane Hunting Grounds rule {}", id, exception);
            }
        });
        rules = Map.copyOf(loaded);
        ArsArcaneMatrix.LOGGER.info("Loaded {} Arcane Hunting Grounds rules.", rules.size());
    }

    private static ArcaneHuntingRule parse(ResourceLocation id, JsonObject json) {
        ResourceLocation entity = ResourceLocation.parse(GsonHelper.getAsString(json, "entity"));
        int pointCost = GsonHelper.getAsInt(json, "point_cost");
        if (pointCost <= 0) throw new IllegalArgumentException("point_cost must be positive");
        JsonArray jsonResults = GsonHelper.getAsJsonArray(json, "results");
        if (jsonResults.isEmpty()) throw new IllegalArgumentException("results must not be empty");
        List<ArcaneHuntingRule.Result> results = new ArrayList<>();
        for (JsonElement element : jsonResults) {
            JsonObject result = GsonHelper.convertToJsonObject(element, "result");
            ResourceLocation item = ResourceLocation.parse(GsonHelper.getAsString(result, "item"));
            int count = GsonHelper.getAsInt(result, "count", 1);
            if (count <= 0) throw new IllegalArgumentException("result.count must be positive");
            results.add(new ArcaneHuntingRule.Result(item, count));
        }
        return new ArcaneHuntingRule(id, entity, pointCost, List.copyOf(results),
                GsonHelper.getAsBoolean(json, "enabled", true));
    }

    public static Optional<ArcaneHuntingRule> find(EntityType<?> type) {
        return INSTANCE.rules.values().stream().filter(rule -> rule.matches(type)).findFirst();
    }

    public static List<ArcaneHuntingRule> allRules() {
        return List.copyOf(INSTANCE.rules.values());
    }
}
