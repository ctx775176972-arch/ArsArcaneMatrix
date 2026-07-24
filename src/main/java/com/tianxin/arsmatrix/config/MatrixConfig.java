package com.tianxin.arsmatrix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Gameplay configuration for the Arcane Matrix Core and Arcane Mine. */
public final class MatrixConfig {

    public static final int PHYSICAL_FRAME_POSITIONS = 42;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SOURCE_CAPACITY;
    public static final ModConfigSpec.IntValue OUTPUT_RANGE;
    public static final ModConfigSpec.IntValue BASE_GENERATION;
    public static final ModConfigSpec.IntValue GENERATION_PER_ADDITIONAL_FRAME;
    public static final ModConfigSpec.IntValue MINIMUM_FRAME_BLOCKS;
    public static final ModConfigSpec.IntValue MAXIMUM_FRAME_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_OUTPUT_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> MINE_LAYER_SIZES;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> MINE_COOLDOWNS;
    public static final ModConfigSpec.IntValue MINE_STRUCTURE_CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue MINE_SOURCE_CAPACITY;
    public static final ModConfigSpec.IntValue MINE_SOURCE_INPUT_RANGE;
    public static final ModConfigSpec.IntValue MINE_MAX_SOURCE_INPUT_PER_SECOND;
    public static final ModConfigSpec.IntValue MINE_MATERIAL_POINT_CAPACITY;
    public static final ModConfigSpec.IntValue MINE_MAX_MATERIAL_CONTAINERS;
    public static final ModConfigSpec.BooleanValue MINE_ALLOW_CROSS_DIMENSION;
    public static final ModConfigSpec.BooleanValue MINE_AUTO_DISCOVER_ORES;
    public static final ModConfigSpec.BooleanValue MINE_ENABLE_PARTICLES;
    public static final ModConfigSpec.IntValue MINE_PARTICLE_INTERVAL;
    public static final ModConfigSpec.DoubleValue MINE_PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue MINE_ENABLE_SOUNDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Arcane Matrix Core gameplay settings.").push("matrix_core");

        SOURCE_CAPACITY = builder
                .comment("Maximum Source stored inside one Matrix Core.")
                .defineInRange("sourceCapacity", 10_000_000, 1, Integer.MAX_VALUE);
        OUTPUT_RANGE = builder
                .comment("Source output range in blocks.")
                .defineInRange("outputRange", 5, 1, 64);
        BASE_GENERATION = builder
                .comment("Source generated per second at the minimum frame count.")
                .defineInRange("baseGenerationPerSecond", 1_000, 0, Integer.MAX_VALUE);
        GENERATION_PER_ADDITIONAL_FRAME = builder
                .comment("Additional Source generated per second for each frame above the minimum.")
                .defineInRange("generationPerAdditionalFrame", 250, 0, Integer.MAX_VALUE);
        MINIMUM_FRAME_BLOCKS = builder
                .comment("Frames required to form the structure. Runtime value is capped by maximumFrameBlocks.")
                .defineInRange("minimumFrameBlocks", 16, 1, PHYSICAL_FRAME_POSITIONS);
        MAXIMUM_FRAME_BLOCKS = builder
                .comment("Maximum frames counted for generation. The physical structure has 42 valid positions.")
                .defineInRange("maximumFrameBlocks", PHYSICAL_FRAME_POSITIONS, 1, PHYSICAL_FRAME_POSITIONS);
        MAX_OUTPUT_PER_SECOND = builder
                .comment("Maximum Source transferred from one Matrix Core per second.")
                .defineInRange("maxOutputPerSecond", 10_000, 0, Integer.MAX_VALUE);

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
        MINE_MAX_SOURCE_INPUT_PER_SECOND = builder
                .comment("Maximum Source pulled into one active Arcane Mine Core per second.")
                .defineInRange("maxSourceInputPerSecond", 10_000, 0, Integer.MAX_VALUE);
        MINE_MATERIAL_POINT_CAPACITY = builder
                .comment("Maximum converted material points buffered inside one core.")
                .defineInRange("materialPointCapacity", 1_024, 32, 1_000_000);
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
