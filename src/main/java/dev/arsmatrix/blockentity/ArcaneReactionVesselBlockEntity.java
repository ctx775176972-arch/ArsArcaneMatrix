package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import dev.arsmatrix.data.ArcaneReactionManager;
import dev.arsmatrix.data.ArcaneReactionRule;
import dev.arsmatrix.menu.ArcaneReactionVesselMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ArcaneReactionVesselBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TANK_CAPACITY = 16000;
    private static final int SOURCE_RANGE = 5;
    private final ItemStackHandler items = new ItemStackHandler(3) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 2; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler automationItems = new AutomationItems();
    private final FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override protected void onContentsChanged() { setChanged(); sync(); }
    };
    private int progress;
    private int maxProgress = 100;
    private boolean sourcePaid;
    private ResourceLocation activeRecipe;
    private State state = State.IDLE;

    public final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) { return switch (index) {
            case 0 -> progress; case 1 -> maxProgress; case 2 -> tank.getFluidAmount();
            case 3 -> BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid());
            case 4 -> state.ordinal(); default -> 0; }; }
        @Override public void set(int index, int value) { if (index == 0) progress = value; }
        @Override public int getCount() { return 5; }
    };

    public ArcaneReactionVesselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_REACTION_VESSEL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, ArcaneReactionVesselBlockEntity vessel) {
        if (level.hasNeighborSignal(pos)) { vessel.setState(State.REDSTONE_PAUSED); return; }
        List<ItemStack> inputs = List.of(vessel.items.getStackInSlot(0), vessel.items.getStackInSlot(1));
        var match = ArcaneReactionManager.findMatch(inputs, vessel.tank.getFluid());
        if (match.isEmpty()) { vessel.reset(State.MISSING_INPUT); return; }
        ArcaneReactionRule recipe = match.get();
        if (vessel.activeRecipe != null && !vessel.activeRecipe.equals(recipe.id())) vessel.reset(State.MISSING_INPUT);
        vessel.activeRecipe = recipe.id();
        vessel.maxProgress = recipe.processingTicks();
        if (!vessel.canAccept(recipe)) { vessel.setState(State.OUTPUT_BLOCKED); return; }
        if (!vessel.sourcePaid) {
            if (!vessel.consumeSource(recipe.sourceCost())) { vessel.setState(State.MISSING_SOURCE); return; }
            vessel.sourcePaid = true;
        }
        vessel.setState(State.RUNNING);
        if (++vessel.progress < vessel.maxProgress) { vessel.setChanged(); return; }
        recipe.consumeItems(inputs);
        if (recipe.inputFluidAmount() > 0) vessel.tank.drain(recipe.inputFluidAmount(), IFluidHandler.FluidAction.EXECUTE);
        ItemStack itemOutput = recipe.createItemOutput();
        if (!itemOutput.isEmpty()) {
            ItemStack stored = vessel.items.getStackInSlot(2);
            if (stored.isEmpty()) vessel.items.setStackInSlot(2, itemOutput);
            else stored.grow(itemOutput.getCount());
        }
        FluidStack fluidOutput = recipe.createFluidOutput();
        if (!fluidOutput.isEmpty()) vessel.tank.fill(fluidOutput, IFluidHandler.FluidAction.EXECUTE);
        vessel.progress = 0;
        vessel.sourcePaid = false;
        vessel.activeRecipe = null;
        vessel.sync();
    }

    private boolean canAccept(ArcaneReactionRule recipe) {
        ItemStack output = recipe.createItemOutput();
        ItemStack stored = items.getStackInSlot(2);
        if (!output.isEmpty() && !stored.isEmpty()
                && (!ItemStack.isSameItemSameComponents(stored, output)
                || stored.getCount() + output.getCount() > stored.getMaxStackSize())) return false;
        FluidStack fluid = recipe.createFluidOutput();
        if (fluid.isEmpty()) return true;
        FluidStack remaining = tank.getFluid().copy();
        if (recipe.inputFluidAmount() > 0) remaining.shrink(recipe.inputFluidAmount());
        return remaining.isEmpty()
                ? fluid.getAmount() <= TANK_CAPACITY
                : FluidStack.isSameFluidSameComponents(remaining, fluid)
                        && remaining.getAmount() + fluid.getAmount() <= TANK_CAPACITY;
    }

    private boolean consumeSource(int cost) {
        if (cost <= 0 || level == null) return true;
        var providers = SourceUtil.canTakeSource(worldPosition, level, SOURCE_RANGE);
        int available = 0;
        for (ISpecialSourceProvider provider : providers) {
            ISourceTile source = provider.getSource();
            if (source != null && source.canProvideSource()) {
                int needed = cost - available;
                available += Math.max(0, Math.min(needed, source.removeSource(needed, true)));
                if (available >= cost) break;
            }
        }
        if (available < cost) return false;
        int remaining = cost;
        for (ISpecialSourceProvider provider : providers) {
            if (remaining <= 0) break;
            ISourceTile source = provider.getSource();
            if (source != null && source.canProvideSource()) remaining -= Math.max(0,
                    Math.min(remaining, source.removeSource(remaining, false)));
        }
        return remaining == 0;
    }

    private void reset(State next) {
        if (progress != 0 || sourcePaid || activeRecipe != null) {
            progress = 0; sourcePaid = false; activeRecipe = null; setChanged();
        }
        setState(next);
    }
    private void setState(State next) { if (state != next) { state = next; sync(); } }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }

    public ItemStackHandler items() { return items; }
    public IItemHandler itemHandler(@Nullable Direction side) { return automationItems; }
    public IFluidHandler fluidHandler(@Nullable Direction side) { return tank; }
    public FluidTank tank() { return tank; }
    public State state() { return state; }
    public void clearFluid() {
        tank.setFluid(FluidStack.EMPTY);
        reset(State.IDLE);
        sync();
    }
    public Container asContainer() { return new SimpleContainer(items.getStackInSlot(0).copy(), items.getStackInSlot(1).copy(), items.getStackInSlot(2).copy()); }

    @Override public Component getDisplayName() { return Component.translatable("block.ars_arcane_matrix.arcane_reaction_vessel"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ArcaneReactionVesselMenu(id, inventory, this); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Progress", progress); tag.putInt("MaxProgress", maxProgress);
        tag.putBoolean("SourcePaid", sourcePaid); tag.putInt("State", state.ordinal());
        if (activeRecipe != null) tag.putString("ActiveRecipe", activeRecipe.toString());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) items.deserializeNBT(registries, tag.getCompound("Items"));
        if (tag.contains("Tank")) {
            tank.readFromNBT(registries, tag.getCompound("Tank"));
        } else {
            FluidTank oldInput = new FluidTank(TANK_CAPACITY);
            FluidTank oldOutput = new FluidTank(TANK_CAPACITY);
            if (tag.contains("InputTank")) oldInput.readFromNBT(registries, tag.getCompound("InputTank"));
            if (tag.contains("OutputTank")) oldOutput.readFromNBT(registries, tag.getCompound("OutputTank"));
            FluidStack input = oldInput.getFluid();
            FluidStack output = oldOutput.getFluid();
            if (!output.isEmpty()) {
                tank.setFluid(output.copy());
                if (!input.isEmpty() && FluidStack.isSameFluidSameComponents(input, output)) {
                    tank.getFluid().setAmount(Math.min(TANK_CAPACITY, input.getAmount() + output.getAmount()));
                }
            } else if (!input.isEmpty()) {
                tank.setFluid(input.copy());
            }
        }
        progress = Math.max(0, tag.getInt("Progress")); maxProgress = Math.max(1, tag.getInt("MaxProgress"));
        sourcePaid = tag.getBoolean("SourcePaid");
        state = State.values()[Math.floorMod(tag.getInt("State"), State.values().length)];
        activeRecipe = ResourceLocation.tryParse(tag.getString("ActiveRecipe"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum State { IDLE, RUNNING, MISSING_INPUT, MISSING_SOURCE, OUTPUT_BLOCKED, REDSTONE_PAUSED }

    private final class AutomationItems implements IItemHandler {
        @Override public int getSlots() { return 3; }
        @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot < 2 ? items.insertItem(slot, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return slot == 2 ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 2; }
    }
}
