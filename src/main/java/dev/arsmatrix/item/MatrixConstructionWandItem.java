package dev.arsmatrix.item;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.block.ArcaneCrusherCoreBlock;
import dev.arsmatrix.block.ArcaneProcessorCoreBlock;
import dev.arsmatrix.block.ArcaneSmelterCoreBlock;
import dev.arsmatrix.blockentity.ArcaneCrusherCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneProcessorCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneSmelterCoreBlockEntity;
import dev.arsmatrix.blockentity.DimensionAnchorBlockEntity;
import dev.arsmatrix.block.SuperSourceJarCoreBlock;
import dev.arsmatrix.blockentity.SuperSourceJarCoreBlockEntity;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.util.MultiblockClearance;
import dev.arsmatrix.util.StructureInventoryAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Places missing blocks from the same definitions used by structure previews. */
public final class MatrixConstructionWandItem extends Item {
    private static final Block SOURCESTONE = arsBlock("sourcestone");
    private static final Block SOURCE_GEM_BLOCK = arsBlock("source_gem_block");
    private static final Block ARCANE_PEDESTAL = arsBlock("arcane_pedestal");
    private static final TagKey<Block> MATRIX_FRAME = tag("matrix_frame_blocks");
    private static final TagKey<Block> MINE_FRAME = tag("arcane_mine_frame_blocks");
    private static final TagKey<Block> MINE_BASIC_FRAME = tag("arcane_mine_basic_frame_blocks");
    private static final TagKey<Block> MINE_NODE = tag("arcane_mine_node_blocks");
    private static final TagKey<Block> PROCESSOR_FRAME = tag("arcane_processor_frame_blocks");
    private static final TagKey<Block> SMELTER_FRAME = tag("arcane_smelter_frame_blocks");
    private static final TagKey<Block> CRUSHER_FRAME = tag("arcane_crusher_frame_blocks");

