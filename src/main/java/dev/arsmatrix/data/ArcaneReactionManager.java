package dev.arsmatrix.data;

import com.google.gson.*;
import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.*;

/** Loads data/&lt;namespace&gt;/arcane_reaction/*.json. */
public final class ArcaneReactionManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ArcaneReactionManager INSTANCE = new ArcaneReactionManager();
    private volatile Map<ResourceLocation, ArcaneReactionRule> recipes = Map.of();

    private ArcaneReactionManager() { super(GSON, "arcane_reaction"); }
    public static void registerReloadListener(AddReloadListenerEvent event) { event.addListener(INSTANCE); }

    @Override protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ArcaneReactionRule> loaded = new LinkedHashMap<>();
        entries.forEach((id, json) -> {
            try {
                ArcaneReactionRule rule = parse(id, GsonHelper.convertToJsonObject(json, id.toString()));
                if (rule.enabled()) loaded.put(id, rule);
            } catch (RuntimeException exception) {
                ArsArcaneMatrix.LOGGER.error("Could not load Arcane Reaction recipe {}", id, exception);
            }
        });
        recipes = Map.copyOf(loaded);
        ArsArcaneMatrix.LOGGER.info("Loaded {} Arcane Reaction recipes.", recipes.size());
    }

    private static ArcaneReactionRule parse(ResourceLocation id, JsonObject json) {
        List<ArcaneReactionIngredient> ingredients = new ArrayList<>();
        JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients", new JsonArray());
        if (array.size() > 2) throw new IllegalArgumentException("ingredients may contain at most two entries");
        for (JsonElement element : array) {
            JsonObject value = element.getAsJsonObject();
            boolean tag = value.has("tag");
            if (tag == value.has("item")) throw new IllegalArgumentException("ingredient needs exactly one item or tag");
            ingredients.add(new ArcaneReactionIngredient(ResourceLocation.parse(
                    GsonHelper.getAsString(value, tag ? "tag" : "item")), tag,
                    positive(GsonHelper.getAsInt(value, "count", 1), "ingredient count")));
        }
        JsonObject input = GsonHelper.getAsJsonObject(json, "input_fluid", new JsonObject());
        ResourceLocation inputFluid = ResourceLocation.parse(GsonHelper.getAsString(input, "id", "minecraft:empty"));
        int inputAmount = Math.max(0, GsonHelper.getAsInt(input, "amount", 0));
        JsonObject output = GsonHelper.getAsJsonObject(json, "output");
        boolean fluidOutput = output.has("fluid");
        ResourceLocation outputItem = ResourceLocation.parse(fluidOutput ? "minecraft:air" : GsonHelper.getAsString(output, "item"));
        ResourceLocation outputFluid = ResourceLocation.parse(fluidOutput ? GsonHelper.getAsString(output, "fluid") : "minecraft:empty");
        int amount = positive(GsonHelper.getAsInt(output, "amount", fluidOutput ? 1000 : 1), "output amount");
        return new ArcaneReactionRule(id, ingredients, inputFluid, inputAmount,
                outputItem, fluidOutput ? 0 : amount, outputFluid, fluidOutput ? amount : 0,
                Math.max(0, GsonHelper.getAsInt(json, "source_cost", 200)),
                positive(GsonHelper.getAsInt(json, "processing_ticks", 100), "processing_ticks"),
                GsonHelper.getAsBoolean(json, "enabled", true));
    }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    public static Optional<ArcaneReactionRule> findMatch(List<ItemStack> items, FluidStack fluid) {
        return INSTANCE.recipes.values().stream().filter(r -> r.matches(items, fluid))
                .sorted(Comparator.comparing(r -> r.id().toString())).findFirst();
    }
    public static Optional<ArcaneReactionRule> find(ResourceLocation id) { return Optional.ofNullable(INSTANCE.recipes.get(id)); }
    public static List<ArcaneReactionRule> allRecipes() { return List.copyOf(INSTANCE.recipes.values()); }
}
