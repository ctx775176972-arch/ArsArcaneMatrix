package dev.arsmatrix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Gameplay configuration for the Arcane Matrix Core and Arcane Mine. */
public final class MatrixConfig {

    public static final int PHYSICAL_FRAME_POSITIONS = 42;
    public static final int MATRIX_AMPLIFIER_POSITIONS = 6;
    public static final int MINE_AMPLIFIER_POSITIONS = 4;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SOURCE_CAPACITY;
    public static final ModConfigSpec.IntValue OUTPUT_RANGE;
    public static final ModConfigSpec.IntValue BASE_GENERATION;
    public static final ModConfigSpec.IntValue GENERATION_PER_ADDITIONAL_FRAME;
    public static final ModConfigSpec.IntValue MAX_GENERATION_PER_SECOND;
    public static final ModConfigSpec.IntValue MINIMUM_FRAME_BLOCKS;
    public static final ModConfigSpec.IntValue MAXIMUM_FRAME_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_OUTPUT_PER_SECOND;
    public static final ModConfigSpec.DoubleValue MATRIX_AMPLIFIER_BONUS;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> MINE_LAYER_SIZES;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> MINE_COOLDOWNS;
    public static final ModConfigSpec.IntValue MINE_STRUCTURE_CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue MINE_SOURCE_CAPACITY;
    public static final ModConfigSpec.IntValue MINE_SOURCE_INPUT_RANGE;
    public static final ModConfigSpec.IntValue MINE_FULL_STRUCTURE_SOURCE_INPUT_RANGE;
    public static final ModConfigSpec.IntValue MINE_MAX_SOURCE_INPUT_PER_SECOND;
    public static final ModConfigSpec.IntValue MINE_OUTPUT_BONUS_PER_AMPLIFIER;
    public static final ModConfigSpec.DoubleValue MINE_COST_INCREASE_PER_AMPLIFIER;
    public static final ModConfigSpec.DoubleValue MINE_AMPLIFIER_DROP_CHANCE;
    public static final ModConfigSpec.IntValue MINE_AMPLIFIER_PITY_MATERIAL_POINTS;
    public static final ModConfigSpec.IntValue MINE_SOURCESTONE_POINTS;
    public static final ModConfigSpec.IntValue MINE_SOURCE_GEM_POINTS;
    public static final ModConfigSpec.IntValue MINE_SOURCE_GEM_BLOCK_POINTS;
    public static final ModConfigSpec.DoubleValue MINE_MATERIAL_POINT_SOURCE_EQUIVALENT;
    public static final ModConfigSpec.IntValue MINE_MATERIAL_POINT_CAPACITY;
    public static final ModConfigSpec.IntValue MINE_MAX_MATERIAL_CONTAINERS;
    public static final ModConfigSpec.BooleanValue MINE_ALLOW_CROSS_DIMENSION;
    public static final ModConfigSpec.BooleanValue MINE_AUTO_DISCOVER_ORES;
    public static final ModConfigSpec.BooleanValue MINE_ENABLE_PARTICLES;
    public static final ModConfigSpec.IntValue MINE_PARTICLE_INTERVAL;
    public static final ModConfigSpec.DoubleValue MINE_PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue MINE_ENABLE_SOUNDS;
    public static final ModConfigSpec.IntValue IMBUEMENT_SOURCE_INPUT_RANGE;
    public static final ModConfigSpec.IntValue IMBUEMENT_MINIMUM_CHAMBER_DISTANCE;
    public static final ModConfigSpec.IntValue IMBUEMENT_MAXIMUM_CHAMBER_DISTANCE;
    public static final ModConfigSpec.IntValue IMBUEMENT_MAX_COMPRESSED_INPUTS;
    public static final ModConfigSpec.IntValue IMBUEMENT_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue GENERATOR_DEFAULT_PROCESSING_COST;
    public static final ModConfigSpec.IntValue GENERATOR_PASSIVE_PROGRESS_PER_SECOND;
    public static final ModConfigSpec.IntValue GENERATOR_SOURCE_INPUT_RANGE;
    public static final ModConfigSpec.IntValue DRYGMY_ARENA_CYCLE_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Arcane Matrix Core gameplay settings.").push("matrix_core");

