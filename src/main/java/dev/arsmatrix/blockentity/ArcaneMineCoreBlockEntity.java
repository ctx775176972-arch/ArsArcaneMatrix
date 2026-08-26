package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.data.ArcaneMineOreManager;
import dev.arsmatrix.data.ArcaneMineOreRule;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.joml.Vector3f;

/**
 * Source-powered, data-driven ore producer controlled by an inverted-beacon multiblock.
 * Material and output containers may be linked across dimensions but are never chunk-loaded.
 */
@SuppressWarnings("removal")
public class ArcaneMineCoreBlockEntity extends BlockEntity
        implements ISourceTile, ITooltipProvider, IWandable {

    private static final DustParticleOptions WHITELIST_PARTICLE = new DustParticleOptions(
            new Vector3f(0.65F, 0.9F, 1.0F),
            0.8F
    );
    private static final DustParticleOptions BLACKLIST_PARTICLE = new DustParticleOptions(
            new Vector3f(0.35F, 0.02F, 0.02F),
            0.8F
    );
    private static final DustParticleOptions REDSTONE_PAUSED_PARTICLE = new DustParticleOptions(
            new Vector3f(0.45F, 0.03F, 0.05F),
            0.65F
    );
    private static final TagKey<Block> FRAME_BLOCKS = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_mine_frame_blocks")
    );
    private static final TagKey<Block> NODE_BLOCKS = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_mine_node_blocks")
    );
    private static final TagKey<Item> MATERIAL_SOURCESTONE = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_mine_material_sourcestone")
    );
    private static final TagKey<Item> MATERIAL_SOURCE_GEM = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_mine_material_source_gem")
    );
    private static final TagKey<Item> MATERIAL_SOURCE_GEM_BLOCK = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_mine_material_source_gem_block")
    );

    private final SourceStorage sourceStorage = createSourceStorage();
    private final List<GlobalPos> materialContainers = new ArrayList<>();
    @Nullable
    private GlobalPos outputContainer;

    private boolean active;
    private boolean redstonePaused;
    private OperatingState operatingState = OperatingState.UNFORMED;
    private int completedLayers;
    private int amplifierCount;
    private int whitelistCount;
    private int blacklistCount;
    private int materialPoints;
    private int cooldownTicks;
    private int targetMaterialCost;
    private int targetSourceCost;
    private int inputRoundRobin;
    private long tickCounter;

    @Nullable
    private ResourceLocation targetRuleId;
    private ItemStack targetOutput = ItemStack.EMPTY;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private ItemStack pendingByproduct = ItemStack.EMPTY;
    private Set<ResourceLocation> whitelistedRules = Set.of();
    private Set<ResourceLocation> blacklistedRules = Set.of();
    private List<BlockPos> whitelistTuningPositions = List.of();
    private List<BlockPos> blacklistTuningPositions = List.of();

    private final IItemHandler materialInputHandler = new MaterialInputHandler();
    private final IItemHandler mineralOutputHandler = new MineralOutputHandler();

    public ArcaneMineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_MINE_CORE.get(), pos, state);
        refreshSourceLimits();
    }

    public void serverTick() {
        tickCounter++;
        int structureInterval = MatrixConfig.MINE_STRUCTURE_CHECK_INTERVAL.get();
        if (tickCounter % structureInterval == 0) {
            updateStructure();
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        boolean powered = level != null && level.hasNeighborSignal(worldPosition);
        if (redstonePaused != powered) {
            redstonePaused = powered;
            setChangedAndSyncClient();
        }
        if (!active || completedLayers <= 0) {
            setOperatingState(OperatingState.UNFORMED);
            return;
        }

        if (redstonePaused) {
            setOperatingState(OperatingState.REDSTONE_PAUSED);
            if (MatrixConfig.MINE_ENABLE_PARTICLES.get()
                    && tickCounter % MatrixConfig.MINE_PARTICLE_INTERVAL.get() == 0) {
                playRedstonePausedParticles();
            }
            return;
        }

        if (MatrixConfig.MINE_ENABLE_PARTICLES.get()
                && tickCounter % MatrixConfig.MINE_PARTICLE_INTERVAL.get() == 0) {
            playActiveParticles();
        }
        if (tickCounter % 20 != 0) {
            return;
        }

        pullNearbySource();
        flushPendingOutput();
        if (!pendingOutput.isEmpty()) {
            setOperatingState(OperatingState.OUTPUT_BLOCKED);
            return;
        }

        ArcaneMineOreRule target = resolveOrChooseTarget();
        if (target == null) {
            setOperatingState(OperatingState.NO_TARGET);
            return;
        }

        int materialCost = MatrixConfig.amplifiedMineCost(target.materialPoints(), amplifierCount);
        int sourceCost = MatrixConfig.amplifiedMineCost(target.sourceCost(), amplifierCount);
        setTargetCosts(materialCost, sourceCost);
        pullMaterialPoints(materialCost);
        if (materialPoints < materialCost) {
            setOperatingState(OperatingState.MATERIAL_STARVED);
            return;
        }
        if (getSource() < sourceCost) {
            setOperatingState(OperatingState.SOURCE_STARVED);
            return;
        }
        if (cooldownTicks > 0) {
            setOperatingState(OperatingState.COOLDOWN);
            return;
        }

        if (outputContainer != null) {
            IItemHandler output = resolveItemHandler(outputContainer);
            if (output == null
                    || !ItemHandlerHelper.insertItemStacked(output, targetOutput.copy(), true).isEmpty()) {
                setOperatingState(OperatingState.OUTPUT_BLOCKED);
                return;
            }
        }

        boolean produceAmplifier = completedLayers >= MatrixConfig.mineLayerSizes().size()
                && level != null
                && level.random.nextDouble() < MatrixConfig.MINE_AMPLIFIER_DROP_CHANCE.get();
        if (produceAmplifier && !canBufferAmplifierByproduct()) {
            setOperatingState(OperatingState.OUTPUT_BLOCKED);
            return;
        }

        materialPoints -= materialCost;
        setSource(getSource() - sourceCost);
        pendingOutput = targetOutput.copy();
        if (produceAmplifier) {
            if (pendingByproduct.isEmpty()) {
                pendingByproduct = new ItemStack(ModItems.ARCANE_AMPLIFIER.get());
            } else {
                pendingByproduct.grow(1);
            }
        }
        clearTargetSelection();
        cooldownTicks = MatrixConfig.mineOperationCooldown(
                completedLayers,
                amplifierCount,
                sourceCost,
                materialCost
        );
        setOperatingState(OperatingState.COOLDOWN);
        setChangedAndSyncClient();
        flushPendingOutput();
        playCompletionEffects();
    }

    /**
     * Pulls Source from Ars Nouveau special providers. Source jars, relays and
     * Beyond Dimensions' Source Pathway expose themselves through this network
     * and wait for consuming machines to initiate the transfer.
     */
    private void pullNearbySource() {
        if (level == null || getSource() >= getMaxSource()) {
            return;
        }

        int inputRange = getSourceInputRange();
        int remainingTransfer = Math.min(
                getMaxSource() - getSource(),
                MatrixConfig.MINE_MAX_SOURCE_INPUT_PER_SECOND.get()
        );

        if (hasCompleteStructure()) {
            remainingTransfer = pullFromNearbyMatrixCores(remainingTransfer, inputRange);
        }

        for (ISpecialSourceProvider provider : SourceUtil.canTakeSource(
                worldPosition,
                level,
                inputRange
        )) {
            if (remainingTransfer <= 0) {
                break;
            }

            ISourceTile source = provider.getSource();
            if (source == null || source == this || !source.canProvideSource()) {
                continue;
            }

            remainingTransfer = pullFromSource(source, remainingTransfer);
        }
    }

    private boolean hasCompleteStructure() {
        return completedLayers >= MatrixConfig.mineLayerSizes().size();
    }

    private int getSourceInputRange() {
        int normalRange = MatrixConfig.MINE_SOURCE_INPUT_RANGE.get();
        if (!hasCompleteStructure()) {
            return normalRange;
        }
        return Math.max(normalRange, MatrixConfig.MINE_FULL_STRUCTURE_SOURCE_INPUT_RANGE.get());
    }

    /**
     * Matrix Cores are ordinary Source-capability providers rather than Ars
     * Nouveau special providers. Scan block entities from already-loaded
     * chunks so a complete mine can consume them without creating chunk loads.
     */
    private int pullFromNearbyMatrixCores(int remainingTransfer, int inputRange) {
        if (!(level instanceof ServerLevel serverLevel) || remainingTransfer <= 0) {
            return remainingTransfer;
        }

        int minChunkX = (worldPosition.getX() - inputRange) >> 4;
        int maxChunkX = (worldPosition.getX() + inputRange) >> 4;
        int minChunkZ = (worldPosition.getZ() - inputRange) >> 4;
        int maxChunkZ = (worldPosition.getZ() + inputRange) >> 4;
        double rangeSquared = (double) inputRange * inputRange;

        for (int chunkX = minChunkX; chunkX <= maxChunkX && remainingTransfer > 0; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ && remainingTransfer > 0; chunkZ++) {
                if (!serverLevel.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                LevelChunk chunk = serverLevel.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof MatrixCoreBlockEntity matrixCore)
                            || matrixCore.getBlockPos().distSqr(worldPosition) > rangeSquared
                            || !matrixCore.canProvideSource()) {
                        continue;
                    }
                    remainingTransfer = pullFromSource(matrixCore, remainingTransfer);
                    if (remainingTransfer <= 0) {
                        break;
                    }
                }
            }
        }
        return remainingTransfer;
    }

    private int pullFromSource(ISourceTile source, int remainingTransfer) {
        int offered = Math.max(0, Math.min(
                remainingTransfer,
                source.removeSource(remainingTransfer, true)
        ));
        int accepted = sourceStorage.receiveSource(offered, true);
        if (accepted <= 0) {
            return remainingTransfer;
        }

        int extracted = Math.max(0, Math.min(
                accepted,
                source.removeSource(accepted, false)
        ));
        int stored = sourceStorage.receiveSource(extracted, false);
        return remainingTransfer - stored;
    }

    @Nullable
    private ArcaneMineOreRule resolveOrChooseTarget() {
        Level currentLevel = level;
        if (currentLevel == null) {
            return null;
        }
        if (targetRuleId != null) {
            Optional<ArcaneMineOreRule> existing = ArcaneMineOreManager.find(targetRuleId);
            if (existing.isPresent() && existing.get().requiredLayers() <= completedLayers
                    && isRuleAllowed(existing.get())
                    && !targetOutput.isEmpty()) {
                return existing.get();
            }
            clearTargetSelection();
            setChangedAndSyncClient();
        }

        Optional<ArcaneMineOreRule> selected = ArcaneMineOreManager.choose(
                completedLayers,
                currentLevel.random,
                this::isRuleAllowed
        );
        if (selected.isEmpty()) {
            return null;
        }
        ItemStack output = selected.get().createOutput(currentLevel.random);
        if (output.isEmpty()) {
            return null;
        }
        int bonus = amplifierCount * MatrixConfig.MINE_OUTPUT_BONUS_PER_AMPLIFIER.get();
        output.grow(bonus);
        targetRuleId = selected.get().id();
        targetOutput = output;
        setChangedAndSyncClient();
        return selected.get();
    }

    private boolean isRuleAllowed(ArcaneMineOreRule rule) {
        return (whitelistedRules.isEmpty() || whitelistedRules.contains(rule.id()))
                && !blacklistedRules.contains(rule.id());
    }

    private void pullMaterialPoints(int targetPoints) {
        if (materialContainers.isEmpty() || materialPoints >= targetPoints) {
            return;
        }
        int attempts = materialContainers.size();
        for (int offset = 0; offset < attempts && materialPoints < targetPoints; offset++) {
            int index = Math.floorMod(inputRoundRobin + offset, materialContainers.size());
            IItemHandler handler = resolveItemHandler(materialContainers.get(index));
            if (handler == null) {
                continue;
            }
            extractConvertibleMaterials(handler, targetPoints);
        }
        inputRoundRobin = materialContainers.isEmpty()
                ? 0
                : Math.floorMod(inputRoundRobin + 1, materialContainers.size());
    }

    private void extractConvertibleMaterials(IItemHandler handler, int targetPoints) {
        for (int slot = 0; slot < handler.getSlots() && materialPoints < targetPoints; slot++) {
            ItemStack available = handler.getStackInSlot(slot);
            int value = materialValue(available);
            if (value <= 0) {
                continue;
            }
            int capacityItems = (getEffectiveMaterialPointCapacity() - materialPoints) / value;
            int neededItems = (int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(
                            1L,
                            ((long) targetPoints - materialPoints + value - 1L) / value
                    )
            );
            int requestedItems = Math.min(
                    available.getCount(),
                    Math.min(capacityItems, neededItems)
            );
            if (requestedItems <= 0) {
                continue;
            }
            ItemStack simulated = handler.extractItem(slot, requestedItems, true);
            if (simulated.isEmpty() || materialValue(simulated) != value) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, simulated.getCount(), false);
            if (!extracted.isEmpty()) {
                materialPoints += extracted.getCount() * materialValue(extracted);
                setChangedAndSyncClient();
            }
        }
    }

    private void flushPendingOutput() {
        if (outputContainer == null) {
            return;
        }
        IItemHandler output = resolveItemHandler(outputContainer);
        if (output == null) {
            return;
        }
        if (!pendingOutput.isEmpty()) {
            pendingOutput = ItemHandlerHelper.insertItemStacked(output, pendingOutput.copy(), false);
        }
        if (!pendingByproduct.isEmpty()) {
            pendingByproduct = ItemHandlerHelper.insertItemStacked(
                    output,
                    pendingByproduct.copy(),
                    false
            );
        }
        setChangedAndSyncClient();
    }

    private boolean canBufferAmplifierByproduct() {
        return pendingByproduct.isEmpty()
                || pendingByproduct.is(ModItems.ARCANE_AMPLIFIER.get())
                && pendingByproduct.getCount() < pendingByproduct.getMaxStackSize();
    }

    @Nullable
    private IItemHandler resolveItemHandler(GlobalPos globalPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (!MatrixConfig.MINE_ALLOW_CROSS_DIMENSION.get()
                && !serverLevel.dimension().equals(globalPos.dimension())) {
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        ServerLevel targetLevel = server.getLevel(globalPos.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(globalPos.pos())) {
            return null;
        }
        return targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, globalPos.pos(), null);
    }

    private void updateStructure() {
        int previous = completedLayers;
        int previousAmplifiers = amplifierCount;
        completedLayers = countContinuousLayers();
        amplifierCount = countAmplifiers(completedLayers);
        TuningScan tuning = scanTuning(completedLayers);
        boolean tuningChanged = !whitelistedRules.equals(tuning.whitelistedRules())
                || !blacklistedRules.equals(tuning.blacklistedRules());
        whitelistedRules = tuning.whitelistedRules();
        blacklistedRules = tuning.blacklistedRules();
        whitelistTuningPositions = tuning.whitelistPositions();
        blacklistTuningPositions = tuning.blacklistPositions();
        whitelistCount = whitelistedRules.size();
        blacklistCount = blacklistedRules.size();
        active = completedLayers > 0;
        if (previous != completedLayers || previousAmplifiers != amplifierCount || tuningChanged) {
            if (previousAmplifiers != amplifierCount || tuningChanged) {
                clearTargetSelection();
            }
            setChangedAndSyncClient();
            if (previous == 0 && completedLayers > 0) {
                playStructureFormedEffect();
            } else if (previous > 0 && completedLayers == 0
                    && level != null && MatrixConfig.MINE_ENABLE_SOUNDS.get()) {
                level.playSound(
                        null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.8F, 0.8F
                );
            }
        }
    }

    private int countContinuousLayers() {
        if (level == null) {
            return 0;
        }
        List<Integer> layerSizes = MatrixConfig.mineLayerSizes();
        int complete = 0;
        for (int layer = 0; layer < layerSizes.size(); layer++) {
            int size = layerSizes.get(layer);
            int radius = size / 2;
            int y = layer + 1;
            boolean valid = true;
            for (int x = -radius; x <= radius && valid; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean node = x == 0 && z == 0
                            || Math.abs(x) == radius && Math.abs(z) == radius;
                    BlockState state = level.getBlockState(worldPosition.offset(x, y, z));
                    boolean validNode = state.is(NODE_BLOCKS)
                            || x == 0 && z == 0 && state.is(ModBlocks.ARCANE_AMPLIFIER.get());
                    if (node ? !validNode : !state.is(FRAME_BLOCKS)) {
                        valid = false;
                        break;
                    }
                }
            }
            if (!valid) {
                break;
            }
            complete++;
        }
        return complete;
    }

    private int countAmplifiers(int completeLayers) {
        if (level == null) {
            return 0;
        }
        int count = 0;
        int checkedLayers = Math.min(completeLayers, MatrixConfig.MINE_AMPLIFIER_POSITIONS);
        for (int layer = 0; layer < checkedLayers; layer++) {
            if (level.getBlockState(worldPosition.above(layer + 1))
                    .is(ModBlocks.ARCANE_AMPLIFIER.get())) {
                count++;
            }
        }
        return Math.min(count, MatrixConfig.MINE_AMPLIFIER_POSITIONS);
    }

    private TuningScan scanTuning(int completeLayers) {
        Level currentLevel = level;
        if (currentLevel == null || completeLayers <= 0) {
            return TuningScan.EMPTY;
        }
        List<ArcaneMineOreRule> rules = ArcaneMineOreManager.allRules();
        Set<ResourceLocation> whitelist = new LinkedHashSet<>();
        Set<ResourceLocation> blacklist = new LinkedHashSet<>();
        List<BlockPos> whitelistPositions = new ArrayList<>();
        List<BlockPos> blacklistPositions = new ArrayList<>();
        List<Integer> layerSizes = MatrixConfig.mineLayerSizes();

        for (int layer = 0; layer < completeLayers; layer++) {
            int radius = layerSizes.get(layer) / 2;
            int yOffset = layer;
            int[][] corners = {
                    {radius, radius}, {radius, -radius},
                    {-radius, radius}, {-radius, -radius}
            };
            int[][] cardinals = {
                    {radius, 0}, {-radius, 0},
                    {0, radius}, {0, -radius}
            };
            scanTuningSamples(
                    currentLevel, corners, yOffset, rules, whitelist, whitelistPositions
            );
            scanTuningSamples(
                    currentLevel, cardinals, yOffset, rules, blacklist, blacklistPositions
            );
        }

        return new TuningScan(
                Set.copyOf(whitelist),
                Set.copyOf(blacklist),
                List.copyOf(whitelistPositions),
                List.copyOf(blacklistPositions)
        );
    }

    private void scanTuningSamples(
            Level currentLevel,
            int[][] offsets,
            int yOffset,
            List<ArcaneMineOreRule> rules,
            Set<ResourceLocation> matchedRules,
            List<BlockPos> matchedPositions
    ) {
        for (int[] offset : offsets) {
            BlockPos samplePos = worldPosition.offset(offset[0], yOffset, offset[1]);
            ItemStack sample = currentLevel.getBlockState(samplePos)
                    .getBlock()
                    .asItem()
                    .getDefaultInstance();
            boolean matched = false;
            for (ArcaneMineOreRule rule : rules) {
                if (rule.matchesTuningSample(sample)) {
                    matchedRules.add(rule.id());
                    matched = true;
                }
            }
            if (matched) {
                matchedPositions.add(samplePos);
            }
        }
    }

    private record TuningScan(
            Set<ResourceLocation> whitelistedRules,
            Set<ResourceLocation> blacklistedRules,
            List<BlockPos> whitelistPositions,
            List<BlockPos> blacklistPositions
    ) {
        private static final TuningScan EMPTY = new TuningScan(
                Set.of(), Set.of(), List.of(), List.of()
        );
    }

    private void playActiveParticles() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int count = Math.max(1, (int) Math.ceil(2.0D * MatrixConfig.MINE_PARTICLE_DENSITY.get()));
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 0.7D;
        double z = worldPosition.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y, z, count, 0.35D, 0.25D, 0.35D, 0.04D);

        int layer = (int) ((tickCounter / 20) % completedLayers);
        int radius = MatrixConfig.mineLayerSizes().get(layer) / 2;
        int node = (int) ((tickCounter / 10) % 5);
        int nodeX = node == 0 ? 0 : (node == 1 || node == 2 ? radius : -radius);
        int nodeZ = node == 0 ? 0 : (node == 1 || node == 3 ? radius : -radius);
        BlockPos nodePos = worldPosition.offset(nodeX, layer + 1, nodeZ);
        serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                nodePos.getX() + 0.5D, nodePos.getY() + 0.7D, nodePos.getZ() + 0.5D,
                Math.max(1, count / 2), 0.15D, 0.15D, 0.15D, 0.015D
        );
        playTuningBeam(serverLevel);
    }

    private void playTuningBeam(ServerLevel serverLevel) {
        int whitelistSize = whitelistTuningPositions.size();
        int total = whitelistSize + blacklistTuningPositions.size();
        if (total == 0) {
            return;
        }
        int interval = MatrixConfig.MINE_PARTICLE_INTERVAL.get();
        int index = (int) ((tickCounter / interval) % total);
        boolean whitelist = index < whitelistSize;
        BlockPos samplePos = whitelist
                ? whitelistTuningPositions.get(index)
                : blacklistTuningPositions.get(index - whitelistSize);
        DustParticleOptions particle = whitelist ? WHITELIST_PARTICLE : BLACKLIST_PARTICLE;

        double startX = samplePos.getX() + 0.5D;
        double startY = samplePos.getY() + 0.5D;
        double startZ = samplePos.getZ() + 0.5D;
        double endX = worldPosition.getX() + 0.5D;
        double endY = worldPosition.getY() + 0.6D;
        double endZ = worldPosition.getZ() + 0.5D;
        for (int step = 0; step <= 6; step++) {
            double progress = step / 6.0D;
            serverLevel.sendParticles(
                    particle,
                    startX + (endX - startX) * progress,
                    startY + (endY - startY) * progress,
                    startZ + (endZ - startZ) * progress,
                    1,
                    0.0D, 0.0D, 0.0D,
                    0.0D
            );
        }
    }

    private void playStructureFormedEffect() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.ENCHANT,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D,
                80, 1.0D, 1.0D, 1.0D, 0.12D
        );
        if (MatrixConfig.MINE_ENABLE_SOUNDS.get()) {
            serverLevel.playSound(
                    null, worldPosition, SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 1.0F, 0.8F
            );
        }
    }

    private void playRedstonePausedParticles() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                REDSTONE_PAUSED_PARTICLE,
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.65D,
                worldPosition.getZ() + 0.5D,
                1,
                0.12D, 0.08D, 0.12D,
                0.0D
        );
    }

    private void setOperatingState(OperatingState state) {
        if (operatingState != state) {
            operatingState = state;
            setChangedAndSyncClient();
        }
    }

    private void setTargetCosts(int materialCost, int sourceCost) {
        if (targetMaterialCost != materialCost || targetSourceCost != sourceCost) {
            targetMaterialCost = materialCost;
            targetSourceCost = sourceCost;
            setChangedAndSyncClient();
        }
    }

    private void clearTargetSelection() {
        targetRuleId = null;
        targetOutput = ItemStack.EMPTY;
        targetMaterialCost = 0;
        targetSourceCost = 0;
    }

    private void playCompletionEffects() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 0.6D, worldPosition.getZ() + 0.5D,
                24, 0.5D, 0.5D, 0.5D, 0.08D
        );
        if (MatrixConfig.MINE_ENABLE_SOUNDS.get()) {
            serverLevel.playSound(
                    null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 1.0F, 1.1F
            );
        }
        if (outputContainer != null) {
            ServerLevel outputLevel = serverLevel.getServer().getLevel(outputContainer.dimension());
            if (outputLevel != null && outputLevel.hasChunkAt(outputContainer.pos())) {
                BlockPos pos = outputContainer.pos();
                outputLevel.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                        8, 0.35D, 0.35D, 0.35D, 0.02D
                );
            }
        }
    }

    @Override
    public Result onFirstConnection(
            GlobalPos target,
            @Nullable Direction ignoredFace,
            @Nullable LivingEntity entity,
            Player player
    ) {
        // Starbuncle transport order: controller first, then container = store/output.
        return bindOutputContainer(target, player);
    }

    @Override
    public Result onLastConnection(
            GlobalPos target,
            @Nullable Direction ignoredFace,
            @Nullable LivingEntity entity,
            Player player
    ) {
        // Starbuncle transport order: container first, then controller = take/input.
        return bindMaterialContainer(target, player);
    }

    private boolean canBindContainer(GlobalPos target, Player player) {
        if (target == null || target.pos().equals(worldPosition)
                && level != null && target.dimension().equals(level.dimension())) {
            return false;
        }
        if (!MatrixConfig.MINE_ALLOW_CROSS_DIMENSION.get()
                && level != null && !target.dimension().equals(level.dimension())) {
            player.sendSystemMessage(Component.translatable(
                    "message.ars_arcane_matrix.arcane_mine.cross_dimension_disabled"
            ));
            return false;
        }
        return true;
    }

    private Result bindOutputContainer(GlobalPos target, Player player) {
        if (!canBindContainer(target, player)) {
            return Result.FAIL;
        }
        outputContainer = target;
        setChangedAndSyncClient();
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_mine.output_bound"
        ));
        return Result.SUCCESS;
    }

    private Result bindMaterialContainer(GlobalPos target, Player player) {
        if (!canBindContainer(target, player)) {
            return Result.FAIL;
        }
        if (!materialContainers.contains(target)
                && materialContainers.size() >= MatrixConfig.MINE_MAX_MATERIAL_CONTAINERS.get()) {
            player.sendSystemMessage(Component.translatable(
                    "message.ars_arcane_matrix.arcane_mine.input_limit"
            ));
            return Result.FAIL;
        }
        if (!materialContainers.contains(target)) {
            materialContainers.add(target);
        }
        setChangedAndSyncClient();
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_mine.input_bound",
                materialContainers.size(),
                MatrixConfig.MINE_MAX_MATERIAL_CONTAINERS.get()
        ));
        return Result.SUCCESS;
    }

    @Override
    public Result onClearConnections(Player player) {
        materialContainers.clear();
        outputContainer = null;
        setChangedAndSyncClient();
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_mine.bindings_cleared"
        ));
        return Result.SUCCESS;
    }

    public void dropBufferedContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        dropStack(pendingOutput);
        dropStack(pendingByproduct);
        int remaining = materialPoints;
        remaining = dropMaterialUnits(remaining, MatrixConfig.MINE_SOURCE_GEM_BLOCK_POINTS.get(), Items.AIR,
                ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem_block"));
        remaining = dropMaterialUnits(remaining, MatrixConfig.MINE_SOURCE_GEM_POINTS.get(), Items.AIR,
                ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem"));
        dropMaterialUnits(remaining, MatrixConfig.MINE_SOURCESTONE_POINTS.get(), Items.AIR,
                ResourceLocation.fromNamespaceAndPath("ars_nouveau", "sourcestone"));
        pendingOutput = ItemStack.EMPTY;
        pendingByproduct = ItemStack.EMPTY;
        clearTargetSelection();
        materialPoints = 0;
    }

    private int dropMaterialUnits(int points, int value, Item fallback, ResourceLocation itemId) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(fallback);
        if (item == Items.AIR) {
            return points;
        }
        int count = points / value;
        while (count > 0) {
            int stackCount = Math.min(count, item.getDefaultMaxStackSize());
            dropStack(new ItemStack(item, stackCount));
            count -= stackCount;
        }
        return points % value;
    }

    private void dropStack(ItemStack stack) {
        Level currentLevel = level;
        if (currentLevel == null || stack.isEmpty()) {
            return;
        }
        Containers.dropItemStack(
                currentLevel,
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D,
                stack.copy()
        );
    }

    private int materialValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(MATERIAL_SOURCE_GEM_BLOCK)) {
            return MatrixConfig.MINE_SOURCE_GEM_BLOCK_POINTS.get();
        }
        if (stack.is(MATERIAL_SOURCE_GEM)) {
            return MatrixConfig.MINE_SOURCE_GEM_POINTS.get();
        }
        if (stack.is(MATERIAL_SOURCESTONE)) {
            return MatrixConfig.MINE_SOURCESTONE_POINTS.get();
        }
        return 0;
    }

    private int getEffectiveMaterialPointCapacity() {
        int largestMaterialValue = Math.max(
                MatrixConfig.MINE_SOURCE_GEM_BLOCK_POINTS.get(),
                Math.max(
                        MatrixConfig.MINE_SOURCE_GEM_POINTS.get(),
                        MatrixConfig.MINE_SOURCESTONE_POINTS.get()
                )
        );
        long targetWithRoundingRoom = (long) targetMaterialCost
                + Math.max(0, largestMaterialValue - 1);
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(
                        MatrixConfig.MINE_MATERIAL_POINT_CAPACITY.get().longValue(),
                        targetWithRoundingRoom
                )
        );
    }

    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction direction) {
        if (direction == Direction.DOWN) {
            return mineralOutputHandler;
        }
        return direction != null && direction.getAxis().isHorizontal() ? materialInputHandler : null;
    }

    private SourceStorage createSourceStorage() {
        return new SourceStorage(getMaxSource(), Integer.MAX_VALUE, 0) {
            @Override
            public boolean canProvideSource(int amount) {
                return false;
            }

            @Override
            public int extractSource(int amount, boolean simulate) {
                // Internal production is the only supported extraction path.
                return super.extractSource(amount, simulate);
            }

            @Override
            public void onContentsChanged() {
                setChangedAndSyncClient();
            }
        };
    }

    private void refreshSourceLimits() {
        sourceStorage.setMaxSource(getMaxSource());
        sourceStorage.setMaxReceive(Integer.MAX_VALUE);
        sourceStorage.setMaxExtract(Integer.MAX_VALUE);
        if (sourceStorage.getSource() > getMaxSource()) {
            sourceStorage.setSource(getMaxSource());
        }
    }

    private void setChangedAndSyncClient() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public SourceStorage getSourceStorage() {
        return sourceStorage;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isRedstonePaused() {
        return redstonePaused;
    }

    public OperatingState getOperatingState() {
        return operatingState;
    }

    public int getTargetMaterialCost() {
        return targetMaterialCost;
    }

    public int getTargetSourceCost() {
        return targetSourceCost;
    }

    public int getCompletedLayers() {
        return completedLayers;
    }

    public int getAmplifierCount() {
        return amplifierCount;
    }

    public int getWhitelistCount() {
        return whitelistCount;
    }

    public int getBlacklistCount() {
        return blacklistCount;
    }

    public int getMaterialPoints() {
        return materialPoints;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getMaterialContainerCount() {
        return materialContainers.size();
    }

    public boolean hasOutputContainer() {
        return outputContainer != null;
    }

    public int getPendingByproductCount() {
        return pendingByproduct.getCount();
    }

    @Override
    public int getMaxSource() {
        return MatrixConfig.MINE_SOURCE_CAPACITY.get();
    }

    @Override
    public int getTransferRate() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canAcceptSource() {
        return getSource() < getMaxSource();
    }

    @Override
    public boolean canProvideSource() {
        return false;
    }

    @Override
    public int getSource() {
        return sourceStorage.getSource();
    }

    @Override
    public int setSource(int source) {
        sourceStorage.setSource(Math.max(0, Math.min(source, getMaxSource())));
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        return setSource((int) Math.min((long) getSource() + Math.max(0, amount), getMaxSource()));
    }

    @Override
    public int addSource(int amount, boolean simulate) {
        return sourceStorage.receiveSource(amount, simulate);
    }

    @Override
    public int removeSource(int amount) {
        return getSource();
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        return 0;
    }

    @Override
    public void getTooltip(List<Component> tooltip) {
        if (ArsNouveauAPI.ENABLE_DEBUG_NUMBERS) {
            tooltip.add(Component.translatable(
                    "tooltip.ars_arcane_matrix.arcane_mine.source_exact",
                    getSource(), getMaxSource()
            ));
        } else {
            int fullness = getMaxSource() == 0 ? 0 : getSource() * 100 / getMaxSource();
            tooltip.add(Component.translatable(
                    "tooltip.ars_arcane_matrix.arcane_mine.source_percent", fullness
            ));
        }
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.arcane_mine.layers",
                completedLayers, MatrixConfig.mineLayerSizes().size(), materialPoints
        ));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.arcane_mine.amplifiers",
                amplifierCount,
                MatrixConfig.MINE_AMPLIFIER_POSITIONS
        ));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.arcane_mine.tuning",
                whitelistCount,
                blacklistCount
        ));
    }

    private static CompoundTag saveGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong());
        return tag;
    }

    @Nullable
    private static GlobalPos loadGlobalPos(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.contains("Pos", Tag.TAG_LONG)) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return GlobalPos.of(key, BlockPos.of(tag.getLong("Pos")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CompletedLayers", completedLayers);
        tag.putInt("AmplifierCount", amplifierCount);
        tag.putInt("WhitelistCount", whitelistCount);
        tag.putInt("BlacklistCount", blacklistCount);
        tag.putInt("MaterialPoints", materialPoints);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putInt("Source", getSource());
        tag.putBoolean("RedstonePaused", redstonePaused);
        tag.putString("OperatingState", operatingState.name());
        tag.putInt("TargetMaterialCost", targetMaterialCost);
        tag.putInt("TargetSourceCost", targetSourceCost);
        tag.put("PendingOutput", pendingOutput.saveOptional(registries));
        tag.put("PendingByproduct", pendingByproduct.saveOptional(registries));
        tag.put("TargetOutput", targetOutput.saveOptional(registries));
        if (targetRuleId != null) {
            tag.putString("TargetRule", targetRuleId.toString());
        }
        if (outputContainer != null) {
            tag.put("OutputContainer", saveGlobalPos(outputContainer));
        }
        ListTag inputs = new ListTag();
        materialContainers.forEach(pos -> inputs.add(saveGlobalPos(pos)));
        tag.put("MaterialContainers", inputs);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        completedLayers = Math.max(0, tag.getInt("CompletedLayers"));
        amplifierCount = Math.max(0, Math.min(
                tag.getInt("AmplifierCount"),
                MatrixConfig.MINE_AMPLIFIER_POSITIONS
        ));
        whitelistCount = Math.max(0, tag.getInt("WhitelistCount"));
        blacklistCount = Math.max(0, tag.getInt("BlacklistCount"));
        active = completedLayers > 0;
        redstonePaused = tag.getBoolean("RedstonePaused");
        try {
            operatingState = OperatingState.valueOf(tag.getString("OperatingState"));
        } catch (IllegalArgumentException ignored) {
            operatingState = active ? OperatingState.ACTIVE : OperatingState.UNFORMED;
        }
        targetMaterialCost = Math.max(0, tag.getInt("TargetMaterialCost"));
        targetSourceCost = Math.max(0, tag.getInt("TargetSourceCost"));
        materialPoints = Math.max(0, Math.min(
                tag.getInt("MaterialPoints"), getEffectiveMaterialPointCapacity()
        ));
        cooldownTicks = Math.max(0, tag.getInt("CooldownTicks"));
        refreshSourceLimits();
        sourceStorage.setSource(Math.max(0, Math.min(tag.getInt("Source"), getMaxSource())));
        pendingOutput = tag.contains("PendingOutput", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("PendingOutput"))
                : ItemStack.EMPTY;
        pendingByproduct = tag.contains("PendingByproduct", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("PendingByproduct"))
                : ItemStack.EMPTY;
        targetOutput = tag.contains("TargetOutput", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("TargetOutput"))
                : ItemStack.EMPTY;
        targetRuleId = ResourceLocation.tryParse(tag.getString("TargetRule"));
        outputContainer = tag.contains("OutputContainer", Tag.TAG_COMPOUND)
                ? loadGlobalPos(tag.getCompound("OutputContainer"))
                : null;
        materialContainers.clear();
        ListTag inputs = tag.getList("MaterialContainers", Tag.TAG_COMPOUND);
        for (int i = 0; i < inputs.size()
                && materialContainers.size() < MatrixConfig.MINE_MAX_MATERIAL_CONTAINERS.get(); i++) {
            GlobalPos pos = loadGlobalPos(inputs.getCompound(i));
            if (pos != null && !materialContainers.contains(pos)) {
                materialContainers.add(pos);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum OperatingState {
        UNFORMED,
        REDSTONE_PAUSED,
        MATERIAL_STARVED,
        SOURCE_STARVED,
        COOLDOWN,
        OUTPUT_BLOCKED,
        NO_TARGET,
        ACTIVE
    }

    private final class MaterialInputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int value = materialValue(stack);
            if (slot != 0 || value <= 0 || stack.isEmpty()) {
                return stack;
            }
            int capacity = getEffectiveMaterialPointCapacity() - materialPoints;
            int accepted = Math.min(stack.getCount(), capacity / value);
            if (accepted <= 0) {
                return stack;
            }
            if (!simulate) {
                materialPoints += accepted * value;
                setChangedAndSyncClient();
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(accepted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && materialValue(stack) > 0;
        }
    }

    private final class MineralOutputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return switch (slot) {
                case 0 -> pendingOutput;
                case 1 -> pendingByproduct;
                default -> ItemStack.EMPTY;
            };
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stored = getStackInSlot(slot);
            if (amount <= 0 || stored.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extractedCount = Math.min(amount, stored.getCount());
            ItemStack extracted = stored.copy();
            extracted.setCount(extractedCount);
            if (!simulate) {
                stored.shrink(extractedCount);
                if (slot == 0 && stored.isEmpty()) {
                    pendingOutput = ItemStack.EMPTY;
                } else if (slot == 1 && stored.isEmpty()) {
                    pendingByproduct = ItemStack.EMPTY;
                }
                setChangedAndSyncClient();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
