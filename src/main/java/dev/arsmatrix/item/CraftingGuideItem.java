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
import net.minecraft.world.item.crafting.RecipeType;
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
            encode(context.getItemInHand(), match.get(), sample);
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
            recordedResult = Minecraft.getInstance().level.getRecipeManager().byKey(recipe)
                    .filter(holder -> holder.value() instanceof CraftingRecipe)
                    .map(holder -> DynamicCraftingRecipeSupport.result(
                            (CraftingRecipe) holder.value(), Minecraft.getInstance().level.registryAccess())
                            .copyWithCount(1))
                    .orElse(ItemStack.EMPTY);
        }
        tooltip.add(recipe == null
                ? Component.translatable("tooltip.ars_arcane_matrix.crafting_guide.blank")
                : Component.translatable("tooltip.ars_arcane_matrix.crafting_guide.recipe",
                        recordedResult.getHoverName()));
        if (recipe != null) {
            tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.crafting_guide.mode",
                    Component.translatable(isFuzzy(stack)
                            ? "tooltip.ars_arcane_matrix.crafting_guide.mode.fuzzy"
                            : "tooltip.ars_arcane_matrix.crafting_guide.mode.strict")));
        }
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

    public static ItemStack getRecordedResult(ItemStack stack) {
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

    public static void encode(ItemStack stack, RecipeHolder<CraftingRecipe> recipe, ItemStack result) {
        encodeRecipe(stack, recipe, result);
    }

    public static void encodeRecipe(ItemStack stack, RecipeHolder<?> recipe, ItemStack result) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(RECIPE_KEY, recipe.id().toString());
        tag.putString(WORKSTATION_KEY, RecipeAutomationSupport.workstation(recipe.value()).toString());
        if (!result.isEmpty()) {
            tag.putString(RESULT_ITEM_KEY, BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
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
