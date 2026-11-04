package dev.arsmatrix.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

/** Optional visual bridge; no linkage to Iron's Spellbooks classes. Never creates usable output. */
public final class PotionFluidDisplay {
    private PotionFluidDisplay() {}
    public static ItemStack icon(FluidStack fluid) {
        var contents = fluid.get(DataComponents.POTION_CONTENTS);
        if (fluid.isEmpty() || contents == null) return ItemStack.EMPTY;
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath(
                "irons_spellbooks", "potion_bottle_type"));
        Object bottle = type == null ? null : fluid.get(type);
        String name = bottle instanceof Enum<?> value ? value.name() : "REGULAR";
        ItemStack icon = new ItemStack(switch (name) {
            case "SPLASH" -> Items.SPLASH_POTION;
            case "LINGERING" -> Items.LINGERING_POTION;
            default -> Items.POTION;
        });
        icon.set(DataComponents.POTION_CONTENTS, contents);
        return icon;
    }
}
