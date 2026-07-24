package com.tianxin.arsmatrix.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.config.MatrixConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads data/<namespace>/arcane_mine/*.json and supplies automatic c:ores/* fallbacks. */
public final class ArcaneMineOreManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ArcaneMineOreManager INSTANCE = new ArcaneMineOreManager();

    private volatile Map<ResourceLocation, ArcaneMineOreRule> configuredRules = Map.of();

    private ArcaneMineOreManager() {
        super(GSON, "arcane_mine");
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
        Map<ResourceLocation, ArcaneMineOreRule> loaded = new LinkedHashMap<>();
        entries.forEach((id, json) -> {
            try {
                ArcaneMineOreRule rule = parse(id, GsonHelper.convertToJsonObject(json, id.toString()));
                loaded.put(id, rule);
            } catch (RuntimeException exception) {
                ArsArcaneMatrix.LOGGER.error("Could not load Arcane Mine ore rule {}", id, exception);
            }
        });
        configuredRules = Map.copyOf(loaded);
        ArsArcaneMatrix.LOGGER.info("Loaded {} Arcane Mine ore rules.", configuredRules.size());
    }

    private static ArcaneMineOreRule parse(ResourceLocation id, JsonObject json) {
        JsonObject output = GsonHelper.getAsJsonObject(json, "output");
        boolean hasItem = output.has("item");
        boolean hasTag = output.has("tag");
        if (hasItem == hasTag) {
            throw new IllegalArgumentException("output must contain exactly one of 'item' or 'tag'");
        }

        ResourceLocation outputId = ResourceLocation.parse(
                GsonHelper.getAsString(output, hasTag ? "tag" : "item")
        );
        int outputCount = positive(GsonHelper.getAsInt(output, "count", 1), "output.count");
        int requiredLayers = positive(GsonHelper.getAsInt(json, "required_layers", 1), "required_layers");
        int materialPoints = positive(GsonHelper.getAsInt(json, "material_points"), "material_points");
        int sourceCost = Math.max(0, GsonHelper.getAsInt(json, "source_cost", materialPoints * 100));
        int weight = positive(GsonHelper.getAsInt(json, "weight", 10), "weight");
        boolean enabled = GsonHelper.getAsBoolean(json, "enabled", true);

        return new ArcaneMineOreRule(
                id, outputId, hasTag, outputCount, requiredLayers,
                materialPoints, sourceCost, weight, enabled
        );
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public static Optional<ArcaneMineOreRule> find(ResourceLocation id) {
        return allRules().stream().filter(rule -> rule.enabled() && rule.id().equals(id)).findFirst();
    }

    public static List<ArcaneMineOreRule> allRules() {
        Map<ResourceLocation, ArcaneMineOreRule> merged = new LinkedHashMap<>();
        INSTANCE.configuredRules.values().stream()
                .filter(ArcaneMineOreRule::enabled)
                .forEach(rule -> merged.put(rule.id(), rule));
        if (!MatrixConfig.MINE_AUTO_DISCOVER_ORES.get()) {
            return List.copyOf(merged.values());
        }

        Map<String, Boolean> configuredOutputs = new LinkedHashMap<>();
        INSTANCE.configuredRules.values()
                .forEach(rule -> configuredOutputs.put(rule.outputKey(), Boolean.TRUE));

        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            ResourceLocation tagId = pair.getFirst().location();
            if (!tagId.getNamespace().equals("c") || !tagId.getPath().startsWith("ores/")) {
                return;
            }
            if (tagId.getPath().equals("ores/netherite_scrap")) {
                return;
            }
            String outputKey = "#" + tagId;
            if (configuredOutputs.containsKey(outputKey)) {
                return;
            }
            ResourceLocation ruleId = ResourceLocation.fromNamespaceAndPath(
                    ArsArcaneMatrix.MOD_ID,
                    "auto/" + tagId.getNamespace() + "/" + tagId.getPath()
            );
            merged.put(ruleId, automaticRule(ruleId, tagId));
        });
        return List.copyOf(merged.values());
    }

    private static ArcaneMineOreRule automaticRule(ResourceLocation ruleId, ResourceLocation tagId) {
        String material = tagId.getPath().substring("ores/".length());
        int layers;
        int points;
        int weight;
        switch (material) {
            case "coal", "copper", "iron" -> {
                layers = 1;
                points = 8;
                weight = 20;
            }
            case "gold", "redstone", "lapis", "quartz" -> {
                layers = 2;
                points = 24;
                weight = 12;
            }
            case "diamond", "emerald" -> {
                layers = 4;
                points = 128;
                weight = 2;
            }
            case "netherite_scrap" -> {
                layers = 4;
                points = 512;
                weight = 1;
            }
            default -> {
                layers = 2;
                points = 24;
                weight = 10;
            }
        }
        return new ArcaneMineOreRule(
                ruleId, tagId, true, 1, layers, points, points * 100, weight, true
        );
    }

    public static Optional<ArcaneMineOreRule> choose(int completedLayers, net.minecraft.util.RandomSource random) {
        List<ArcaneMineOreRule> eligible = new ArrayList<>();
        int totalWeight = 0;
        for (ArcaneMineOreRule rule : allRules()) {
            if (rule.requiredLayers() <= completedLayers && !rule.createOutput(random).isEmpty()) {
                eligible.add(rule);
                totalWeight += rule.weight();
            }
        }
        if (eligible.isEmpty() || totalWeight <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(totalWeight);
        for (ArcaneMineOreRule rule : eligible) {
            roll -= rule.weight();
            if (roll < 0) {
                return Optional.of(rule);
            }
        }
        return Optional.of(eligible.getLast());
    }
}
