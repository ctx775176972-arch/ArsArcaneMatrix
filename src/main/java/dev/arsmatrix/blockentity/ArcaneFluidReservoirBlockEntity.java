//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.item.IWandable.Result;
import dev.arsmatrix.menu.ArcaneFluidReservoirMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public final class ArcaneFluidReservoirBlockEntity extends BlockEntity implements MenuProvider, IWandable, ITooltipProvider {
    public static final int UPGRADE_SLOTS = 4;
    public static final int MODULE_SLOTS = 2;
    public static final int MAX_TANKS = 3;
    public static final int BASE_CAPACITY = 64000;
    private static final Fluid WATER;
    private static final Fluid LAVA;
    private static final Map<Integer, List<BlockPos>> RANGE_OFFSETS;
    private final int[] amounts = new int[3];
    private final int[] tankTypes = new int[]{-1, -1, -1};
    private final ItemStackHandler upgrades = new ItemStackHandler(4) {
        public boolean isItemValid(int slot, ItemStack stack) {
            return ArcaneFluidReservoirBlockEntity.isUpgrade(stack);
        }

        protected void onContentsChanged(int slot) {
            ArcaneFluidReservoirBlockEntity.this.sync();
        }
    };
    private final ItemStackHandler tankModules = new ItemStackHandler(2) {
        public int getSlotLimit(int slot) {
            return 1;
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            if (!stack.is((Item)ModItems.ARCANE_FLUID_TANK.get())) return false;
            CompoundTag blockEntityData = stack.getOrDefault(
                    DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
            return blockEntityData.getInt("StoredAmount") <= 0;
        }

        protected void onContentsChanged(int slot) {
            ArcaneFluidReservoirBlockEntity.this.outputFluid = Math.min(ArcaneFluidReservoirBlockEntity.this.outputFluid, ArcaneFluidReservoirBlockEntity.this.unlockedTankCount() - 1);
            ArcaneFluidReservoirBlockEntity.this.sync();
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int lastUnlockedTank = ArcaneFluidReservoirBlockEntity.this.unlockedTankCount() - 1;
            return slot >= 0 && slot < 2 && lastUnlockedTank > 0 && ArcaneFluidReservoirBlockEntity.this.amounts[lastUnlockedTank] > 0 ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
    };
    private final IFluidHandler fluidHandler = new ReservoirFluidHandler();
    private Mode mode;
    private int inputFluid;
    private int outputFluid;
    private GlobalPos boundTarget;
    private Direction boundFace;
    private GlobalPos outputTarget;
    private Direction outputTargetFace;
    private int tickCounter;
    private int scanCursor;
    private State state;

    public ArcaneFluidReservoirBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.ARCANE_FLUID_RESERVOIR.get(), pos, state);
        this.mode = ArcaneFluidReservoirBlockEntity.Mode.RANGE;
        this.inputFluid = BuiltInRegistries.FLUID.getId(WATER);
        this.state = ArcaneFluidReservoirBlockEntity.State.IDLE;
    }

    public void serverTick() {
        Level var2 = this.level;
        if (var2 instanceof ServerLevel serverLevel) {
            ++this.tickCounter;
            int interval = Math.max(5, 20 - 5 * this.countUpgrade((Item)ModItems.FLUID_SPEED_UPGRADE.get()));
            if (this.tickCounter % interval == 0 && !this.level.hasNeighborSignal(this.worldPosition)) {
                this.pushSelectedFluid();
                boolean worked = this.mode == ArcaneFluidReservoirBlockEntity.Mode.RANGE ? this.collectWorldFluid() : this.pullBoundFluid(serverLevel);
                if (!worked && this.state == ArcaneFluidReservoirBlockEntity.State.RUNNING) {
                    this.setState(ArcaneFluidReservoirBlockEntity.State.IDLE);
                }

                if (this.tickCounter % 100 == 0) {
                    this.sync();
                }

            }
        }
    }

    private boolean collectWorldFluid() {
        Fluid wanted = fluidByRegistryId(this.inputFluid);
        if (wanted == Fluids.EMPTY) {
            this.setState(ArcaneFluidReservoirBlockEntity.State.NO_WORLD_FLUID);
            return false;
        } else {
            int radius = Math.min(32, 8 + 8 * this.countUpgrade((Item)ModItems.FLUID_RANGE_UPGRADE.get()));
            if (this.insertFluid(wanted, 1000, true) < 1000) {
                this.setState(ArcaneFluidReservoirBlockEntity.State.OUTPUT_BLOCKED);
                return false;
            } else {
                // Match Mekanism's pump behavior for renewable water: a source directly
                // below the machine is treated as an infinite intake and is never removed.
                // This avoids a remove/refill cycle and the resulting neighbor updates.
                if (this.collectInfiniteWaterBelow(wanted)) {
                    return true;
                }

                List<BlockPos> offsets = rangeOffsets(radius);

                for(int index = 0; index < Math.min(32, offsets.size()); ++index) {
                    if (this.tryCollectWorldSource((BlockPos)offsets.get(index), wanted)) {
                        return true;
                    }
                }

                for(int attempt = 0; attempt < Math.min(128, offsets.size()); ++attempt) {
                    BlockPos offset = (BlockPos)offsets.get(Math.floorMod(this.scanCursor++, offsets.size()));
                    if (this.tryCollectWorldSource(offset, wanted)) {
                        return true;
                    }
                }

                this.setState(ArcaneFluidReservoirBlockEntity.State.NO_WORLD_FLUID);
                return false;
            }
        }
    }

    private boolean collectInfiniteWaterBelow(Fluid wanted) {
        if (wanted != WATER || !this.level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION)) {
            return false;
        }

        BlockPos intake = this.worldPosition.below();
        if (!this.level.hasChunkAt(intake)) return false;
        var fluidState = this.level.getFluidState(intake);
        if (!fluidState.isSource() || fluidState.getType() != WATER) return false;

        this.insertFluid(WATER, 1000, false);
        this.setState(ArcaneFluidReservoirBlockEntity.State.RENEWABLE_SOURCE);
        return true;
    }

    private boolean tryCollectWorldSource(BlockPos offset, Fluid wanted) {
        BlockPos target = this.worldPosition.offset(offset);
        if (!this.level.hasChunkAt(target)) {
            return false;
        } else {
            BlockState targetState = this.level.getBlockState(target);
            if (targetState.getBlock() instanceof LiquidBlock && targetState.getFluidState().isSource() && targetState.getFluidState().getType() == wanted) {
                this.level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                this.insertFluid(wanted, 1000, false);
                boolean renewable = wanted == WATER ? this.level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION) : this.level.getGameRules().getBoolean(GameRules.RULE_LAVA_SOURCE_CONVERSION);
                this.setState(renewable ? ArcaneFluidReservoirBlockEntity.State.RENEWABLE_SOURCE : ArcaneFluidReservoirBlockEntity.State.RUNNING);
                this.sync();
                return true;
            } else {
                return false;
            }
        }
    }

    private static List<BlockPos> rangeOffsets(int radius) {
        return (List)RANGE_OFFSETS.computeIfAbsent(radius, (value) -> {
            List<BlockPos> offsets = new ArrayList();
            int radiusSquared = value * value;

            for(int y = -value; y <= value; ++y) {
                for(int z = -value; z <= value; ++z) {
                    for(int x = -value; x <= value; ++x) {
                        if (x * x + y * y + z * z <= radiusSquared) {
                            offsets.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }

            offsets.sort(Comparator.comparingInt((BlockPos pos) -> pos.distManhattan(BlockPos.ZERO))
                    .thenComparingInt(Vec3i::getY)
                    .thenComparingInt(Vec3i::getZ)
                    .thenComparingInt(Vec3i::getX));
            return List.copyOf(offsets);
        });
    }

    private boolean pullBoundFluid(ServerLevel serverLevel) {
        if (this.boundTarget == null) {
            this.setState(ArcaneFluidReservoirBlockEntity.State.NO_TARGET);
            return false;
        } else if (!this.wirelessReachable(this.boundTarget)) {
            this.setState(ArcaneFluidReservoirBlockEntity.State.WIRELESS_OUT_OF_RANGE);
            return false;
        } else {
            ServerLevel targetLevel = serverLevel.getServer().getLevel(this.boundTarget.dimension());
            if (targetLevel != null && targetLevel.hasChunkAt(this.boundTarget.pos())) {
                IFluidHandler target = (IFluidHandler)targetLevel.getCapability(FluidHandler.BLOCK, this.boundTarget.pos(), this.boundFace);
                if (target == null) {
                    this.setState(ArcaneFluidReservoirBlockEntity.State.INVALID_TARGET);
                    return false;
                } else {
                    int limit = 1000 * (1 + this.countUpgrade((Item)ModItems.FLUID_SPEED_UPGRADE.get()));
                    FluidStack simulated = target.drain(limit, FluidAction.SIMULATE);
                    if (!simulated.isEmpty() && simulated.getFluid() != Fluids.EMPTY) {
                        int accepted = this.insertFluid(simulated.getFluid(), simulated.getAmount(), true);
                        if (accepted <= 0) {
                            this.setState(ArcaneFluidReservoirBlockEntity.State.OUTPUT_BLOCKED);
                            return false;
                        } else {
                            FluidStack request = simulated.copyWithAmount(accepted);
                            FluidStack drained = target.drain(request, FluidAction.EXECUTE);
                            this.insertFluid(drained.getFluid(), drained.getAmount(), false);
                            this.setState(ArcaneFluidReservoirBlockEntity.State.RUNNING);
                            this.sync();
                            return true;
                        }
                    } else {
                        this.setState(ArcaneFluidReservoirBlockEntity.State.INPUT_EMPTY);
                        return false;
                    }
                }
            } else {
                this.setState(ArcaneFluidReservoirBlockEntity.State.TARGET_UNLOADED);
                return false;
            }
        }
    }

    private void pushSelectedFluid() {
        if (this.outputFluid >= 0 && this.outputFluid < this.unlockedTankCount() && this.amounts[this.outputFluid] > 0 && this.tankTypes[this.outputFluid] >= 0 && this.level != null) {
            this.pushToBoundOutput();
        }
    }

    private boolean pushToBoundOutput() {
        if (this.outputTarget != null && this.level != null && this.level.getServer() != null) {
            if (!this.wirelessReachable(this.outputTarget)) {
                this.setState(ArcaneFluidReservoirBlockEntity.State.WIRELESS_OUT_OF_RANGE);
                return false;
            } else {
                ServerLevel targetLevel = this.level.getServer().getLevel(this.outputTarget.dimension());
                if (targetLevel != null && targetLevel.hasChunkAt(this.outputTarget.pos())) {
                    IFluidHandler target = (IFluidHandler)targetLevel.getCapability(FluidHandler.BLOCK, this.outputTarget.pos(), this.outputTargetFace);
                    if (target == null) {
                        return false;
                    } else {
                        int offered = Math.min(1000 * (1 + this.countUpgrade((Item)ModItems.FLUID_SPEED_UPGRADE.get())), this.amounts[this.outputFluid]);
                        if (offered <= 0) {
                            return false;
                        } else {
                            Fluid selectedFluid = fluidByRegistryId(this.tankTypes[this.outputFluid]);
                            FluidStack stack = new FluidStack(selectedFluid, offered);
                            int accepted = target.fill(stack, FluidAction.SIMULATE);
                            if (accepted <= 0) {
                                return false;
                            } else {
                                int inserted = target.fill(new FluidStack(selectedFluid, accepted), FluidAction.EXECUTE);
                                int[] var10000 = this.amounts;
                                int var10001 = this.outputFluid;
                                var10000[var10001] -= Math.max(0, inserted);
                                this.clearEmptyTank(this.outputFluid);
                                if (inserted > 0) {
                                    this.sync();
                                }

                                return inserted > 0;
                            }
                        }
                    }
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return this.fluidHandler;
    }

    public ItemStackHandler getUpgrades() {
        return this.upgrades;
    }

    public ItemStackHandler getTankModules() {
        return this.tankModules;
    }

    public int capacity() {
        return '切' * (1 + this.countUpgrade((Item)ModItems.FLUID_CAPACITY_UPGRADE.get()));
    }

    public int amount(int tank) {
        return this.amounts[Math.max(0, Math.min(2, tank))];
    }

    public int tankType(int tank) {
        return this.tankTypes[Math.max(0, Math.min(2, tank))];
    }

    public int unlockedTankCount() {
        int modules = 0;

        for(int slot = 0; slot < this.tankModules.getSlots(); ++slot) {
            if (!this.tankModules.getStackInSlot(slot).isEmpty()) {
                ++modules;
            }
        }

        return Math.min(3, 1 + modules);
    }

    public int inputFluid() {
        return this.inputFluid;
    }

    public int outputFluid() {
        return this.outputFluid;
    }

    public int outputTankType() {
        return this.outputFluid >= 0 && this.outputFluid < this.unlockedTankCount() && this.amounts[this.outputFluid] > 0 ? this.tankTypes[this.outputFluid] : -1;
    }

    public Fluid outputTankFluid() {
        int type = this.outputTankType();
        return type < 0 ? Fluids.EMPTY : fluidByRegistryId(type);
    }

    public int outputTankAmount() {
        return this.outputFluid >= 0 && this.outputFluid < this.unlockedTankCount() ? this.amounts[this.outputFluid] : 0;
    }

    public boolean canWirelessReach(GlobalPos target) {
        return this.wirelessReachable(target);
    }

    public int wirelessTier() {
        return Math.min(3, this.countUpgrade((Item)ModItems.FLUID_RANGE_UPGRADE.get()));
    }

    public Mode mode() {
        return this.mode;
    }

    public State operatingState() {
        return this.state;
    }

    public void cycleMode(Player player) {
        this.mode = this.mode == ArcaneFluidReservoirBlockEntity.Mode.RANGE ? ArcaneFluidReservoirBlockEntity.Mode.BOUND : ArcaneFluidReservoirBlockEntity.Mode.RANGE;
        if (this.mode == ArcaneFluidReservoirBlockEntity.Mode.RANGE) {
            this.scanCursor = 0;
        }

        this.sync();
        player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.mode." + this.mode.name().toLowerCase()), true);
    }

    public void cycleInput() {
        List<Integer> choices = new ArrayList();
        addFluidChoice(choices, WATER);
        addFluidChoice(choices, LAVA);

        for(int tank = 0; tank < this.unlockedTankCount(); ++tank) {
            if (this.tankTypes[tank] >= 0 && !choices.contains(this.tankTypes[tank])) {
                choices.add(this.tankTypes[tank]);
            }
        }

        int current = choices.indexOf(this.inputFluid);
        this.inputFluid = (Integer)choices.get((current + 1 + choices.size()) % choices.size());
        this.scanCursor = 0;
        this.sync();
    }

    public void cycleOutput() {
        this.outputFluid = (this.outputFluid + 1) % this.unlockedTankCount();
        this.sync();
    }

    private int insertFluid(Fluid fluid, int amount, boolean simulate) {
        int type = BuiltInRegistries.FLUID.getId(fluid);
        if (fluid != Fluids.EMPTY && type >= 0 && amount > 0) {
            int tank = -1;

            for(int index = 0; index < this.unlockedTankCount(); ++index) {
                if (this.tankTypes[index] == type) {
                    tank = index;
                    break;
                }
            }

            if (tank < 0) {
                for(int index = 0; index < this.unlockedTankCount(); ++index) {
                    if (this.tankTypes[index] < 0 || this.amounts[index] <= 0) {
                        tank = index;
                        break;
                    }
                }
            }

            if (tank < 0) {
                return 0;
            } else {
                int accepted = Math.min(amount, this.capacity() - this.amounts[tank]);
                if (!simulate && accepted > 0) {
                    this.tankTypes[tank] = type;
                    int[] var10000 = this.amounts;
                    var10000[tank] += accepted;
                    this.sync();
                }

                return Math.max(0, accepted);
            }
        } else {
            return 0;
        }
    }

    private boolean wirelessReachable(GlobalPos target) {
        if (this.level != null && target != null) {
            int tier = this.wirelessTier();
            if (!target.dimension().equals(this.level.dimension())) {
                return tier >= 3;
            } else if (tier >= 2) {
                return true;
            } else {
                int maximum = tier == 0 ? 32 : 128;
                return this.worldPosition.distSqr(target.pos()) <= (double)maximum * (double)maximum;
            }
        } else {
            return false;
        }
    }

    public IWandable.Result onFirstConnection(GlobalPos target, @Nullable Direction face, @Nullable LivingEntity entity, Player player) {
        return isAdvancedLectern(target, player) ? Result.NONE : this.bindOutput(target, face, player);
    }

    public IWandable.Result onLastConnection(GlobalPos target, @Nullable Direction face, @Nullable LivingEntity entity, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
            if (targetLevel != null && (targetLevel.getBlockEntity(target.pos()) instanceof ArcaneFluidReservoirBlockEntity || targetLevel.getBlockEntity(target.pos()) instanceof AdvancedStorageLecternBlockEntity)) {
                return Result.NONE;
            }
        }

        return this.bindInput(target, face, player);
    }

    private static boolean isAdvancedLectern(GlobalPos target, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        } else {
            ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
            return targetLevel != null && targetLevel.getBlockEntity(target.pos()) instanceof AdvancedStorageLecternBlockEntity;
        }
    }

    private IWandable.Result bindInput(GlobalPos target, @Nullable Direction face, Player player) {
        if (this.level != null && target != null && (!target.dimension().equals(this.level.dimension()) || !target.pos().equals(this.worldPosition))) {
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                if (!this.wirelessReachable(target)) {
                    player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.out_of_range"), true);
                    return Result.FAIL;
                } else {
                    ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
                    if (targetLevel != null && targetLevel.hasChunkAt(target.pos()) && targetLevel.getCapability(FluidHandler.BLOCK, target.pos(), face) != null) {
                        this.boundTarget = target;
                        this.boundFace = face;
                        this.mode = ArcaneFluidReservoirBlockEntity.Mode.BOUND;
                        this.sync();
                        player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.input_bound", new Object[]{target.dimension().location().toString(), target.pos().toShortString()}), true);
                        return Result.SUCCESS;
                    } else {
                        player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.invalid_target"), true);
                        return Result.FAIL;
                    }
                }
            } else {
                return Result.FAIL;
            }
        } else {
            return Result.FAIL;
        }
    }

    private IWandable.Result bindOutput(GlobalPos target, @Nullable Direction face, Player player) {
        if (this.level != null && target != null && (!target.dimension().equals(this.level.dimension()) || !target.pos().equals(this.worldPosition))) {
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                if (!this.wirelessReachable(target)) {
                    player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.out_of_range"), true);
                    return Result.FAIL;
                } else {
                    ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
                    if (targetLevel != null && targetLevel.hasChunkAt(target.pos()) && targetLevel.getCapability(FluidHandler.BLOCK, target.pos(), face) != null) {
                        this.outputTarget = target;
                        this.outputTargetFace = face;
                        this.sync();
                        player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.output_bound", new Object[]{target.dimension().location().toString(), target.pos().toShortString()}), true);
                        return Result.SUCCESS;
                    } else {
                        player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.invalid_target"), true);
                        return Result.FAIL;
                    }
                }
            } else {
                return Result.FAIL;
            }
        } else {
            return Result.FAIL;
        }
    }

    public IWandable.Result onClearConnections(Player player) {
        this.boundTarget = null;
        this.boundFace = null;
        this.outputTarget = null;
        this.outputTargetFace = null;
        this.sync();
        player.displayClientMessage(Component.translatable("message.ars_arcane_matrix.arcane_fluid_reservoir.cleared"), true);
        return Result.SUCCESS;
    }

    public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.arcane_fluid_reservoir");
    }

    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ArcaneFluidReservoirMenu(id, inventory, this);
    }

    public void getTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_reservoir.mode", new Object[]{Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.mode." + this.mode.name().toLowerCase())}));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_reservoir.tanks", new Object[]{this.unlockedTankCount(), this.capacity()}));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_reservoir.wireless", new Object[]{Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.wireless_tier." + this.wirelessTier())}));
        tooltip.add(Component.translatable("state.ars_arcane_matrix.arcane_fluid_reservoir." + this.state.name().toLowerCase()));
    }

    private int countUpgrade(Item item) {
        int count = 0;

        for(int i = 0; i < this.upgrades.getSlots(); ++i) {
            if (this.upgrades.getStackInSlot(i).is(item)) {
                count += this.upgrades.getStackInSlot(i).getCount();
            }
        }

        return count;
    }

    private boolean hasUpgrade(Item item) {
        return this.countUpgrade(item) > 0;
    }

    private static boolean isUpgrade(ItemStack stack) {
        return stack.is((Item)ModItems.FLUID_CAPACITY_UPGRADE.get()) || stack.is((Item)ModItems.FLUID_RANGE_UPGRADE.get()) || stack.is((Item)ModItems.FLUID_SPEED_UPGRADE.get());
    }

    private static Fluid fluidByRegistryId(int id) {
        Fluid fluid = (Fluid)BuiltInRegistries.FLUID.byId(id);
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    private static void addFluidChoice(List<Integer> choices, Fluid fluid) {
        int id = BuiltInRegistries.FLUID.getId(fluid);
        if (id >= 0 && !choices.contains(id)) {
            choices.add(id);
        }

    }

    private void setState(State next) {
        if (this.state != next) {
            this.state = next;
            this.sync();
        }

    }

    private void sync() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }

    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("Amounts", this.amounts);
        tag.putIntArray("TankTypes", this.tankTypes);
        tag.putInt("TankFormat", 3);

        for(int tank = 0; tank < 3; ++tank) {
            if (this.tankTypes[tank] >= 0) {
                tag.putString("TankFluid" + tank, BuiltInRegistries.FLUID.getKey(fluidByRegistryId(this.tankTypes[tank])).toString());
            }
        }

        tag.putInt("Mode", this.mode.ordinal());
        tag.putInt("InputFluid", this.inputFluid);
        tag.putString("InputFluidId", BuiltInRegistries.FLUID.getKey(fluidByRegistryId(this.inputFluid)).toString());
        tag.putInt("OutputFluid", this.outputFluid);
        tag.putInt("State", this.state.ordinal());
        tag.put("Upgrades", this.upgrades.serializeNBT(registries));
        tag.put("TankModules", this.tankModules.serializeNBT(registries));
        if (this.boundTarget != null) {
            tag.putString("TargetDimension", this.boundTarget.dimension().location().toString());
            tag.putLong("TargetPos", this.boundTarget.pos().asLong());
            if (this.boundFace != null) {
                tag.putInt("TargetFace", this.boundFace.get3DDataValue());
            }
        }

        if (this.outputTarget != null) {
            tag.putString("OutputTargetDimension", this.outputTarget.dimension().location().toString());
            tag.putLong("OutputTargetPos", this.outputTarget.pos().asLong());
            if (this.outputTargetFace != null) {
                tag.putInt("OutputTargetFace", this.outputTargetFace.get3DDataValue());
            }
        }

    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] savedAmounts = tag.getIntArray("Amounts");
        if (tag.contains("TankTypes") && tag.getInt("TankFormat") >= 2) {
            System.arraycopy(savedAmounts, 0, this.amounts, 0, Math.min(3, savedAmounts.length));
            int[] savedTypes = tag.getIntArray("TankTypes");
            System.arraycopy(savedTypes, 0, this.tankTypes, 0, Math.min(3, savedTypes.length));
            if (tag.getInt("TankFormat") >= 3) {
                for(int tank = 0; tank < 3; ++tank) {
                    ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString("TankFluid" + tank));
                    if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId)) {
                        this.tankTypes[tank] = BuiltInRegistries.FLUID.getId((Fluid)BuiltInRegistries.FLUID.get(fluidId));
                    } else if (this.amounts[tank] > 0) {
                        this.amounts[tank] = 0;
                        this.tankTypes[tank] = -1;
                    }
                }
            }
        } else {
            int migrated = 0;

            for(int oldType = 0; oldType < Math.min(3, savedAmounts.length); ++oldType) {
                if (savedAmounts[oldType] > 0 && migrated < 3) {
                    Fluid legacyFluid = oldType == 0 ? WATER : (oldType == 1 ? LAVA : findMilkFluid());
                    if (legacyFluid != Fluids.EMPTY) {
                        this.tankTypes[migrated] = BuiltInRegistries.FLUID.getId(legacyFluid);
                        this.amounts[migrated] = savedAmounts[oldType];
                        ++migrated;
                    }
                }
            }

            for(int module = 0; module < Math.max(0, migrated - 1); ++module) {
                this.tankModules.setStackInSlot(module, new ItemStack((ItemLike)ModItems.ARCANE_FLUID_TANK.get()));
            }
        }

        this.mode = ArcaneFluidReservoirBlockEntity.Mode.values()[Math.floorMod(tag.getInt("Mode"), ArcaneFluidReservoirBlockEntity.Mode.values().length)];
        if (tag.getInt("TankFormat") >= 3) {
            ResourceLocation inputId = ResourceLocation.tryParse(tag.getString("InputFluidId"));
            this.inputFluid = inputId != null && BuiltInRegistries.FLUID.containsKey(inputId) ? BuiltInRegistries.FLUID.getId((Fluid)BuiltInRegistries.FLUID.get(inputId)) : BuiltInRegistries.FLUID.getId(WATER);
        } else if (tag.getInt("TankFormat") >= 2) {
            this.inputFluid = tag.getInt("InputFluid");
        } else {
            this.inputFluid = BuiltInRegistries.FLUID.getId(tag.getInt("InputFluid") == 1 ? LAVA : WATER);
        }

        this.outputFluid = Math.floorMod(tag.getInt("OutputFluid"), 3);
        this.state = ArcaneFluidReservoirBlockEntity.State.values()[Math.floorMod(tag.getInt("State"), ArcaneFluidReservoirBlockEntity.State.values().length)];
        if (tag.contains("Upgrades")) {
            this.upgrades.deserializeNBT(registries, tag.getCompound("Upgrades"));
        }

        if (tag.contains("TankModules")) {
            this.tankModules.deserializeNBT(registries, tag.getCompound("TankModules"));
        }

        this.outputFluid = Math.min(this.outputFluid, this.unlockedTankCount() - 1);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("TargetDimension"));
        if (dimension != null && tag.contains("TargetPos")) {
            this.boundTarget = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(tag.getLong("TargetPos")));
        }

        this.boundFace = tag.contains("TargetFace") ? Direction.from3DDataValue(tag.getInt("TargetFace")) : null;
        ResourceLocation outputDimension = ResourceLocation.tryParse(tag.getString("OutputTargetDimension"));
        if (outputDimension != null && tag.contains("OutputTargetPos")) {
            this.outputTarget = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, outputDimension), BlockPos.of(tag.getLong("OutputTargetPos")));
        }

        this.outputTargetFace = tag.contains("OutputTargetFace") ? Direction.from3DDataValue(tag.getInt("OutputTargetFace")) : null;
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static Fluid findMilkFluid() {
        for(Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null && id.getPath().equals("milk") && fluid != Fluids.EMPTY) {
                return fluid;
            }
        }

        return Fluids.EMPTY;
    }

    private void clearEmptyTank(int tank) {
        if (tank >= 0 && tank < 3 && this.amounts[tank] <= 0) {
            this.amounts[tank] = 0;
            this.tankTypes[tank] = -1;
        }

    }

    static {
        WATER = Fluids.WATER;
        LAVA = Fluids.LAVA;
        RANGE_OFFSETS = new ConcurrentHashMap();
    }

    public static enum Mode {
        RANGE,
        BOUND;

        private Mode() {
        }
    }

    public static enum State {
        IDLE,
        RUNNING,
        RENEWABLE_SOURCE,
        NO_WORLD_FLUID,
        NO_LAVA_CONVERSION,
        NO_TARGET,
        TARGET_UNLOADED,
        INVALID_TARGET,
        INPUT_EMPTY,
        OUTPUT_BLOCKED,
        WIRELESS_OUT_OF_RANGE;

        private State() {
        }
    }

    private final class ReservoirFluidHandler implements IFluidHandler {
        private ReservoirFluidHandler() {
        }

        public int getTanks() {
            return ArcaneFluidReservoirBlockEntity.this.unlockedTankCount();
        }

        public FluidStack getFluidInTank(int tank) {
            return tank >= 0 && tank < ArcaneFluidReservoirBlockEntity.this.unlockedTankCount() && ArcaneFluidReservoirBlockEntity.this.amounts[tank] > 0 && ArcaneFluidReservoirBlockEntity.this.tankTypes[tank] >= 0 ? new FluidStack(ArcaneFluidReservoirBlockEntity.fluidByRegistryId(ArcaneFluidReservoirBlockEntity.this.tankTypes[tank]), ArcaneFluidReservoirBlockEntity.this.amounts[tank]) : FluidStack.EMPTY;
        }

        public int getTankCapacity(int tank) {
            return ArcaneFluidReservoirBlockEntity.this.capacity();
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            int type = BuiltInRegistries.FLUID.getId(stack.getFluid());
            return tank >= 0 && tank < ArcaneFluidReservoirBlockEntity.this.unlockedTankCount() && stack.getFluid() != Fluids.EMPTY && type >= 0 && (ArcaneFluidReservoirBlockEntity.this.tankTypes[tank] < 0 || ArcaneFluidReservoirBlockEntity.this.tankTypes[tank] == type);
        }

        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            return ArcaneFluidReservoirBlockEntity.this.insertFluid(resource.getFluid(), resource.getAmount(), !action.execute());
        }

        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            int type = BuiltInRegistries.FLUID.getId(resource.getFluid());
            if (resource.getFluid() != Fluids.EMPTY && type >= 0) {
                for(int tank = 0; tank < ArcaneFluidReservoirBlockEntity.this.unlockedTankCount(); ++tank) {
                    if (ArcaneFluidReservoirBlockEntity.this.tankTypes[tank] == type && ArcaneFluidReservoirBlockEntity.this.amounts[tank] > 0) {
                        int drained = Math.min(resource.getAmount(), ArcaneFluidReservoirBlockEntity.this.amounts[tank]);
                        FluidStack result = new FluidStack(resource.getFluid(), drained);
                        if (action.execute()) {
                            int[] var10000 = ArcaneFluidReservoirBlockEntity.this.amounts;
                            var10000[tank] -= drained;
                            ArcaneFluidReservoirBlockEntity.this.clearEmptyTank(tank);
                            ArcaneFluidReservoirBlockEntity.this.sync();
                        }

                        return result;
                    }
                }

                return FluidStack.EMPTY;
            } else {
                return FluidStack.EMPTY;
            }
        }

        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (ArcaneFluidReservoirBlockEntity.this.outputFluid >= 0 && ArcaneFluidReservoirBlockEntity.this.outputFluid < ArcaneFluidReservoirBlockEntity.this.unlockedTankCount() && ArcaneFluidReservoirBlockEntity.this.amounts[ArcaneFluidReservoirBlockEntity.this.outputFluid] > 0 && ArcaneFluidReservoirBlockEntity.this.tankTypes[ArcaneFluidReservoirBlockEntity.this.outputFluid] >= 0) {
                int drained = Math.min(maxDrain, ArcaneFluidReservoirBlockEntity.this.amounts[ArcaneFluidReservoirBlockEntity.this.outputFluid]);
                FluidStack result = new FluidStack(ArcaneFluidReservoirBlockEntity.fluidByRegistryId(ArcaneFluidReservoirBlockEntity.this.tankTypes[ArcaneFluidReservoirBlockEntity.this.outputFluid]), drained);
                if (action.execute()) {
                    int[] var10000 = ArcaneFluidReservoirBlockEntity.this.amounts;
                    int var10001 = ArcaneFluidReservoirBlockEntity.this.outputFluid;
                    var10000[var10001] -= drained;
                    ArcaneFluidReservoirBlockEntity.this.clearEmptyTank(ArcaneFluidReservoirBlockEntity.this.outputFluid);
                    ArcaneFluidReservoirBlockEntity.this.sync();
                }

                return result;
            } else {
                return FluidStack.EMPTY;
            }
        }
    }
}