        SOURCE_CAPACITY = builder
                .comment("Maximum Source stored inside one Matrix Core.")
                .defineInRange("sourceCapacity", 10_000_000, 1, Integer.MAX_VALUE);
        OUTPUT_RANGE = builder
                .comment("Source output range in blocks, measured from the Matrix Core. Runtime keeps a minimum of 16 blocks so the multiblock frame does not consume most of the usable radius.")
                .defineInRange("outputRange", 16, 1, 64);
        BASE_GENERATION = builder
                .comment("Source generated per second at the minimum frame count.")
                .defineInRange("baseGenerationPerSecond", 1_000, 0, Integer.MAX_VALUE);
        GENERATION_PER_ADDITIONAL_FRAME = builder
                .comment("Additional Source generated per second for each frame above the minimum.")
                .defineInRange("generationPerAdditionalFrame", 250, 0, Integer.MAX_VALUE);
        MAX_GENERATION_PER_SECOND = builder
                .comment("Configurable Source generation cap per Matrix Core.")
                .defineInRange("maxGenerationPerSecond", 100_000, 0, Integer.MAX_VALUE);
        MINIMUM_FRAME_BLOCKS = builder
                .comment(
                        "Frames required to form the structure. At least one complete 5x5 ring is always required.",
                        "Runtime value is capped by maximumFrameBlocks."
                )
                .defineInRange("minimumFrameBlocks", 16, 1, PHYSICAL_FRAME_POSITIONS);
        MAXIMUM_FRAME_BLOCKS = builder
                .comment("Maximum frames counted for generation. The physical structure has 42 valid positions.")
                .defineInRange("maximumFrameBlocks", PHYSICAL_FRAME_POSITIONS, 1, PHYSICAL_FRAME_POSITIONS);
        MAX_OUTPUT_PER_SECOND = builder
                .comment("Maximum Source transferred from one Matrix Core per second.")
                .defineInRange("maxOutputPerSecond", 100_000, 0, Integer.MAX_VALUE);
        MATRIX_AMPLIFIER_BONUS = builder
                .comment("Generation multiplier added by each of the six valid Arcane Amplifier vertices.")
                .defineInRange("amplifierBonusPerBlock", 0.25D, 0.0D, 100.0D);

        builder.pop();

        builder.comment("Arcane Mine structure settings.").push("arcane_mine").push("structure");
        MINE_LAYER_SIZES = builder
                .worldRestart()
                .comment("Odd, ascending square sizes above the core. Defaults to an inverted 3/5/7/9 beacon.")
                .defineList("layerSizes", List.of(3, 5, 7, 9),
                        value -> value instanceof Integer size && size >= 3 && size <= 15 && (size & 1) == 1);
        MINE_STRUCTURE_CHECK_INTERVAL = builder
                .comment("Ticks between complete structure scans.")
                .defineInRange("structureCheckInterval", 20, 5, 200);
        builder.pop();

