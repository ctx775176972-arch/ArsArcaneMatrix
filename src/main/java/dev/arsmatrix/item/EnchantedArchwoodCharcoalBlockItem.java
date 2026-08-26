package dev.arsmatrix.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** Compressed enchanted fuel with ten times the burn duration of one charcoal. */
public final class EnchantedArchwoodCharcoalBlockItem extends BlockItem {
    public static final int BURN_TICKS = EnchantedArchwoodCharcoalItem.BURN_TICKS * 10;

    public EnchantedArchwoodCharcoalBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return BURN_TICKS;
    }
}