    public MatrixConstructionWandItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.construction_wand.single")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.construction_wand.all")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.construction_wand.creative")
                .withStyle(ChatFormatting.AQUA));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        List<Placement> structure = structureFor(context.getLevel(), context.getClickedPos());
        if (structure.isEmpty()) {
            if (!context.getLevel().isClientSide) player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.construction_wand.unsupported"), true);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;

        boolean placeAll = player.isShiftKeyDown() || player.getAbilities().instabuild;
        int placed = 0;
        int blocked = 0;
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (Placement placement : structure) {
            BlockState current = context.getLevel().getBlockState(placement.pos());
            if (placement.satisfied(context.getLevel(), current)) continue;
            // Required-clearance positions are validated but never cleared automatically.
            // This makes structure migrations safe for existing worlds.
            if (placement.kind() == Kind.CLEARANCE) {
                blocked++;
                continue;
            }
            if (!context.getLevel().isInWorldBounds(placement.pos()) || !current.canBeReplaced()) {
                blocked++;
                continue;
            }
            Item required = placement.block().asItem();
            if (!player.getAbilities().instabuild && !consumeOne(player.getInventory(), required)) {
                missing.merge(required, 1, Integer::sum);
                continue;
            }
            context.getLevel().setBlockAndUpdate(placement.pos(), placement.block().defaultBlockState());
            placed++;
            if (!placeAll) break;
        }

        if (placed > 0) player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.construction_wand.placed", placed), true);
        else if (missing.isEmpty() && blocked == 0) player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.construction_wand.complete"), true);
        if (!missing.isEmpty()) player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.construction_wand.missing", describeMissing(missing)), false);
        if (blocked > 0) player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.construction_wand.blocked", blocked).withStyle(ChatFormatting.RED), false);
        return InteractionResult.SUCCESS;
    }

    private static boolean consumeOne(Inventory inventory, Item required) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(required)) continue;
            stack.shrink(1);
            inventory.setChanged();
            return true;
        }
        return false;
    }

    private static Component describeMissing(Map<Item, Integer> missing) {
        Component result = Component.empty();
        boolean first = true;
        for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
            if (!first) result = result.copy().append(Component.literal("、"));
            result = result.copy().append(entry.getKey().getName(new ItemStack(entry.getKey())))
                    .append(Component.literal(" ×" + entry.getValue()));
            first = false;
        }
        return result;
    }

    private static List<Placement> structureFor(Level level, BlockPos core) {
        Block block = level.getBlockState(core).getBlock();
        List<Placement> result = new ArrayList<>();
        if (block == ModBlocks.MATRIX_CORE.get()) addMatrix(result, core);
        else if (block == ModBlocks.ARCANE_MINE_CORE.get()) addMine(result, core);
        else if (block == ModBlocks.ARCANE_PROCESSOR_CORE.get()) addProcessor(level, result, core);
        else if (block == ModBlocks.ARCANE_SMELTER_CORE.get()) addSmelter(level, result, core);
        else if (block == ModBlocks.ARCANE_CRUSHER_CORE.get()) addCrusher(level, result, core);
        else if (block == ModBlocks.SUPER_SOURCE_JAR_CORE.get()) addMatrixSourceReservoir(level, result, core);
        else if (block == ModBlocks.DIMENSION_ANCHOR.get()) addDimensionAnchor(result, core);
        result.sort(Comparator.comparingInt((Placement p) -> p.pos().getY())
                .thenComparingDouble(p -> p.pos().distSqr(core)));
        return result;
    }

    private static void addMatrix(List<Placement> result, BlockPos core) {
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            if (!(x == 0 && (Math.abs(y) == 2 || Math.abs(z) == 2)
                    || y == 0 && (Math.abs(x) == 2 || Math.abs(z) == 2)
                    || z == 0 && (Math.abs(x) == 2 || Math.abs(y) == 2))) continue;
            boolean amplifierAllowed = Math.abs(x) == 2 && y == 0 && z == 0
                    || x == 0 && Math.abs(y) == 2 && z == 0
                    || x == 0 && y == 0 && Math.abs(z) == 2;
            result.add(new Placement(core.offset(x, y, z), ModBlocks.ARCANE_STRUCTURAL_FRAME.get(),
                    amplifierAllowed ? Kind.MATRIX_AMPLIFIER : Kind.MATRIX_FRAME));
        }
    }

    private static void addMine(List<Placement> result, BlockPos core) {
        List<Integer> sizes = MatrixConfig.mineLayerSizes();
        for (int layer = 0; layer < sizes.size(); layer++) {
            int radius = sizes.get(layer) / 2;
            int y = layer + 1;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                boolean center = x == 0 && z == 0;
                boolean node = center || Math.abs(x) == radius && Math.abs(z) == radius;
                boolean blacklistAnchor = !node && (Math.abs(x) == radius && z == 0
                        || x == 0 && Math.abs(z) == radius);
                Block frame = layer == 0 || blacklistAnchor
                        ? SOURCESTONE : ModBlocks.ARCANE_STRUCTURAL_FRAME.get();
                Kind kind = center ? Kind.MINE_CENTER : node ? Kind.MINE_NODE
                        : layer == 0 || blacklistAnchor ? Kind.MINE_BASIC_FRAME : Kind.MINE_FRAME;
                result.add(new Placement(core.offset(x, y, z), node ? SOURCE_GEM_BLOCK : frame, kind));
            }
        }
    }

    private static void addProcessor(Level level, List<Placement> result, BlockPos core) {
        Direction facing = level.getBlockState(core).getValue(ArcaneProcessorCoreBlock.FACING);
        ArcaneProcessorCoreBlockEntity.framePositions(core, facing).forEach(pos ->
                result.add(new Placement(pos, ModBlocks.ARCANE_STRUCTURAL_FRAME.get(), Kind.PROCESSOR_FRAME)));
        result.add(new Placement(ArcaneProcessorCoreBlockEntity.toolPedestalPosition(core, facing),
                ARCANE_PEDESTAL, Kind.EXACT));
        result.add(new Placement(ArcaneProcessorCoreBlockEntity.foodContainerPosition(core, facing),
                net.minecraft.world.level.block.Blocks.BARREL, Kind.CONSUMABLE_CONTAINER));
    }

    private static void addSmelter(Level level, List<Placement> result, BlockPos core) {
        Direction facing = level.getBlockState(core).getValue(ArcaneSmelterCoreBlock.FACING);
        ArcaneSmelterCoreBlockEntity.framePositions(core, facing)
                .forEach(pos -> result.add(new Placement(pos, ModBlocks.ARCANE_STRUCTURAL_FRAME.get(), Kind.SMELTER_FRAME)));
        result.add(new Placement(ArcaneSmelterCoreBlockEntity.consumableContainerPosition(core),
                net.minecraft.world.level.block.Blocks.BARREL, Kind.CONSUMABLE_CONTAINER));
    }

    private static void addCrusher(Level level, List<Placement> result, BlockPos core) {
        Direction facing = level.getBlockState(core).getValue(ArcaneCrusherCoreBlock.FACING);
        ArcaneCrusherCoreBlockEntity.framePositions(core, facing)
                .forEach(pos -> result.add(new Placement(pos, ModBlocks.ARCANE_STRUCTURAL_FRAME.get(), Kind.CRUSHER_FRAME)));
        result.add(new Placement(ArcaneCrusherCoreBlockEntity.consumableContainerPosition(core, facing),
                net.minecraft.world.level.block.Blocks.BARREL, Kind.CONSUMABLE_CONTAINER));
    }

    private static void addMatrixSourceReservoir(Level level, List<Placement> result, BlockPos core) {
        Direction facing = level.getBlockState(core).getValue(SuperSourceJarCoreBlock.FACING);
        for (SuperSourceJarCoreBlockEntity.StructurePart part
                : SuperSourceJarCoreBlockEntity.structureParts(core, facing)) {
            Block block = part.kind() == SuperSourceJarCoreBlockEntity.PartKind.FRAME
                    ? ModBlocks.ARCANE_STRUCTURAL_FRAME.get()
                    : net.minecraft.world.level.block.Blocks.TINTED_GLASS;
            result.add(new Placement(part.pos(), block, Kind.EXACT));
        }
    }

    private static void addDimensionAnchor(List<Placement> result, BlockPos anchor) {
        DimensionAnchorBlockEntity.expansionFramePositions(anchor).forEach(pos ->
                result.add(new Placement(pos, ModBlocks.ARCANE_STRUCTURAL_FRAME.get(), Kind.EXACT)));
    }

    private record Placement(BlockPos pos, Block block, Kind kind) {
        boolean satisfied(Level level, BlockState state) {
            return switch (kind) {
                case EXACT -> state.is(block);
                case CLEARANCE -> MultiblockClearance.isOpen(level, pos);
                case MATRIX_FRAME -> state.is(MATRIX_FRAME);
                case MATRIX_AMPLIFIER -> state.is(MATRIX_FRAME) || state.is(ModBlocks.ARCANE_AMPLIFIER.get());
                case MINE_FRAME -> state.is(MINE_FRAME);
                case MINE_BASIC_FRAME -> state.is(MINE_BASIC_FRAME);
                case MINE_NODE -> state.is(MINE_NODE);
                case MINE_CENTER -> state.is(MINE_NODE) || state.is(ModBlocks.ARCANE_AMPLIFIER.get());
                case PROCESSOR_FRAME -> state.is(PROCESSOR_FRAME);
                case SMELTER_FRAME -> state.is(SMELTER_FRAME);
                case CRUSHER_FRAME -> state.is(CRUSHER_FRAME);
                case CONSUMABLE_CONTAINER -> StructureInventoryAccess.at(level, pos) != null;
            };
        }
    }

    private enum Kind { EXACT, CLEARANCE, MATRIX_FRAME, MATRIX_AMPLIFIER, MINE_FRAME, MINE_BASIC_FRAME, MINE_NODE, MINE_CENTER,
        PROCESSOR_FRAME, SMELTER_FRAME, CRUSHER_FRAME, CONSUMABLE_CONTAINER }
    private static Block arsBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", path));
    }
    private static TagKey<Block> tag(String path) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, path));
    }
}
