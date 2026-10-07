package dev.arsmatrix.item;

import dev.arsmatrix.compat.DynamicCraftingRecipeSupport;
import dev.arsmatrix.compat.RecipeAutomationSupport;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import dev.arsmatrix.client.CraftingGuideRenderer;
import net.minecraft.client.Minecraft;

/** A physical recipe pattern taught by using it on a crafting table with a sample in the off hand. */
public final class CraftingGuideItem extends Item {

    private static final String RECIPE_KEY = "Recipe";
    private static final String FUZZY_KEY = "FuzzyTags";
    private static final String WORKSTATION_KEY = "Workstation";
    private static final String RESULT_ITEM_KEY = "ResultItem";
    private static final String RESULT_STACK_KEY = "ResultStack";

    public CraftingGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private final CraftingGuideRenderer renderer = new CraftingGuideRenderer();

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.CRAFTING_TABLE)) {
            return InteractionResult.PASS;
        }
        ItemStack sample = context.getPlayer() == null
                ? ItemStack.EMPTY
                : context.getPlayer().getOffhandItem();
        if (sample.isEmpty()) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide) {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.crafting_guide.need_sample"
                ), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (!context.getLevel().isClientSide) {
            Optional<RecipeHolder<CraftingRecipe>> match = context.getLevel().getRecipeManager()
                    .getAllRecipesFor(RecipeType.CRAFTING)
                    .stream()
                    .filter(holder -> {
                        CraftingRecipe recipe = holder.value();
                        if ((recipe.isSpecial() && !DynamicCraftingRecipeSupport.supports(recipe))
                                || DynamicCraftingRecipeSupport.ingredients(recipe).isEmpty()) {
                            return false;
                        }
                        ItemStack output = DynamicCraftingRecipeSupport.result(
                                recipe, context.getLevel().registryAccess());
                        return ItemStack.isSameItemSameComponents(output, sample);
                    })
                    .findFirst();
            if (match.isEmpty()) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable(
                            "message.ars_arcane_matrix.crafting_guide.no_recipe",
                            sample.getHoverName()
                    ), true);
                }
                return InteractionResult.SUCCESS;
            }
            encode(context.getItemInHand(), match.get(), sample,
                    context.getLevel().registryAccess());
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.crafting_guide.recorded",
                        sample.getHoverName()
                ), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || getRecipeId(stack) == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            setFuzzy(stack, !isFuzzy(stack));
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.mode",
                    Component.translatable(isFuzzy(stack)
                            ? "tooltip.ars_arcane_matrix.crafting_guide.mode.fuzzy"
                            : "tooltip.ars_arcane_matrix.crafting_guide.mode.strict")
            ), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        ResourceLocation recipe = getRecipeId(stack);
        ItemStack recordedResult = recipe == null ? ItemStack.EMPTY : getRecordedResult(stack);
        if (recordedResult.isEmpty() && recipe != null && Minecraft.getInstance().level != null) {
            recordedResult = RecipeAutomationSupport.find(
                            Minecraft.getInstance().level.getRecipeManager(), recipe)
                    .filter(holder -> RecipeAutomationSupport.supports(holder.value()))
                    .map(holder -> RecipeAutomationSupport.result(
                            holder.value(), Minecraft.getInstance().level.registryAccess()).copyWithCount(1))
                    .orElse(ItemStack.EMPTY);
        }
        tooltip.add(recipe == null
                ? Component.translatable("tooltip.ars_arcane_matrix.crafting_guide.blank")
                : Component.translatable("tooltip.ars_arcane_matrix.crafting_guide.recipe",
                        recordedResult.getHoverName()));
        if (!recordedResult.isEmpty()) {
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(recordedResult).entrySet()) {
                tooltip.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
            }
        }
        if (recipe != null) {
            tooltip.add(Component.translatable(
                    "tooltip.ars_arcane_matrix.crafting_guide.workstation",
                    Component.translatable(workstationTranslation(
                            Minecraft.getInstance().level == null
                                    ? getWorkstationId(stack)
                                    : getWorkstationId(stack,
                                            Minecraft.getInstance().level.getRecipeManager())))));
            tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.crafting_guide.mode",
                    Component.translatable(isFuzzy(stack)
                            ? "tooltip.ars_arcane_matrix.crafting_guide.mode.fuzzy"
                            : "tooltip.ars_arcane_matrix.crafting_guide.mode.strict")));
        }
    }

    private static String workstationTranslation(ResourceLocation workstation) {
        if (RecipeAutomationSupport.SOURCE_STONE_FURNACE.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.furnace";
        }
        if (RecipeAutomationSupport.ENCHANTING_APPARATUS.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.apparatus";
        }
        if (RecipeAutomationSupport.IMBUEMENT_CHAMBER.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.imbuement";
        }
        if (RecipeAutomationSupport.STONECUTTER.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.stonecutter";
        }
        if (RecipeAutomationSupport.ARCANE_REACTION_VESSEL.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.reaction_vessel";
        }
        if (RecipeAutomationSupport.FARMERS_DELIGHT_COOKING_POT.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.cooking_pot";
        }
        if (RecipeAutomationSupport.AVARITIA_NEUTRON_COMPRESSOR.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.avaritia_compressor";
        }
        if (RecipeAutomationSupport.AVARITIA_SCULK_CRAFTING_TABLE.equals(workstation)
                || RecipeAutomationSupport.AVARITIA_NETHER_CRAFTING_TABLE.equals(workstation)
                || RecipeAutomationSupport.AVARITIA_END_CRAFTING_TABLE.equals(workstation)
                || RecipeAutomationSupport.AVARITIA_EXTREME_CRAFTING_TABLE.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.avaritia_table";
        }
        return "screen.ars_arcane_matrix.order_terminal.workstation.crafting";
    }

    public static ResourceLocation getRecipeId(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(RECIPE_KEY, Tag.TAG_STRING)) {
            return null;
        }
        String encodedId = tag.getString(RECIPE_KEY);
        if (encodedId.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(encodedId);
    }

    public static boolean isFuzzy(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return !tag.contains(FUZZY_KEY) || tag.getBoolean(FUZZY_KEY);
    }

    public static ResourceLocation getWorkstationId(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String value = data.copyTag().getString(WORKSTATION_KEY);
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? ResourceLocation.withDefaultNamespace("crafting_table") : parsed;
    }

    /**
     * Resolves the workstation from the live recipe when possible. This also
     * repairs the presentation of guides encoded by older versions whose
     * compatibility tier mapping was incorrect.
     */
    public static ResourceLocation getWorkstationId(ItemStack stack, RecipeManager recipes) {
        ResourceLocation recipeId = getRecipeId(stack);
        if (recipeId == null || recipes == null) return getWorkstationId(stack);
        return RecipeAutomationSupport.find(recipes, recipeId)
                .filter(holder -> RecipeAutomationSupport.supports(holder.value()))
                .map(holder -> RecipeAutomationSupport.workstation(holder.value()))
                .orElseGet(() -> getWorkstationId(stack));
    }

    public static ItemStack getRecordedResult(ItemStack stack) {
        if (Minecraft.getInstance().level != null) {
            var level = Minecraft.getInstance().level;
            ResourceLocation currentRecipeId = getRecipeId(stack);
            if (currentRecipeId != null) {
                var current = RecipeAutomationSupport.find(level.getRecipeManager(), currentRecipeId);
                if (current.isPresent() && RecipeAutomationSupport.isAvaritiaCompressor(current.get().value())) {
                    ItemStack actual = RecipeAutomationSupport.result(current.get().value(), level.registryAccess());
                    if (!actual.isEmpty()) return actual.copyWithCount(1);
                }
            }
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (data.copyTag().contains(RESULT_STACK_KEY, Tag.TAG_COMPOUND)) {
                return getRecordedResult(stack, level.registryAccess());
            }
            ResourceLocation recipeId = getRecipeId(stack);
            if (recipeId != null) {
                ItemStack rebuilt = RecipeAutomationSupport.find(level.getRecipeManager(), recipeId)
                        .filter(holder -> RecipeAutomationSupport.supports(holder.value()))
                        .map(holder -> RecipeAutomationSupport.result(
                                holder.value(), level.registryAccess()).copyWithCount(1))
                        .orElse(ItemStack.EMPTY);
                if (!rebuilt.isEmpty()) return rebuilt;
            }
        }
        return getLegacyRecordedResult(stack);
    }

    public static ItemStack getRecordedResult(
            ItemStack stack, net.minecraft.core.HolderLookup.Provider registries
    ) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (tag.contains(RESULT_STACK_KEY, Tag.TAG_COMPOUND)) {
            ItemStack result = ItemStack.parseOptional(registries, tag.getCompound(RESULT_STACK_KEY));
            if (!result.isEmpty()) return result;
        }
        return getLegacyRecordedResult(stack);
    }

    private static ItemStack getLegacyRecordedResult(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        ResourceLocation id = ResourceLocation.tryParse(data.copyTag().getString(RESULT_ITEM_KEY));
        return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static void setFuzzy(ItemStack stack, boolean fuzzy) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(FUZZY_KEY, fuzzy);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void encode(
            ItemStack stack,
            RecipeHolder<CraftingRecipe> recipe,
            ItemStack result,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        encodeRecipe(stack, recipe, result, registries);
    }

    public static void encodeRecipe(
            ItemStack stack,
            RecipeHolder<?> recipe,
            ItemStack result,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(RECIPE_KEY, recipe.id().toString());
        tag.putString(WORKSTATION_KEY, RecipeAutomationSupport.workstation(recipe.value()).toString());
        if (!result.isEmpty()) {
            ItemStack recordedResult = result.copyWithCount(1);
            tag.putString(RESULT_ITEM_KEY,
                    BuiltInRegistries.ITEM.getKey(recordedResult.getItem()).toString());
            tag.put(RESULT_STACK_KEY, recordedResult.saveOptional(registries));
        }
        tag.putBoolean(FUZZY_KEY, !(recipe.value() instanceof CraftingRecipe crafting)
                || !requiresStrictComponents(crafting));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static boolean requiresStrictComponents(CraftingRecipe recipe) {
        for (var ingredient : recipe.getIngredients()) {
            for (ItemStack candidate : ingredient.getItems()) {
                if (!candidate.getComponentsPatch().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
