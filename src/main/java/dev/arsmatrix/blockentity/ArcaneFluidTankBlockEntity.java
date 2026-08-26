package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Persistent 64-bucket standalone storage used by the Arcane Fluid Controller. */
public final class ArcaneFluidTankBlockEntity extends BlockEntity implements ITooltipProvider {
    public static final int CAPACITY = 64_000;

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override protected void onContentsChanged() { sync(); }
    };

    public ArcaneFluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_FLUID_TANK.get(), pos, state);
    }

    public IFluidHandler fluidHandler() { return tank; }
    public FluidStack fluid() { return tank.getFluid(); }

    @Override public void getTooltip(List<Component> tooltip) {
        FluidStack fluid = tank.getFluid();
        tooltip.add(fluid.isEmpty()
                ? Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_tank.empty", CAPACITY)
                : Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_tank.stored",
                        fluid.getHoverName(), fluid.getAmount(), CAPACITY));
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fluid", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("StoredAmount", tank.getFluidAmount());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fluid")) tank.readFromNBT(registries, tag.getCompound("Fluid"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
