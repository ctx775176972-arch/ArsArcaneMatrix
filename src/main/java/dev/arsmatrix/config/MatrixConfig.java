package dev.arsmatrix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Gameplay configuration for the Arcane Matrix Core and Arcane Mine. */
public final class MatrixConfig {

    public static final int PHYSICAL_FRAME_POSITIONS = 42;
    public static final int MATRIX_AMPLIFIER_POSITIONS = 6;
    public static final int MINE_AMPLIFIER_POSITIONS = 4;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_ALAKARKINOS_EXPEDITIONS;
    public static final ModConfigSpec.BooleanValue ENABLE_DRYGMY_TOOL_ENHANCEMENTS;
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
    public static final ModConfigSpec.IntValue MINE_SOURCE_INPUT_RANGE;
    public static final ModConfigSpec.IntValue MINE_FULL_STRUCTURE_SOURCE_INPUT_RANGE;
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
    public static final ModConfigSpec.IntValue IMBUEMENT_MAX_COMPRESSED_INPUTS;
    public static final ModConfigSpec.IntValue IMBUEMENT_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue GENERATOR_DEFAULT_PROCESSING_COST;
    public static final ModConfigSpec.IntValue GENERATOR_PASSIVE_PROGRESS_PER_SECOND;
    public static final ModConfigSpec.IntValue DRYGMY_ARENA_CYCLE_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Arcane Matrix Core gameplay settings. / 矩阵核心运行设置。").push("matrix_core");

        SOURCE_CAPACITY = builder
                .comment("Maximum Source stored inside one Matrix Core. / 单个矩阵核心的魔源容量。")
                .translation("config.ars_arcane_matrix.matrix_core.sourceCapacity")
                .defineInRange("sourceCapacity", 10_000_000, 1, Integer.MAX_VALUE);
        OUTPUT_RANGE = builder
                .comment("Source output range in blocks, measured from the Matrix Core. Runtime keeps a minimum of 16 blocks so the multiblock frame does not consume most of the usable radius. / 以核心为中心的魔源输出范围，单位为格；实际最小为16格。")
                .translation("config.ars_arcane_matrix.matrix_core.outputRange")
                .defineInRange("outputRange", 16, 1, 64);
        BASE_GENERATION = builder
                .comment("Source generated per second at the minimum frame count. / 达到最低框架数量时，每秒产生的基础魔源。")
                .translation("config.ars_arcane_matrix.matrix_core.baseGenerationPerSecond")
                .defineInRange("baseGenerationPerSecond", 1_000, 0, Integer.MAX_VALUE);
        GENERATION_PER_ADDITIONAL_FRAME = builder
                .comment("Additional Source generated per second for each frame above the minimum. / 超过最低要求后，每个额外框架增加的每秒魔源产量。")
                .translation("config.ars_arcane_matrix.matrix_core.generationPerAdditionalFrame")
                .defineInRange("generationPerAdditionalFrame", 250, 0, Integer.MAX_VALUE);
        MAX_GENERATION_PER_SECOND = builder
                .comment("Configurable Source generation cap per Matrix Core. / 增幅器加成前的魔源产量上限；最终产量仍会乘以增幅倍率。")
                .translation("config.ars_arcane_matrix.matrix_core.maxGenerationPerSecond")
                .defineInRange("maxGenerationPerSecond", 100_000, 0, Integer.MAX_VALUE);
        MINIMUM_FRAME_BLOCKS = builder
                .comment(
                        "Frames required to form the structure. At least one complete 5x5 ring is always required. / 启动所需的最低有效框架数量；仍必须至少完成一个5×5环。",
                        "Runtime value is capped by maximumFrameBlocks. / 此值不能超过下方的最大有效框架数量。"
                )
                .translation("config.ars_arcane_matrix.matrix_core.minimumFrameBlocks")
                .defineInRange("minimumFrameBlocks", 16, 1, PHYSICAL_FRAME_POSITIONS);
        MAXIMUM_FRAME_BLOCKS = builder
                .comment("Maximum frames counted for generation. The physical structure has 42 valid positions. / 计入产量的最大框架数量；结构共有42个有效位置。")
                .translation("config.ars_arcane_matrix.matrix_core.maximumFrameBlocks")
                .defineInRange("maximumFrameBlocks", PHYSICAL_FRAME_POSITIONS, 1, PHYSICAL_FRAME_POSITIONS);
        MAX_OUTPUT_PER_SECOND = builder
                .comment("Maximum Source transferred from one Matrix Core per second. / 单个矩阵核心每秒主动输出的魔源上限。")
                .translation("config.ars_arcane_matrix.matrix_core.maxOutputPerSecond")
                .defineInRange("maxOutputPerSecond", 100_000, 0, Integer.MAX_VALUE);
        MATRIX_AMPLIFIER_BONUS = builder
                .comment("Generation multiplier added by each of the six valid Arcane Amplifier vertices. / 每个有效增幅器的产量加成，0.25表示增加25%；最多6个。")
                .translation("config.ars_arcane_matrix.matrix_core.amplifierBonusPerBlock")
                .defineInRange("amplifierBonusPerBlock", 0.25D, 0.0D, 100.0D);

