package com.tianxin.arsmatrix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Gameplay configuration for the Arcane Matrix Core. */
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
}
