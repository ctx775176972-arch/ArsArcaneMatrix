package dev.arsmatrix.recipe;

import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.mojang.serialization.MapCodec;
import dev.arsmatrix.registry.ModRecipeTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/** Matches a Containment Jar whose captured entity is specifically a vanilla cow. */
public final class CowMobJarIngredient implements ICustomIngredient {
    public static final CowMobJarIngredient INSTANCE = new CowMobJarIngredient();
    public static final MapCodec<CowMobJarIngredient> CODEC = MapCodec.unit(INSTANCE);

    private CowMobJarIngredient() {}

    @Override
    public boolean test(ItemStack stack) {
        if (!stack.is(BlockRegistry.MOB_JAR.asItem())) return false;
        MobJarData data = stack.get(DataComponentRegistry.MOB_JAR.get());
        if (data == null || data.entityTag().isEmpty()) return false;
        return "minecraft:cow".equals(data.entityTag().get().getString("id"));
    }

    @Override
    public Stream<ItemStack> getItems() {
        ItemStack jar = new ItemStack(BlockRegistry.MOB_JAR.get());
        CompoundTag cow = new CompoundTag();
        cow.putString("id", "minecraft:cow");
        jar.set(DataComponentRegistry.MOB_JAR.get(), new MobJarData(cow, new CompoundTag()));
        return Stream.of(jar);
    }

    @Override public boolean isSimple() { return false; }

    @Override
    public IngredientType<?> getType() {
        return ModRecipeTypes.COW_MOB_JAR_INGREDIENT.get();
    }

    @Override public boolean equals(Object other) { return other instanceof CowMobJarIngredient; }
    @Override public int hashCode() { return CowMobJarIngredient.class.hashCode(); }
}