        builder.pop();

        builder.comment("Arcane Mine structure settings. / 矿井结构设置。").push("arcane_mine").push("structure");
        MINE_LAYER_SIZES = builder
                .worldRestart()
                .comment("Odd, ascending square sizes above the core. Defaults to an inverted 3/5/7/9 beacon. / 核心上方各层的边长，必须是递增奇数；默认3、5、7、9。修改后需要重新进入世界。")
                .translation("config.ars_arcane_matrix.arcane_mine.structure.layerSizes")
                .defineList("layerSizes", List.of(3, 5, 7, 9),
                        value -> value instanceof Integer size && size >= 3 && size <= 15 && (size & 1) == 1);
        MINE_STRUCTURE_CHECK_INTERVAL = builder
                .comment("Ticks between complete structure scans. / 完整结构检测间隔，单位为刻；20刻约为1秒。")
                .translation("config.ars_arcane_matrix.arcane_mine.structure.structureCheckInterval")
                .defineInRange("structureCheckInterval", 20, 5, 200);
        builder.pop();

        builder.comment("Arcane Mine operation settings. / 矿井运行设置。").push("operation");
        MINE_SOURCE_INPUT_RANGE = builder
                .comment("Range in blocks used to pull from Ars Nouveau Source providers, including Beyond Dimensions Source Pathways. / 矿井未完成全部层数时的取源范围，单位为格。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.sourceInputRange")
                .defineInRange("sourceInputRange", 5, 1, 64);
        MINE_FULL_STRUCTURE_SOURCE_INPUT_RANGE = builder
                .comment(
                        "Expanded Source input range used when every configured Arcane Mine layer is complete. / 完成全部矿井层数后的扩大取源范围，单位为格。",
                        "Loaded Matrix Cores inside this range are valid providers; this search never loads additional chunks. / 可使用范围内已加载的矩阵核心，不会主动加载其他区块。"
                )
                .translation("config.ars_arcane_matrix.arcane_mine.operation.fullStructureSourceInputRange")
                .defineInRange("fullStructureSourceInputRange", 32, 1, 64);
        MINE_OUTPUT_BONUS_PER_AMPLIFIER = builder
                .comment("Extra ordinary ore blocks produced per Arcane Amplifier. / 每个增幅器额外增加的普通矿物产量。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.outputBonusPerAmplifier")
                .defineInRange("outputBonusPerAmplifier", 1, 0, 64);
        MINE_COST_INCREASE_PER_AMPLIFIER = builder
                .comment("Source and material cost multiplier added by each Arcane Amplifier. / 每个增幅器增加的魔源与材料费用倍率；0.5表示额外增加50%。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.costIncreasePerAmplifier")
                .defineInRange("costIncreasePerAmplifier", 0.5D, 0.0D, 100.0D);
        MINE_AMPLIFIER_DROP_CHANCE = builder
                .comment("Arcane Amplifier chance per 128 material points consumed by a full four-layer mine. / 完整矿井每消耗128材料点的增幅器产出概率；0.01表示1%。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.amplifierByproductChance")
                .defineInRange("amplifierByproductChance", 0.01D, 0.0D, 1.0D);
        MINE_AMPLIFIER_PITY_MATERIAL_POINTS = builder
                .comment("Guaranteed Arcane Amplifier after this many material points are consumed without one. / 未获得增幅器时累计消耗多少材料点必定产出；获得后重新累计。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.amplifierPityMaterialPoints")
                .defineInRange("amplifierPityMaterialPoints", 12_800, 1, 100_000_000);
        MINE_SOURCESTONE_POINTS = builder
                .comment("Material points supplied by one item in the arcane_mine_material_sourcestone tag. / 每个魔源石类材料提供的材料点数。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.sourcestonePoints")
                .defineInRange("sourcestonePoints", 1, 1, 1_000_000);
        MINE_SOURCE_GEM_POINTS = builder
                .comment("Material points supplied by one item in the arcane_mine_material_source_gem tag. / 每个魔源宝石类材料提供的材料点数。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.sourceGemPoints")
                .defineInRange("sourceGemPoints", 32, 1, 1_000_000);
        MINE_SOURCE_GEM_BLOCK_POINTS = builder
                .comment("Material points supplied by one item in the arcane_mine_material_source_gem_block tag. / 每个魔源宝石块类材料提供的材料点数。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.sourceGemBlockPoints")
                .defineInRange("sourceGemBlockPoints", 128, 1, 1_000_000);
        MINE_MATERIAL_POINT_SOURCE_EQUIVALENT = builder
                .comment(
                        "Source-equivalent cost per material point used only for full-amplifier pacing. / 仅用于满增幅矿井计算工作间隔：每材料点折算的魔源成本，不会额外扣除魔源。",
                        "The default is based on 500 Source per Source Gem and 32 points per gem. / 默认按每个魔源宝石500魔源、提供32材料点折算。"
                )
                .translation("config.ars_arcane_matrix.arcane_mine.operation.materialPointSourceEquivalent")
                .defineInRange("materialPointSourceEquivalent", 15.625D, 0.0D, 1_000_000.0D);
        MINE_MATERIAL_POINT_CAPACITY = builder
                .comment(
                        "Normal converted-material buffer capacity. / 矿井材料点缓存的基础容量。",
                        "Runtime capacity expands to the selected recipe cost to prevent impossible production. / 若单次配方费用更高，会自动扩展到足够完成一次配方。"
                )
                .translation("config.ars_arcane_matrix.arcane_mine.operation.materialPointCapacity")
                .defineInRange("materialPointCapacity", 4_096, 32, 1_000_000);
        MINE_MAX_MATERIAL_CONTAINERS = builder
                .comment("Maximum Dominion Wand material-container links. / 支配之杖最多可绑定的矿井材料输入容器数量。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.maxMaterialContainers")
                .defineInRange("maxMaterialContainers", 4, 1, 16);
        MINE_COOLDOWNS = builder
                .comment("Cooldown in ticks after production for each completed structure layer. / 各层生产冷却，单位为刻；默认依次20、15、10、5秒。满4个增幅器时使用单独的费用折算间隔。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.cooldownTicksByLayer")
                .defineList("cooldownTicksByLayer", List.of(400, 300, 200, 100),
                        value -> value instanceof Integer ticks && ticks >= 1 && ticks <= 72_000);
        MINE_ALLOW_CROSS_DIMENSION = builder
                .comment("Allow loaded containers in other dimensions to be linked. / 是否允许绑定其他维度已加载的容器；不会自动加载目标区块。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.allowCrossDimension")
                .define("allowCrossDimension", true);
        MINE_AUTO_DISCOVER_ORES = builder
                .comment("Create conservative default rules for unconfigured c:ores/* item tags. / 是否自动为其他模组的矿物生成默认规则；关闭后只使用数据包明确指定的矿物。")
                .translation("config.ars_arcane_matrix.arcane_mine.operation.autoDiscoverOres")
                .define("autoDiscoverOres", true);
        builder.pop();

