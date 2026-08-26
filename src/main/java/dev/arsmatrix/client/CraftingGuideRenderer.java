package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Lighting;
import dev.arsmatrix.item.CraftingGuideItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

/** Renders an encoded guide as workstation in back and requested output in front. */
public final class CraftingGuideRenderer extends BlockEntityWithoutLevelRenderer {

    public CraftingGuideRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        var renderer = minecraft.getItemRenderer();
        boolean gui = displayContext == ItemDisplayContext.GUI;
        // Child stacks must use the GUI transform while the guide is rendered in a slot.
        // NONE renders the raw baked model, which made both layers look like pieces of a
        // block texture instead of the same item icons shown by a normal inventory slot.
        // The parent guide owns the hand transform. Reapplying each child item's
        // first/third-person transform pushed the composed guide to the bottom of the hand.
        ItemDisplayContext nestedContext = gui ? ItemDisplayContext.GUI : ItemDisplayContext.FIXED;
        int childLight = gui ? LightTexture.FULL_BRIGHT : packedLight;
        var recipeId = CraftingGuideItem.getRecipeId(stack);
        if (recipeId == null) {
            ItemStack parchment = BuiltInRegistries.ITEM.getOptional(
                            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "blank_parchment"))
                    .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.PAPER));
            poseStack.pushPose();
            if (gui) poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(0.86F, 0.86F, 0.86F);
            renderChild(renderer, parchment, nestedContext, gui, childLight, packedOverlay,
                    poseStack, buffer, minecraft, 0);
            poseStack.popPose();

            poseStack.pushPose();
            if (gui) poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.18F, -0.18F, 0.38F);
            poseStack.scale(0.42F, 0.42F, 0.42F);
            renderChild(renderer, new ItemStack(Items.CRAFTING_TABLE), nestedContext, gui,
                    childLight, packedOverlay, poseStack, buffer, minecraft, 1);
            poseStack.popPose();
            return;
        }

        ItemStack workstation = BuiltInRegistries.ITEM.getOptional(
                        CraftingGuideItem.getWorkstationId(stack))
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.CRAFTING_TABLE));
        ItemStack output = CraftingGuideItem.getRecordedResult(stack);
        if (output.isEmpty() && minecraft.level != null) {
            output = minecraft.level.getRecipeManager().byKey(recipeId)
                    .map(holder -> holder.value().getResultItem(minecraft.level.registryAccess()).copyWithCount(1))
                    .orElse(ItemStack.EMPTY);
        }

        // Compose both GUI-transformed child item models inside the guide's local slot space.
        // The conservative scales keep even isometric block-item icons inside the slot.
        poseStack.pushPose();
        if (gui) poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.92F, 0.92F, 0.92F);
        renderChild(renderer, workstation, nestedContext, gui, childLight, packedOverlay,
                poseStack, buffer, minecraft, 1);
        poseStack.popPose();

        if (!output.isEmpty()) {
            poseStack.pushPose();
            if (gui) poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.18F, -0.18F, 0.35F);
            poseStack.scale(0.62F, 0.62F, 0.62F);
            renderChild(renderer, output, nestedContext, gui, childLight, packedOverlay,
                    poseStack, buffer, minecraft, 2);
            poseStack.popPose();
        }
    }

    /**
     * A BEWLR is rendered under its parent's lighting choice. Since the guide's
     * builtin/entity parent uses 3D lighting, nested flat item models otherwise
     * become brown or nearly black in inventories and JEI. Mirror GuiGraphics'
     * per-item lighting selection and flush before restoring the 3D lights.
     */
    private static void renderChild(
            ItemRenderer renderer,
            ItemStack child,
            ItemDisplayContext context,
            boolean gui,
            int light,
            int overlay,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Minecraft minecraft,
            int seed
    ) {
        var model = renderer.getModel(child, minecraft.level, null, seed);
        boolean flatGuiModel = gui && !model.usesBlockLight();
        if (flatGuiModel) Lighting.setupForFlatItems();
        renderer.render(child, context, false, poseStack, buffers, light, overlay, model);
        if (flatGuiModel && buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
        if (flatGuiModel) Lighting.setupFor3DItems();
    }
}
