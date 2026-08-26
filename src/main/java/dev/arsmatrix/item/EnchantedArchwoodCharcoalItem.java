package dev.arsmatrix.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/** Long-burning magical fuel that enables the Arcane Smelter's crystal mode. */
public final class EnchantedArchwoodCharcoalItem extends Item {
    public static final int BURN_TICKS = 25_600; // 128 vanilla smelts

    public EnchantedArchwoodCharcoalItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return BURN_TICKS;
    }
}
