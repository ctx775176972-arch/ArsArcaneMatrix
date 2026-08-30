package dev.arsmatrix.compat.jei;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.data.ArcaneHuntingRule;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModItems;
import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public final class ArcaneHuntingJeiCategory implements IRecipeCategory<ArcaneHuntingRule> {
    public static final RecipeType<ArcaneHuntingRule> TYPE = RecipeType.create(
            ArsArcaneMatrix.MOD_ID, "arcane_hunting", ArcaneHuntingRule.class);
    private final IDrawable icon;

    public ArcaneHuntingJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(ModBlocks.DRYGMY_ARENA.get());
    }
    @Override public RecipeType<ArcaneHuntingRule> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.ars_arcane_matrix.arcane_hunting"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 150; }
    @Override public int getHeight() { return 72; }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, ArcaneHuntingRule recipe, IFocusGroup focuses) {
        builder.addInputSlot(8, 27).setStandardSlotBackground().addItemStack(filledJar(recipe));
        ItemStack normal = new ItemStack(ItemsRegistry.CONJURATION_ESSENCE.get(),
                Math.ceilDiv(recipe.pointCost(), 10));
        ItemStack condensed = new ItemStack(ModItems.CONDENSED_SUMMONING_CATALYST.get(),
                Math.ceilDiv(recipe.pointCost(), 100));
        builder.addInputSlot(66, 13).setStandardSlotBackground()
                .addItemStacks(java.util.List.of(normal, condensed))
                .addRichTooltipCallback((slot, tooltip) -> slot.getDisplayedItemStack().ifPresent(stack -> {
                    String key = stack.is(ModItems.CONDENSED_SUMMONING_CATALYST.get())
                            ? "jei.ars_arcane_matrix.arcane_hunting.catalyst.advanced"
                            : "jei.ars_arcane_matrix.arcane_hunting.catalyst.normal";
                    tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GOLD));
                }));
        int index = 0;
        for (ItemStack output : recipe.createOutputs()) {
            builder.addOutputSlot(123 - (index % 2) * 20, 27 + (index / 2) * 20)
                    .setOutputSlotBackground()
                    .addItemStack(output);
            index++;
        }
    }

    @Override public void draw(ArcaneHuntingRule recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                               double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        Component target = BuiltInRegistries.ENTITY_TYPE.getOptional(recipe.entityId())
                .map(EntityType::getDescription)
                .orElseGet(() -> Component.literal(recipe.entityId().toString()));
        String targetText = target.getString();
        if (font.width(targetText) > 146) {
            targetText = font.plainSubstrByWidth(targetText, 137) + "…";
        }
        graphics.drawString(font, targetText, 2, 1, 0x404040, false);
        graphics.drawString(font, Component.literal("→"), 91, 37, 0x8050A0, false);
        graphics.drawString(font,
                Component.translatable("jei.ars_arcane_matrix.arcane_hunting.time",
                        Math.ceilDiv(MatrixConfig.DRYGMY_ARENA_CYCLE_TICKS.get(), 20)),
                4, 58, 0x404040, false);
    }

    private static ItemStack filledJar(ArcaneHuntingRule recipe) {
        ItemStack jar = new ItemStack(BlockRegistry.MOB_JAR.get());
        CompoundTag entity = new CompoundTag();
        entity.putString("id", recipe.entityId().toString());
        jar.set(DataComponentRegistry.MOB_JAR.get(), new MobJarData(entity, new CompoundTag()));
        return jar;
    }
    @Override public ResourceLocation getRegistryName(ArcaneHuntingRule recipe) { return recipe.id(); }
}
