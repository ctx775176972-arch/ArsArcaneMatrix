package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.ArcaneReactionVesselBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModMenus;
import dev.arsmatrix.data.ArcaneReactionIngredient;
import dev.arsmatrix.data.ArcaneReactionManager;
import dev.arsmatrix.data.ArcaneReactionRule;
import dev.arsmatrix.util.RemoteMenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ArcaneReactionVesselMenu extends AbstractContainerMenu {
    public static final int BUTTON_CLEAR_FLUID = 0;
    private static final int BUTTON_RECIPE_FLAG = 1 << 29;
    private static final int BUTTON_RECIPE_HASH_MASK = BUTTON_RECIPE_FLAG - 1;
    private final BlockPos pos;
    private final ContainerData data;
    public ArcaneReactionVesselMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) { this(id, inventory, buffer.readBlockPos(), null); }
    public ArcaneReactionVesselMenu(int id, Inventory inventory, ArcaneReactionVesselBlockEntity vessel) { this(id, inventory, vessel.getBlockPos(), vessel); }
    private ArcaneReactionVesselMenu(int id, Inventory player, BlockPos pos, ArcaneReactionVesselBlockEntity supplied) {
        super(ModMenus.ARCANE_REACTION_VESSEL.get(), id); this.pos = pos.immutable();
        ArcaneReactionVesselBlockEntity vessel = supplied != null ? supplied
                : player.player.level().getBlockEntity(pos) instanceof ArcaneReactionVesselBlockEntity found ? found : null;
        ItemStackHandler items = vessel == null ? new ItemStackHandler(3) : vessel.items();
        // The network-side menu must keep its own writable data array. Reusing the
        // client block entity's ContainerData drops every update except progress,
        // leaving the displayed recipe duration stuck at the five-second default.
        data = supplied == null ? new SimpleContainerData(5) : supplied.menuData; addDataSlots(data);
        addSlot(new SlotItemHandler(items, 0, 44, 35)); addSlot(new SlotItemHandler(items, 1, 62, 35));
        addSlot(new SlotItemHandler(items, 2, 116, 35) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) addSlot(new Slot(player, col+row*9+9, 8+col*18, 84+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(player, col, 8+col*18, 138));
    }
    public int data(int index) { return data.get(index); }
    public static int recipeTransferButton(ResourceLocation recipeId) {
        return BUTTON_RECIPE_FLAG | (recipeId.hashCode() & BUTTON_RECIPE_HASH_MASK);
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CLEAR_FLUID
                && player.level().getBlockEntity(pos) instanceof ArcaneReactionVesselBlockEntity vessel) {
            vessel.clearFluid();
            return true;
        }
        if ((id & BUTTON_RECIPE_FLAG) == 0) return false;
        if (player.level().isClientSide) return true;
        ArcaneReactionRule recipe = ArcaneReactionManager.allRecipes().stream()
                .filter(rule -> (rule.id().hashCode() & BUTTON_RECIPE_HASH_MASK)
                        == (id & BUTTON_RECIPE_HASH_MASK))
                .findFirst().orElse(null);
        if (recipe == null
                || !(player.level().getBlockEntity(pos) instanceof ArcaneReactionVesselBlockEntity vessel)) {
            return false;
        }
        return transferRecipeItems(player, vessel, recipe);
    }

    private boolean transferRecipeItems(
            Player player, ArcaneReactionVesselBlockEntity vessel, ArcaneReactionRule recipe
    ) {
        if (!vessel.canAcceptJeiTransfer()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.arcane_reaction_vessel.jei.busy"), true);
            return false;
        }
        List<ArcaneReactionIngredient> ingredients = recipe.ingredients();
        Map<Integer, Integer> reserved = new HashMap<>();
        List<TransferPlan> plans = new ArrayList<>();
        for (int destination = 0; destination < ingredients.size(); destination++) {
            ArcaneReactionIngredient ingredient = ingredients.get(destination);
            ItemStack existing = vessel.items().getStackInSlot(destination);
            if (!existing.isEmpty() && !ingredient.matches(existing)) {
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.arcane_reaction_vessel.jei.clear_inputs"), true);
                return false;
            }
            int needed = Math.max(0, ingredient.count() - existing.getCount());
            if (needed == 0) {
                plans.add(new TransferPlan(destination, existing.copyWithCount(1), List.of()));
                continue;
            }
            ItemStack choice = existing.isEmpty()
                    ? findAvailableChoice(ingredient, needed, reserved) : existing.copyWithCount(1);
            if (choice.isEmpty() || existing.getCount() + needed > choice.getMaxStackSize()) {
                showMissing(player, ingredient, needed);
                return false;
            }
            List<SlotTake> takes = reserveFromInventory(choice, needed, reserved);
            if (takes == null) {
                showMissing(player, ingredient, needed);
                return false;
            }
            plans.add(new TransferPlan(destination, choice, takes));
        }
        for (TransferPlan plan : plans) {
            int moved = 0;
            for (SlotTake take : plan.takes) {
                slots.get(take.menuSlot).remove(take.count);
                moved += take.count;
            }
            if (moved <= 0) continue;
            ItemStack existing = vessel.items().getStackInSlot(plan.destination);
            if (existing.isEmpty()) vessel.items().setStackInSlot(
                    plan.destination, plan.choice.copyWithCount(moved));
            else existing.grow(moved);
        }
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_reaction_vessel.jei.filled"), true);
        return true;
    }

    private ItemStack findAvailableChoice(
            ArcaneReactionIngredient ingredient, int needed, Map<Integer, Integer> reserved
    ) {
        for (int menuSlot = 3; menuSlot < 39; menuSlot++) {
            ItemStack candidate = slots.get(menuSlot).getItem();
            if (!ingredient.matches(candidate)) continue;
            int total = 0;
            for (int other = 3; other < 39; other++) {
                ItemStack available = slots.get(other).getItem();
                if (ItemStack.isSameItemSameComponents(candidate, available)) {
                    total += Math.max(0, available.getCount() - reserved.getOrDefault(other, 0));
                }
            }
            if (total >= needed) return candidate.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private List<SlotTake> reserveFromInventory(
            ItemStack choice, int needed, Map<Integer, Integer> reserved
    ) {
        List<SlotTake> result = new ArrayList<>();
        int remaining = needed;
        for (int menuSlot = 3; menuSlot < 39 && remaining > 0; menuSlot++) {
            ItemStack available = slots.get(menuSlot).getItem();
            if (!ItemStack.isSameItemSameComponents(choice, available)) continue;
            int free = Math.max(0, available.getCount() - reserved.getOrDefault(menuSlot, 0));
            int take = Math.min(free, remaining);
            if (take <= 0) continue;
            reserved.merge(menuSlot, take, Integer::sum);
            result.add(new SlotTake(menuSlot, take));
            remaining -= take;
        }
        return remaining == 0 ? List.copyOf(result) : null;
    }

    private static void showMissing(
            Player player, ArcaneReactionIngredient ingredient, int needed
    ) {
        ItemStack display = ingredient.displayStacks().stream().findFirst().orElse(ItemStack.EMPTY);
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_reaction_vessel.jei.missing",
                Math.max(1, needed), display.getHoverName()), true);
    }

    private record SlotTake(int menuSlot, int count) {}
    private record TransferPlan(int destination, ItemStack choice, List<SlotTake> takes) {}
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); ItemStack stack=slot.getItem(); ItemStack original=stack.copy();
        boolean moved = index < 3 ? moveItemStackTo(stack,3,slots.size(),true) : moveItemStackTo(stack,0,2,false);
        if (!moved) return ItemStack.EMPTY; if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); return original;
    }
    @Override public boolean stillValid(Player player) { return player.level().getBlockState(pos).is(ModBlocks.ARCANE_REACTION_VESSEL.get()) && RemoteMenuAccess.isWithinUseRange(player, pos); }
}
