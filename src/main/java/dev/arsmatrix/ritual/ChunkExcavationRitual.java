package dev.arsmatrix.ritual;

import com.hollingsworth.arsnouveau.api.ANFakePlayer;
import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.util.StructureInventoryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/** Mines one complete 16x16 chunk layer per second below the ritual brazier. */
public final class ChunkExcavationRitual extends AbstractRitual {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "chunk_excavation");
    private static final int SOURCE_PER_LAYER = 1_000;
    private int pendingSourceCost;

    @Override
    protected void tick() {
        if (!(getWorld() instanceof ServerLevel level) || getPos() == null) return;
        if (level.getGameTime() % 20L != 0L) return;

        BlockPos brazierPos = getPos();
        int targetY = brazierPos.getY() - 1 - getProgress();
        if (targetY < level.getMinBuildHeight()) {
            setFinished();
            return;
        }

        ChunkPos chunk = new ChunkPos(brazierPos);
        List<BlockPos> layer = new ArrayList<>(256);
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                BlockPos pos = new BlockPos(x, targetY, z);
                layer.add(pos);
            }
        }

        ItemStack tool = createTool(level, didConsumeItem(Items.DIAMOND), didConsumeItem(Items.EMERALD));
        boolean clearFluids = didConsumeItem(Items.SPONGE);
        ANFakePlayer fakePlayer = ANFakePlayer.getPlayer(level);
        List<PlannedBlock> planned = new ArrayList<>();
        List<ItemStack> allDrops = new ArrayList<>();

        for (BlockPos pos : layer) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            boolean containsFluid = !state.getFluidState().isEmpty();
            if (containsFluid && !clearFluids) continue;
            // Inventories, machines and other data-bearing blocks are preserved.
            if (state.hasBlockEntity()) continue;
            // Preserve Bedrock and modded unbreakable blocks without stopping
            // excavation of the rest of this layer or any lower layer.
            if (state.getDestroySpeed(level, pos) < 0.0F) continue;
            if (!BlockUtil.destroyRespectsClaim(fakePlayer, level, pos)) continue;

            if (containsFluid && state.getBlock() instanceof LiquidBlock) {
                planned.add(new PlannedBlock(pos, List.of()));
                continue;
            }
            if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) continue;

            List<ItemStack> drops = Block.getDrops(state, level, pos, null, fakePlayer, tool);
            planned.add(new PlannedBlock(pos, drops));
            drops.stream().filter(drop -> !drop.isEmpty()).map(ItemStack::copy).forEach(allDrops::add);
        }

        if (planned.isEmpty()) {
            incrementProgress();
            return;
        }

        List<IItemHandler> outputs = nearbyOutputs(level, brazierPos);
        if (!allDrops.isEmpty() && !canInsertAll(outputs, allDrops)) return;

        pendingSourceCost = SOURCE_PER_LAYER;
        if (!takeSourceNow()) return;

        for (PlannedBlock block : planned) {
            level.removeBlock(block.pos(), false);
            for (ItemStack drop : block.drops()) {
                ItemStack remainder = insertAcross(outputs, drop.copy());
                // Defensive fallback for inventories whose capability changes during this tick.
                if (!remainder.isEmpty()) Block.popResource(level, brazierPos.above(), remainder);
            }
        }
        incrementProgress();
    }

    private static ItemStack createTool(ServerLevel level, boolean diamondTier, boolean silkTouch) {
        ItemStack tool = new ItemStack(diamondTier ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
        if (silkTouch) {
            Holder<Enchantment> silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SILK_TOUCH);
            var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                    EnchantmentHelper.getEnchantmentsForCrafting(tool));
            mutable.set(silk, 1);
            EnchantmentHelper.setEnchantments(tool, mutable.toImmutable());
        }
        return tool;
    }

    private static List<IItemHandler> nearbyOutputs(ServerLevel level, BlockPos center) {
        List<IItemHandler> outputs = new ArrayList<>();
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    IItemHandler handler = StructureInventoryAccess.at(level, center.offset(x, y, z));
                    if (handler != null && !outputs.contains(handler)) outputs.add(handler);
                }
            }
        }
        return outputs;
    }

    private static boolean canInsertAll(List<IItemHandler> outputs, List<ItemStack> stacks) {
        List<VirtualSlot> slots = new ArrayList<>();
        for (IItemHandler output : outputs) {
            for (int slot = 0; slot < output.getSlots(); slot++) {
                slots.add(new VirtualSlot(output, slot, output.getStackInSlot(slot).copy()));
            }
        }
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (VirtualSlot slot : slots) {
                if (remaining.isEmpty()) break;
                remaining = slot.reserve(remaining);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private static ItemStack insertAcross(List<IItemHandler> outputs, ItemStack stack) {
        ItemStack remaining = stack;
        for (IItemHandler output : outputs) {
            if (remaining.isEmpty()) break;
            remaining = ItemHandlerHelper.insertItemStacked(output, remaining, false);
        }
        return remaining;
    }

    @Override
    public boolean canConsumeItem(ItemStack stack) {
        if (stack.is(Items.DIAMOND)) return !didConsumeItem(Items.DIAMOND);
        if (stack.is(Items.SPONGE)) return !didConsumeItem(Items.SPONGE);
        if (stack.is(Items.EMERALD)) return !didConsumeItem(Items.EMERALD);
        return false;
    }

    @Override public int getSourceCost() { return pendingSourceCost; }
    @Override public ResourceLocation getRegistryName() { return ID; }
    @Override public String getLangName() { return "Deep Excavation"; }
    @Override public String getLangDescription() {
        return "Excavates one complete chunk layer below the brazier each second.";
    }

    private record PlannedBlock(BlockPos pos, List<ItemStack> drops) {}

    /** A non-mutating reservation used to ensure the complete layer fits before mining. */
    private static final class VirtualSlot {
        private final IItemHandler handler;
        private final int slot;
        private ItemStack virtual;

        private VirtualSlot(IItemHandler handler, int slot, ItemStack virtual) {
            this.handler = handler;
            this.slot = slot;
            this.virtual = virtual;
        }

        private ItemStack reserve(ItemStack input) {
            if (!virtual.isEmpty() && !ItemStack.isSameItemSameComponents(virtual, input)) return input;
            // Probe the slot with a complete stack. Probing with the usually
            // one-item block drop would incorrectly make an empty 64-slot look
            // as though it only had room for that single item.
            int probeCount = Math.min(input.getMaxStackSize(), handler.getSlotLimit(slot));
            ItemStack probe = input.copyWithCount(probeCount);
            ItemStack rejected = handler.insertItem(slot, probe, true);
            int acceptedByHandler = probeCount - rejected.getCount();
            int actualCount = handler.getStackInSlot(slot).getCount();
            int alreadyReserved = Math.max(0, virtual.getCount() - actualCount);
            int moved = Math.min(input.getCount(), Math.max(0, acceptedByHandler - alreadyReserved));
            if (moved <= 0) return input;
            if (virtual.isEmpty()) virtual = input.copyWithCount(moved);
            else virtual.grow(moved);
            return input.copyWithCount(input.getCount() - moved);
        }
    }
}
