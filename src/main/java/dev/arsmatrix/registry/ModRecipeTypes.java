package dev.arsmatrix.registry;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.recipe.ArcaneMachineUpgradeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe registrations that extend Ars Nouveau's processing blocks. */
public final class ModRecipeTypes {
    private ModRecipeTypes() {}

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ArsArcaneMatrix.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ArsArcaneMatrix.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, ArcaneMachineUpgradeRecipe.Serializer>
            ARCANE_MACHINE_UPGRADE_SERIALIZER = SERIALIZERS.register(
                    "arcane_machine_upgrade", ArcaneMachineUpgradeRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ArcaneMachineUpgradeRecipe>>
            ARCANE_MACHINE_UPGRADE_TYPE = TYPES.register(
                    "arcane_machine_upgrade", () -> new RecipeType<>() {
                        @Override
                        public String toString() {
                            return ArsArcaneMatrix.MOD_ID + ":arcane_machine_upgrade";
                        }
                    });

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
        TYPES.register(eventBus);
        eventBus.addListener(ModRecipeTypes::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ArsNouveauAPI.getInstance().getEnchantingRecipeTypes()
                .add(ARCANE_MACHINE_UPGRADE_TYPE.get()));
    }
}
