package dev.arsmatrix.client;

import dev.arsmatrix.compat.RecipeAutomationSupport;
import dev.arsmatrix.item.CraftingGuideItem;
import dev.arsmatrix.menu.WixiePatternProviderMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Six rows of persistent, physical encoded guides plus the player inventory. */
public final class WixiePatternProviderScreen extends AbstractContainerScreen<WixiePatternProviderMenu> {
    private Button previousPage;
    private Button nextPage;
    private Button sortName;
    private Button sortWorkstation;

    public WixiePatternProviderScreen(
            WixiePatternProviderMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 232;
        inventoryLabelY = 138;
    }

    @Override
    protected void init() {
        super.init();
        previousPage = addRenderableWidget(Button.builder(Component.literal("‹"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_PREVIOUS_PAGE))
                .bounds(leftPos + 137, topPos + 4, 14, 11).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal("›"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_NEXT_PAGE))
                .bounds(leftPos + 154, topPos + 4, 14, 11).build());
        sortName = addRenderableWidget(Button.builder(Component.literal("A"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_SORT_NAME))
                .bounds(leftPos + imageWidth + 3, topPos + 17, 16, 14).build());
        sortWorkstation = addRenderableWidget(Button.builder(Component.literal("▣"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_SORT_WORKSTATION))
                .bounds(leftPos + imageWidth + 3, topPos + 34, 16, 14).build());
        updatePageButtons();
    }

    private void sendPageButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updatePageButtons() {
        if (previousPage == null || nextPage == null) return;
        previousPage.visible = menu.getPageCount() > 1;
        nextPage.visible = menu.getPageCount() > 1;
        previousPage.active = menu.getPage() > 0;
        nextPage.active = menu.getPage() + 1 < menu.getPageCount();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xED181027);
        graphics.fill(leftPos + 5, topPos + 25, leftPos + 171, topPos + 139, 0xD02A1D42);
        graphics.fill(leftPos + 5, topPos + 145, leftPos + 171, topPos + 227, 0xD0201730);
        for (int row = 0; row < WixiePatternProviderMenu.GUIDE_ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                int x = leftPos + 7 + column * 18;
                int y = topPos + 27 + row * 18;
                drawItemSlot(graphics, x, y);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawItemSlot(graphics,
                        leftPos + 7 + column * 18,
                        topPos + 148 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawItemSlot(graphics, leftPos + 7 + column * 18, topPos + 206);
        }
    }

    private static void drawItemSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
        graphics.fill(x + 1, y + 16, x + 17, y + 17, 0xFF100C18);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePageButtons();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawWorkstationBadges(graphics);
        // Item rendering is buffered. Flush the corner badges before drawing tooltips or their
        // item quads may be submitted after the tooltip and appear on top of its text/background.
        graphics.flush();
        // The workstation icon is only a visual corner badge. The guide slot owns
        // the full hover area so the badge cannot replace its useful tooltip.
        renderTooltip(graphics, mouseX, mouseY);
        if (sortName != null && sortName.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.pattern_provider.sort_name.tooltip"), mouseX, mouseY);
        } else if (sortWorkstation != null && sortWorkstation.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.pattern_provider.sort_workstation.tooltip"), mouseX, mouseY);
        }
    }

    private void drawWorkstationBadges(GuiGraphics graphics) {
        int first = menu.getPage() * WixiePatternProviderMenu.PAGE_SIZE;
        int last = Math.min(menu.getGuideSlots(), first + WixiePatternProviderMenu.PAGE_SIZE);
        for (int index = first; index < last; index++) {
            Slot slot = menu.getSlot(index);
            ItemStack guide = slot.getItem();
            if (guide.isEmpty() || CraftingGuideItem.getRecipeId(guide) == null) continue;
            ResourceLocation workstation = minecraft.level == null
                    ? CraftingGuideItem.getWorkstationId(guide)
                    : CraftingGuideItem.getWorkstationId(
                            guide, minecraft.level.getRecipeManager());
            ItemStack icon = BuiltInRegistries.ITEM.getOptional(workstation)
                    .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.CRAFTING_TABLE));
            int x = leftPos + slot.x + 9;
            int y = topPos + slot.y + 9;
            graphics.fill(x - 1, y - 1, x + 8, y + 8, 0xD0080610);
            graphics.pose().pushPose();
            // GuiGraphics.renderItem adds another +150 Z internally. Keeping this at 300
            // placed the badge at Z=450, above Minecraft tooltips (Z=400). Z=200 still
            // keeps the badge above the guide item while allowing every tooltip layer to
            // cover it normally.
            graphics.pose().translate(x, y, 200.0F);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
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

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE8D9FF, false);
        int used = menu.getUsedGuideSlots();
        Component capacityText = Component.translatable(
                "screen.ars_arcane_matrix.pattern_provider.capacity",
                used, menu.getGuideSlots(), menu.getGuideSlots() - used, menu.getUpgradeTier());
        float scale = Math.min(1.0F, 160.0F / Math.max(1, font.width(capacityText)));
        graphics.pose().pushPose();
        graphics.pose().translate(8.0F, 17.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, capacityText, 0, 0, 0xCBBCE3, false);
        graphics.pose().popPose();
        if (menu.getPageCount() > 1) {
            Component pageText = Component.literal(
                    (menu.getPage() + 1) + "/" + menu.getPageCount());
            graphics.drawString(font, pageText,
                    134 - font.width(pageText), titleLabelY, 0xCBBCE3, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                0xCBBCE3, false);
    }
}