        builder.comment("Arcane Mine visual and sound effects. / 矿井特效的服务器发送设置，影响所有玩家；个人光束与投影显示请修改客户端配置。").push("effects");
        MINE_ENABLE_PARTICLES = builder
                .comment("Send periodic mine particles. / 是否发送矿井运行与红石暂停的周期粒子；不影响完成时的粒子。")
                .translation("config.ars_arcane_matrix.arcane_mine.effects.enableParticles")
                .define("enableParticles", true);
        MINE_PARTICLE_INTERVAL = builder
                .comment("Periodic particle interval in ticks. / 周期粒子发送间隔，20刻约1秒；越小越频繁。")
                .translation("config.ars_arcane_matrix.arcane_mine.effects.particleIntervalTicks")
                .defineInRange("particleIntervalTicks", 10, 2, 200);
        MINE_PARTICLE_DENSITY = builder
                .comment("Active particle density multiplier. / 运行粒子密度倍率；设为0仍有最低粒子量，关闭请使用enableParticles。")
                .translation("config.ars_arcane_matrix.arcane_mine.effects.particleDensity")
                .defineInRange("particleDensity", 1.0D, 0.0D, 4.0D);
        MINE_ENABLE_SOUNDS = builder
                .comment("Send mine event sounds. / 是否向玩家播放矿井启动、停止与完成音效。")
                .translation("config.ars_arcane_matrix.arcane_mine.effects.enableSounds")
                .define("enableSounds", true);
        builder.pop(2);

