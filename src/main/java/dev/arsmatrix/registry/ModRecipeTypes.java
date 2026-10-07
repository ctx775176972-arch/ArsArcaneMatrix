package dev.arsmatrix.registry;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.recipe.ArcaneMachineUpgradeRecipe;
import dev.arsmatrix.recipe.UnbreakableApparatusRecipe;
import dev.arsmatrix.recipe.CowMobJarIngredient;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Recipe registrations that extend Ars Nouveau's processing blocks. */
public final class ModRecipeTypes {
    private ModRecipeTypes() {}

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ArsArcaneMatrix.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ArsArcaneMatrix.MOD_ID);
    private static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, ArsArcaneMatrix.MOD_ID);
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends
            net.neoforged.neoforge.common.conditions.ICondition>> CONDITIONS = DeferredRegister.create(
                    net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS,
                    ArsArcaneMatrix.MOD_ID);

    static {
        CONDITIONS.register("unbreakable_enabled", () -> dev.arsmatrix.recipe.UnbreakableEnabledCondition.CODEC);
        CONDITIONS.register("optional_recipe", () -> dev.arsmatrix.recipe.OptionalRecipeCondition.CODEC);
    }

    public static final DeferredHolder<RecipeSerializer<?>, ArcaneMachineUpgradeRecipe.Serializer>
            ARCANE_MACHINE_UPGRADE_SERIALIZER = SERIALIZERS.register(
                    "arcane_machine_upgrade", ArcaneMachineUpgradeRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, UnbreakableApparatusRecipe.Serializer>
            UNBREAKABLE_APPARATUS_SERIALIZER = SERIALIZERS.register(
                    "unbreakable_apparatus", UnbreakableApparatusRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ArcaneMachineUpgradeRecipe>>
            ARCANE_MACHINE_UPGRADE_TYPE = TYPES.register(
                    "arcane_machine_upgrade", () -> new RecipeType<>() {
                        @Override
                        public String toString() {
                            return ArsArcaneMatrix.MOD_ID + ":arcane_machine_upgrade";
                        }
                    });

    public static final DeferredHolder<IngredientType<?>, IngredientType<CowMobJarIngredient>>
            COW_MOB_JAR_INGREDIENT = INGREDIENT_TYPES.register(
                    "cow_mob_jar", () -> new IngredientType<>(CowMobJarIngredient.CODEC));

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
        TYPES.register(eventBus);
        INGREDIENT_TYPES.register(eventBus);
        CONDITIONS.register(eventBus);
        eventBus.addListener(ModRecipeTypes::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry.register(
                ModItems.ENCHANTERS_POCKET_WATCH.get(), ModItems.ENCHANTERS_POCKET_WATCH.get()));
        event.enqueueWork(() -> ArsNouveauAPI.getInstance().getEnchantingRecipeTypes()
                .add(ARCANE_MACHINE_UPGRADE_TYPE.get()));
    }
}