        builder.comment("Arcane Mine operation settings.").push("operation");
        MINE_SOURCE_CAPACITY = builder
                .comment("Maximum Source stored inside one Arcane Mine Core.")
                .defineInRange("sourceCapacity", 1_000_000, 1, Integer.MAX_VALUE);
        MINE_SOURCE_INPUT_RANGE = builder
                .comment("Range in blocks used to pull from Ars Nouveau Source providers, including Beyond Dimensions Source Pathways.")
                .defineInRange("sourceInputRange", 5, 1, 64);
        MINE_FULL_STRUCTURE_SOURCE_INPUT_RANGE = builder
                .comment(
                        "Expanded Source input range used when every configured Arcane Mine layer is complete.",
                        "Loaded Matrix Cores inside this range are valid providers; this search never loads additional chunks."
                )
                .defineInRange("fullStructureSourceInputRange", 32, 1, 64);
        MINE_MAX_SOURCE_INPUT_PER_SECOND = builder
                .comment("Maximum Source pulled into one active Arcane Mine Core per second.")
                .defineInRange("maxSourceInputPerSecond", 100_000, 0, Integer.MAX_VALUE);
        MINE_OUTPUT_BONUS_PER_AMPLIFIER = builder
                .comment("Extra ordinary ore blocks produced per Arcane Amplifier.")
                .defineInRange("outputBonusPerAmplifier", 1, 0, 64);
        MINE_COST_INCREASE_PER_AMPLIFIER = builder
                .comment("Source and material cost multiplier added by each Arcane Amplifier.")
                .defineInRange("costIncreasePerAmplifier", 0.5D, 0.0D, 100.0D);
        MINE_AMPLIFIER_DROP_CHANCE = builder
                .comment("Arcane Amplifier chance per 128 material points consumed by a full four-layer mine.")
                .defineInRange("amplifierByproductChance", 0.01D, 0.0D, 1.0D);
        MINE_AMPLIFIER_PITY_MATERIAL_POINTS = builder
                .comment("Guaranteed Arcane Amplifier after this many material points are consumed without one.")
                .defineInRange("amplifierPityMaterialPoints", 12_800, 1, 100_000_000);
        MINE_SOURCESTONE_POINTS = builder
                .comment("Material points supplied by one item in the arcane_mine_material_sourcestone tag.")
                .defineInRange("sourcestonePoints", 1, 1, 1_000_000);
        MINE_SOURCE_GEM_POINTS = builder
                .comment("Material points supplied by one item in the arcane_mine_material_source_gem tag.")
                .defineInRange("sourceGemPoints", 32, 1, 1_000_000);
        MINE_SOURCE_GEM_BLOCK_POINTS = builder
                .comment("Material points supplied by one item in the arcane_mine_material_source_gem_block tag.")
                .defineInRange("sourceGemBlockPoints", 128, 1, 1_000_000);
        MINE_MATERIAL_POINT_SOURCE_EQUIVALENT = builder
                .comment(
                        "Source-equivalent cost per material point used only for full-amplifier pacing.",
                        "The default is based on 500 Source per Source Gem and 32 points per gem."
                )
                .defineInRange("materialPointSourceEquivalent", 15.625D, 0.0D, 1_000_000.0D);
        MINE_MATERIAL_POINT_CAPACITY = builder
                .comment(
                        "Normal converted-material buffer capacity.",
                        "Runtime capacity expands to the selected recipe cost to prevent impossible production."
                )
                .defineInRange("materialPointCapacity", 4_096, 32, 1_000_000);
        MINE_MAX_MATERIAL_CONTAINERS = builder
                .comment("Maximum Dominion Wand material-container links.")
                .defineInRange("maxMaterialContainers", 4, 1, 16);
        MINE_COOLDOWNS = builder
                .comment("Cooldown in ticks after production for each completed structure layer.")
                .defineList("cooldownTicksByLayer", List.of(400, 300, 200, 100),
                        value -> value instanceof Integer ticks && ticks >= 1 && ticks <= 72_000);
        MINE_ALLOW_CROSS_DIMENSION = builder
                .comment("Allow loaded containers in other dimensions to be linked.")
                .define("allowCrossDimension", true);
        MINE_AUTO_DISCOVER_ORES = builder
                .comment("Create conservative default rules for unconfigured c:ores/* item tags.")
                .define("autoDiscoverOres", true);
        builder.pop();

        builder.comment("Arcane Mine visual and sound effects.").push("effects");
        MINE_ENABLE_PARTICLES = builder.define("enableParticles", true);
        MINE_PARTICLE_INTERVAL = builder
                .defineInRange("particleIntervalTicks", 10, 2, 200);
        MINE_PARTICLE_DENSITY = builder
                .defineInRange("particleDensity", 1.0D, 0.0D, 4.0D);
        MINE_ENABLE_SOUNDS = builder.define("enableSounds", true);
        builder.pop(2);

        builder.comment("Arcane Imbuement Core settings.").push("arcane_imbuement_core");
        IMBUEMENT_SOURCE_INPUT_RANGE = builder
                .comment("Range used to pay Source directly from Ars Nouveau providers.")
                .defineInRange("sourceInputRange", 5, 1, 64);
        IMBUEMENT_MINIMUM_CHAMBER_DISTANCE = builder
                .comment("Minimum vertical distance to a chamber in the same X/Z column.")
                .defineInRange("minimumChamberDistance", 2, 1, 16);
        IMBUEMENT_MAXIMUM_CHAMBER_DISTANCE = builder
                .comment("Maximum vertical distance to a chamber in the same X/Z column.")
                .defineInRange("maximumChamberDistance", 6, 1, 32);
        IMBUEMENT_MAX_COMPRESSED_INPUTS = builder
                .comment("Maximum Lapis or Amethyst items/blocks processed in one cycle.")
                .defineInRange("maxCompressedInputsPerCycle", 4, 1, 7);
        IMBUEMENT_CYCLE_TICKS = builder
                .comment("Duration of one bulk cycle. 100 ticks equals five seconds.")
                .defineInRange("cycleTicks", 100, 1, 72_000);
        builder.pop();

