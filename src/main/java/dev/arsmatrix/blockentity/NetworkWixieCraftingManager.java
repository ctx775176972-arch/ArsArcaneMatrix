package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.recipe.CraftingManager;
import com.hollingsworth.arsnouveau.common.block.tile.WixieCauldronTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Routes a terminal-dispatched recipe through Ars Nouveau's real Wixie worker. */
final class NetworkWixieCraftingManager extends CraftingManager {

    private final BlockPos providerPos;
    private boolean readyToComplete;

    NetworkWixieCraftingManager(
            BlockPos providerPos,
            ItemStack output,
            List<ItemStack> ingredients,
            List<ItemStack> remainders
    ) {
        super(output.copy(), copyStacks(ingredients));
        this.providerPos = providerPos.immutable();
        remainingItems = copyStacks(remainders);
    }

    @Override
    public boolean canBeCompleted() {
        return readyToComplete && super.canBeCompleted();
    }

    void markReadyToComplete() {
        readyToComplete = true;
    }

    @Override
    public void completeCraft(WixieCauldronTile wixie) {
        Level level = wixie.getLevel();
        if (level != null
                && level.getBlockEntity(providerPos) instanceof WixiePatternProviderBlockEntity provider) {
            provider.completeWixieJob(wixie.getBlockPos());
            wixie.hasSource = false;
            wixie.onCraftingComplete();
            craftCompleted = true;
            return;
        }
        super.completeCraft(wixie);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        stacks.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).forEach(result::add);
        return result;
    }
}
