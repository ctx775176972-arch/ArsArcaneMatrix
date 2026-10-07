package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import dev.arsmatrix.block.SourceStoneFurnaceBlock;
import dev.arsmatrix.menu.SourceStoneFurnaceMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SourceStoneFurnaceBlockEntity extends BlockEntity implements MenuProvider {
    public static final int PROCESS_TICKS = 100;
    public static final int SOURCE_COST = 20;
    public static final int SOURCE_BUFFER_CAPACITY = 200;
    private static final int SOURCE_RANGE = 5;
    private static final int LIT_HOLD_TICKS = 10;

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && (level == null || findRecipe(stack) != null);
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler topHandler = new FaceHandler(true);
    private final IItemHandler bottomHandler = new FaceHandler(false);
    private int progress;
    private boolean sourcePaid;
    private int sourceBuffer;
    private long lastSourceRefillTick = Long.MIN_VALUE;
    private long lastFailedSourceRefillTick = Long.MIN_VALUE;
    private int litHoldTicks;
    /** Locked while one Advanced Lectern/Wixie machine task owns both slots. */
    private boolean networkReserved;

    public final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> PROCESS_TICKS;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 0) progress = value;
        }
        @Override public int getCount() { return 2; }
    };

    public SourceStoneFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOURCE_STONE_FURNACE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SourceStoneFurnaceBlockEntity furnace) {
        if (furnace.litHoldTicks > 0) furnace.litHoldTicks--;
        ItemStack input = furnace.inventory.getStackInSlot(0);
        AbstractCookingRecipe recipe = furnace.findRecipe(input);
        ItemStack output = recipe == null ? ItemStack.EMPTY
                : recipe.assemble(new SingleRecipeInput(input.copyWithCount(1)), level.registryAccess());
        if (recipe == null || output.isEmpty() || !furnace.canOutput(output)) {
            furnace.setLit(furnace.litHoldTicks > 0);
            if (recipe == null || input.isEmpty()) {
                furnace.progress = 0;
                furnace.sourcePaid = false;
                furnace.setChanged();
            }
            return;
        }
        furnace.refillSourceBuffer();
        if (!furnace.sourcePaid) {
            if (!furnace.consumeSource(SOURCE_COST)) {
                furnace.setLit(furnace.litHoldTicks > 0);
                return;
            }
            furnace.sourcePaid = true;
            furnace.setChanged();
        }
        furnace.litHoldTicks = LIT_HOLD_TICKS;
        furnace.setLit(true);
        furnace.progress++;
        if (furnace.progress < PROCESS_TICKS) {
            furnace.setChanged();
            return;
        }
        input.shrink(1);
        ItemStack stored = furnace.inventory.getStackInSlot(1);
        if (stored.isEmpty()) furnace.inventory.setStackInSlot(1, output.copy());
        else stored.grow(output.getCount());
        furnace.progress = 0;
        furnace.sourcePaid = false;
        furnace.setChanged();
    }

    @Nullable private AbstractCookingRecipe findRecipe(ItemStack input) {
        if (level == null || input.isEmpty()) return null;
        SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
        Optional<RecipeHolder<SmokingRecipe>> smoking = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMOKING, recipeInput, level);
        if (smoking.isPresent()) return smoking.get().value();
        Optional<RecipeHolder<SmeltingRecipe>> smelting = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, recipeInput, level);
        return smelting.map(RecipeHolder::value).orElse(null);
    }

    private boolean canOutput(ItemStack output) {
        ItemStack stored = inventory.getStackInSlot(1);
        return stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, output)
                && stored.getCount() + output.getCount() <= stored.getMaxStackSize();
    }

    private boolean consumeSource(int cost) {
        if (cost <= 0 || sourceBuffer < cost) return false;
        sourceBuffer -= cost;
        setChanged();
        return true;
    }

    /** Refill only when a valid recipe has output space; retain partial transfers. */
    private void refillSourceBuffer() {
        if (level == null || sourceBuffer >= SOURCE_BUFFER_CAPACITY) return;
        long now = level.getGameTime();
        // Extra block-entity ticks from acceleration share the same world time.
        // Successful emergency refills must not be throttled by that clock.
        if (lastFailedSourceRefillTick != Long.MIN_VALUE
                && now - lastFailedSourceRefillTick < 20) return;
        if (sourceBuffer >= SOURCE_COST && lastSourceRefillTick != Long.MIN_VALUE
                && now - lastSourceRefillTick < 20) return;
        lastSourceRefillTick = now;
        var providers = SourceUtil.canTakeSource(worldPosition, level, SOURCE_RANGE);
        for (ISpecialSourceProvider provider : providers) {
            ISourceTile source = provider.getSource();
            if (source != null && source.canProvideSource()) {
                int needed = SOURCE_BUFFER_CAPACITY - sourceBuffer;
                int extracted = Math.max(0, Math.min(needed, source.removeSource(needed, false)));
                if (extracted > 0) {
                    sourceBuffer += extracted;
                    setChanged();
                }
                if (sourceBuffer >= SOURCE_BUFFER_CAPACITY) break;
            }
        }
        // Only back off when another operation cannot be paid. This bounds
        // scans under 256x acceleration without limiting available throughput.
        lastFailedSourceRefillTick = sourceBuffer < SOURCE_COST ? now : Long.MIN_VALUE;
    }

    private void setLit(boolean lit) {
        if (level == null || getBlockState().getValue(SourceStoneFurnaceBlock.LIT) == lit) return;
        level.setBlock(worldPosition, getBlockState().setValue(SourceStoneFurnaceBlock.LIT, lit),
                Block.UPDATE_CLIENTS);
    }

    public ItemStackHandler inventory() { return inventory; }

    public boolean isAvailableForNetworkJob() {
        return !networkReserved && inventory.getStackInSlot(0).isEmpty()
                && inventory.getStackInSlot(1).isEmpty();
    }

    /**
     * Returns manual leftovers before a Wixie reserves this furnace. Progress and
     * prepaid Source belong to that old input and must not carry into a new recipe.
     */
    public List<ItemStack> takeUnreservedContents() {
        if (networkReserved) return List.of();
        List<ItemStack> removed = new ArrayList<>(2);
        ItemStack input = inventory.extractItem(0, Integer.MAX_VALUE, false);
        ItemStack output = inventory.extractItem(1, Integer.MAX_VALUE, false);
        if (!input.isEmpty()) removed.add(input);
        if (!output.isEmpty()) removed.add(output);
        if (!removed.isEmpty()) {
            progress = 0;
            sourcePaid = false;
            litHoldTicks = 0;
            setLit(false);
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return List.copyOf(removed);
    }

    /** Atomically reserves this furnace and loads one complete network batch. */
    public boolean startNetworkJob(ItemStack input) {
        if (level == null || level.isClientSide || input.isEmpty() || !isAvailableForNetworkJob()
                || findRecipe(input) == null) return false;
        ItemStack remainder = inventory.insertItem(0, input.copy(), false);
        if (!remainder.isEmpty()) return false;
        networkReserved = true;
        setChanged();
        return true;
    }

    /** Returns the completed result and releases the reservation, or an empty stack while working. */
    public ItemStack takeNetworkResult(ItemStack expected) {
        if (!networkReserved || expected.isEmpty() || !inventory.getStackInSlot(0).isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack output = inventory.getStackInSlot(1);
        if (!ItemStack.isSameItemSameComponents(output, expected)) return ItemStack.EMPTY;
        // No input remains, so this reservation cannot produce another result.
        // Accepting the actual amount also recovers batches made by older builds
        // that accidentally inserted only their first inventory fragment.
        ItemStack extracted = inventory.extractItem(
                1, Math.min(expected.getCount(), output.getCount()), false);
        networkReserved = false;
        setChanged();
        return extracted;
    }

    /** Cancels a stale reservation and returns everything still held by the furnace. */
    public List<ItemStack> cancelNetworkJob() {
        List<ItemStack> recovered = new ArrayList<>(2);
        ItemStack input = inventory.extractItem(0, Integer.MAX_VALUE, false);
        ItemStack output = inventory.extractItem(1, Integer.MAX_VALUE, false);
        if (!input.isEmpty()) recovered.add(input);
        if (!output.isEmpty()) recovered.add(output);
        networkReserved = false;
        progress = 0;
        sourcePaid = false;
        litHoldTicks = 0;
        setLit(false);
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        setChanged();
        return List.copyOf(recovered);
    }

    public boolean isNetworkReserved() { return networkReserved; }
    public IItemHandler itemHandler(@Nullable Direction side) {
        if (side == Direction.UP) return topHandler;
        if (side == Direction.DOWN) return bottomHandler;
        return side == null ? inventory : null;
    }
    public Container asContainer() {
        return new SimpleContainer(inventory.getStackInSlot(0).copy(), inventory.getStackInSlot(1).copy());
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.source_stone_furnace");
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SourceStoneFurnaceMenu(id, inv, this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putBoolean("SourcePaid", sourcePaid);
        tag.putInt("SourceBuffer", sourceBuffer);
        tag.putBoolean("NetworkReserved", networkReserved);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        progress = Math.max(0, Math.min(PROCESS_TICKS, tag.getInt("Progress")));
        sourcePaid = tag.getBoolean("SourcePaid");
        sourceBuffer = Math.max(0, Math.min(SOURCE_BUFFER_CAPACITY, tag.getInt("SourceBuffer")));
        lastSourceRefillTick = Long.MIN_VALUE;
        lastFailedSourceRefillTick = Long.MIN_VALUE;
        networkReserved = tag.getBoolean("NetworkReserved");
    }

    private final class FaceHandler implements IItemHandler {
        private final boolean input;
        private FaceHandler(boolean input) { this.input = input; }
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inventory.getStackInSlot(input ? 0 : 1) : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return input && slot == 0 && !networkReserved
                    ? inventory.insertItem(0, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return !input && slot == 0 && !networkReserved
                    ? inventory.extractItem(1, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(input ? 0 : 1); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return input && slot == 0; }
    }
}
