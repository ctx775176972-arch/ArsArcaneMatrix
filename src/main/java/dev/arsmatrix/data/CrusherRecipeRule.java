package dev.arsmatrix.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One concrete tag-derived crusher display/processing rule. */
public record CrusherRecipeRule(
        ResourceLocation id,
        ItemStack input,
        ItemStack dust,
        int baseDustCount,
        int airDustCount
) {
    public ItemStack baseOutput() { return dust.copyWithCount(baseDustCount); }
    public ItemStack airOutput() { return dust.copyWithCount(airDustCount); }
    public ItemStack airBonusOutput() { return dust.copyWithCount(Math.max(0, airDustCount - baseDustCount)); }
}
