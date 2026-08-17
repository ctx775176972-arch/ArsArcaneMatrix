package dev.arsmatrix.compat.jei;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.data.ArcaneMineOreRule;
import dev.arsmatrix.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class ArcaneMineJeiCategory implements IRecipeCategory<ArcaneMineOreRule> {

    public static final RecipeType<ArcaneMineOreRule> TYPE = RecipeType.create(
            ArsArcaneMatrix.MOD_ID,
            "arcane_mining",
            ArcaneMineOreRule.class
    );

    private static final ResourceLocation SOURCESTONE_TAG = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "arcane_mine_material_sourcestone"
    );
    private static final ResourceLocation SOURCE_GEM_TAG = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "arcane_mine_material_source_gem"
    );
    private static final ResourceLocation SOURCE_GEM_BLOCK_TAG = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "arcane_mine_material_source_gem_block"
    );

    private final IDrawable icon;

    public ArcaneMineJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(ModBlocks.ARCANE_MINE_CORE.get());
    }

    @Override
    public RecipeType<ArcaneMineOreRule> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ars_arcane_matrix.arcane_mining");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 154;
    }

    @Override
    public int getHeight() {
        return 68;
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            ArcaneMineOreRule recipe,
            IFocusGroup focuses
    ) {
        builder.addInputSlot(6, 29)
                .setStandardSlotBackground()
                .addItemStacks(materialOptions(recipe.materialPoints()));
        builder.addOutputSlot(130, 29)
                .setOutputSlotBackground()
                .addItemStacks(outputOptions(recipe));
    }

    @Override
    public void draw(
            ArcaneMineOreRule recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        var font = Minecraft.getInstance().font;
        List<FormattedCharSequence> requirementLines = font.split(Component.translatable(
                "jei.ars_arcane_matrix.arcane_mining.requirements",
                recipe.requiredLayers(), recipe.materialPoints()), getWidth() - 4);
        for (int line = 0; line < Math.min(2, requirementLines.size()); line++) {
            drawCenteredWithoutShadow(graphics, font, requirementLines.get(line),
                    2 + line * 10, 0x404040);
        }
        drawCenteredWithoutShadow(
                graphics,
                font,
                Component.translatable(
                        "jei.ars_arcane_matrix.arcane_mining.source",
                        recipe.sourceCost()
                ),
                54,
                0x404040
        );
    }

    private void drawCenteredWithoutShadow(
            GuiGraphics graphics,
            net.minecraft.client.gui.Font font,
            Component text,
            int y,
            int color
    ) {
        int x = (getWidth() - font.width(text)) / 2;
        graphics.drawString(font, text, x, y, color, false);
    }

    private void drawCenteredWithoutShadow(
            GuiGraphics graphics,
            net.minecraft.client.gui.Font font,
            FormattedCharSequence text,
            int y,
            int color
    ) {
        int x = (getWidth() - font.width(text)) / 2;
        graphics.drawString(font, text, x, y, color, false);
    }

    @Override
    public ResourceLocation getRegistryName(ArcaneMineOreRule recipe) {
        return recipe.id();
    }

    private static List<ItemStack> materialOptions(int requiredPoints) {
        List<ItemStack> stacks = new ArrayList<>();
        addTaggedStacks(stacks, SOURCESTONE_TAG, requiredPoints, MatrixConfig.MINE_SOURCESTONE_POINTS.get());
        addTaggedStacks(stacks, SOURCE_GEM_TAG, requiredPoints, MatrixConfig.MINE_SOURCE_GEM_POINTS.get());
        addTaggedStacks(
                stacks,
                SOURCE_GEM_BLOCK_TAG,
                requiredPoints,
                MatrixConfig.MINE_SOURCE_GEM_BLOCK_POINTS.get()
        );
        return stacks;
    }

    private static void addTaggedStacks(
            List<ItemStack> stacks,
            ResourceLocation tagId,
            int requiredPoints,
            int pointsPerItem
    ) {
        int count = Math.max(1, (requiredPoints + pointsPerItem - 1) / pointsPerItem);
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        BuiltInRegistries.ITEM.getTag(tag).stream()
                .flatMap(named -> named.stream().map(Holder::value))
                .filter(item -> item != Items.AIR)
                .map(item -> new ItemStack(item, count))
                .forEach(stacks::add);
    }

    private static List<ItemStack> outputOptions(ArcaneMineOreRule recipe) {
        if (!recipe.outputIsTag()) {
            Item item = BuiltInRegistries.ITEM.getOptional(recipe.output()).orElse(Items.AIR);
            return !ArcaneMineOreRule.isAllowedOutput(item)
                    ? List.of()
                    : List.of(new ItemStack(item, recipe.outputCount()));
        }

        TagKey<Item> tag = TagKey.create(Registries.ITEM, recipe.output());
        return BuiltInRegistries.ITEM.getTag(tag).stream()
                .flatMap(named -> named.stream().map(Holder::value))
                .filter(ArcaneMineOreRule::isAllowedOutput)
                .map(item -> new ItemStack(item, recipe.outputCount()))
                .toList();
    }
}