        builder.comment("Arcane Imbuement Core settings. / 奥术灌注核心设置。").push("arcane_imbuement_core");
        IMBUEMENT_SOURCE_INPUT_RANGE = builder
                .comment("Range used to pay Source directly from Ars Nouveau providers. / 直接从周围魔源提供者支付费用的范围，单位为格。")
                .translation("config.ars_arcane_matrix.arcane_imbuement_core.sourceInputRange")
                .defineInRange("sourceInputRange", 5, 1, 64);
        IMBUEMENT_MAX_COMPRESSED_INPUTS = builder
                .comment("Maximum Lapis or Amethyst items/blocks processed in one cycle. / 每批最多处理的青金石或紫水晶材料数量，单件和方块均按一份输入计数。")
                .translation("config.ars_arcane_matrix.arcane_imbuement_core.maxCompressedInputsPerCycle")
                .defineInRange("maxCompressedInputsPerCycle", 4, 1, 7);
        IMBUEMENT_CYCLE_TICKS = builder
                .comment("Duration of one bulk cycle. 100 ticks equals five seconds. / 单批工作时间，单位为刻；100刻约为5秒。")
                .translation("config.ars_arcane_matrix.arcane_imbuement_core.cycleTicks")
                .defineInRange("cycleTicks", 100, 1, 72_000);
        builder.pop();

        builder.comment("Source Stone Generator settings. / 魔源造石机设置。").push("source_stone_generator");
        GENERATOR_DEFAULT_PROCESSING_COST = builder
                .comment("Progress required by the fallback 64 Cobblestone recipe. / 没有基座配方时，默认64圆石配方所需的工作进度。")
                .translation("config.ars_arcane_matrix.source_stone_generator.defaultProcessingCost")
                .defineInRange("defaultProcessingCost", 200, 1, Integer.MAX_VALUE);
        GENERATOR_PASSIVE_PROGRESS_PER_SECOND = builder
                .comment("Free imbuement-style progress gained each second without external Source. / 没有外部魔源时每秒自然增加的进度；设为0则停止自然积累。")
                .translation("config.ars_arcane_matrix.source_stone_generator.passiveProgressPerSecond")
                .defineInRange("passiveProgressPerSecond", 20, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Arcane Hunting Grounds settings. / 奥术猎场设置。").push("drygmy_arena");
        DRYGMY_ARENA_CYCLE_TICKS = builder
                .comment("Ticks per special-output cycle. 6000 ticks equals five minutes. / 每次猎场生产的工作时间，单位为刻；6000刻约为5分钟。")
                .translation("config.ars_arcane_matrix.drygmy_arena.cycleTicks")
                .defineInRange("cycleTicks", 6_000, 20, 1_728_000);
        builder.pop();
        builder.push("creatures");
        ENABLE_ALAKARKINOS_EXPEDITIONS = builder
                .comment("Enable simulated structure exploration. Native archaeology is unaffected. / 启用探宝蟹模拟结构宝箱探险，不影响普通考古。")
                .translation("config.ars_arcane_matrix.creatures.enableAlakarkinosExpeditions")
                .worldRestart().define("enableAlakarkinosExpeditions", true);
        ENABLE_DRYGMY_TOOL_ENHANCEMENTS = builder
                .comment("Enable Matrix pedestal weapon enhancements for Drygmy. / 启用矩阵提供的德格米基座武器强化，不影响其他模组自己的强化。")
                .translation("config.ars_arcane_matrix.creatures.enableDrygmyToolEnhancements")
                .worldRestart().define("enableDrygmyToolEnhancements", true);
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