        builder.comment("Source Stone Generator settings.").push("source_stone_generator");
        GENERATOR_DEFAULT_PROCESSING_COST = builder
                .comment("Progress required by the fallback 64 Cobblestone recipe.")
                .defineInRange("defaultProcessingCost", 200, 1, Integer.MAX_VALUE);
        GENERATOR_PASSIVE_PROGRESS_PER_SECOND = builder
                .comment("Free imbuement-style progress gained each second without external Source.")
                .defineInRange("passiveProgressPerSecond", 20, 0, Integer.MAX_VALUE);
        GENERATOR_SOURCE_INPUT_RANGE = builder
                .comment("Range used to draw Source from Ars Nouveau providers.")
                .defineInRange("sourceInputRange", 5, 1, 64);
        builder.pop();

        builder.comment("Arcane Hunting Grounds settings.").push("drygmy_arena");
        DRYGMY_ARENA_CYCLE_TICKS = builder
                .comment("Ticks per special-output cycle. 6000 ticks equals five minutes.")
                .defineInRange("cycleTicks", 6_000, 20, 1_728_000);
        builder.pop();
        SPEC = builder.build();
    }

    private MatrixConfig() {
    }

    public static int minimumFrameBlocks() {
        return Math.min(MINIMUM_FRAME_BLOCKS.get(), maximumFrameBlocks());
    }

    public static int maximumFrameBlocks() {
        return Math.min(MAXIMUM_FRAME_BLOCKS.get(), PHYSICAL_FRAME_POSITIONS);
    }

    public static int matrixGenerationFor(int frameBlocks, int amplifiers) {
        int minimumFrames = minimumFrameBlocks();
        if (frameBlocks < minimumFrames) {
            return 0;
        }
        long base = BASE_GENERATION.get().longValue()
                + (long) (frameBlocks - minimumFrames) * GENERATION_PER_ADDITIONAL_FRAME.get();
        base = Math.min(base, MAX_GENERATION_PER_SECOND.get());
        double multiplier = 1.0D
                + Math.max(0, Math.min(amplifiers, MATRIX_AMPLIFIER_POSITIONS))
                * MATRIX_AMPLIFIER_BONUS.get();
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base * multiplier));
    }

    public static int amplifiedMineCost(int baseCost, int amplifiers) {
        double multiplier = 1.0D
                + Math.max(0, Math.min(amplifiers, MINE_AMPLIFIER_POSITIONS))
                * MINE_COST_INCREASE_PER_AMPLIFIER.get();
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.max(0, baseCost) * multiplier));
    }

    public static int mineOperationCooldown(
            int completedLayers,
            int amplifiers,
            int amplifiedSourceCost,
            int amplifiedMaterialCost
    ) {
        if (amplifiers < MINE_AMPLIFIER_POSITIONS) {
            return mineCooldownForLayer(completedLayers);
        }
        int referenceGeneration = matrixGenerationFor(
                maximumFrameBlocks(),
                MATRIX_AMPLIFIER_POSITIONS
        );
        if (referenceGeneration <= 0) {
            return mineCooldownForLayer(completedLayers);
        }
        double pacingCost = amplifiedSourceCost
                + Math.max(0, amplifiedMaterialCost) * MINE_MATERIAL_POINT_SOURCE_EQUIVALENT.get();
        long requiredTicks = Math.max(
                20L,
                (long) Math.ceil(pacingCost * 20.0D / referenceGeneration)
        );
        long roundedToSecond = ((requiredTicks + 19L) / 20L) * 20L;
        return (int) Math.min(72_000L, roundedToSecond);
    }

    public static List<Integer> mineLayerSizes() {
        List<Integer> configured = MINE_LAYER_SIZES.get().stream().map(Number::intValue).toList();
        if (configured.isEmpty()) {
            return List.of(3, 5, 7, 9);
        }
        int previous = 1;
        for (int size : configured) {
            if (size <= previous || size < 3 || size > 15 || (size & 1) == 0) {
                return List.of(3, 5, 7, 9);
            }
            previous = size;
        }
        return configured;
    }

    public static int mineCooldownForLayer(int completedLayers) {
        List<? extends Integer> values = MINE_COOLDOWNS.get();
        if (values.isEmpty()) {
            return 100;
        }
        int index = Math.max(0, Math.min(completedLayers - 1, values.size() - 1));
        return Math.max(1, values.get(index));
    }
}
