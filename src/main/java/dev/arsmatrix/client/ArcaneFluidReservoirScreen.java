package dev.arsmatrix.client;

import dev.arsmatrix.blockentity.ArcaneFluidReservoirBlockEntity;
import dev.arsmatrix.menu.ArcaneFluidReservoirMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class ArcaneFluidReservoirScreen extends AbstractContainerScreen<ArcaneFluidReservoirMenu> {
    private Button modeButton;
    private Button inputButton;
    private Button outputButton;
    public ArcaneFluidReservoirScreen(ArcaneFluidReservoirMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 244;
        imageHeight = 247;
        inventoryLabelX = 40;
        inventoryLabelY = 156;
    }
    @Override protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> click(2)).bounds(leftPos + 8, topPos + 20, 74, 18).build());
        inputButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> click(0)).bounds(leftPos + 85, topPos + 20, 74, 18).build());
        outputButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> click(1)).bounds(leftPos + 162, topPos + 20, 74, 18).build());
    }
    private void click(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181028);
        graphics.fill(leftPos + 5, topPos + 16, leftPos + 239, topPos + 153, 0xD02A1D42);
        graphics.fill(leftPos + 37, topPos + 163, leftPos + 207, topPos + 243, 0xD0201730);
        for (int i = 0; i < 3; i++) {
            int x = leftPos + 76 + i * 34;
            graphics.fill(x, topPos + 53, x + 24, topPos + 92, 0xFF080610);
            boolean unlocked = i < menu.unlockedTankCount();
            int tankType = menu.tankType(i);
            int height = menu.capacity() <= 0 ? 0 : Math.min(35,
                    (int) ((long) menu.amount(i) * 35L / menu.capacity()));
            if (unlocked && tankType >= 0) drawFluid(graphics, tankType,
                    x + 3, topPos + 89 - height, 18, height);
            else graphics.fill(x + 3, topPos + 89 - height, x + 21, topPos + 89, 0xFF241D2D);
            if (!unlocked) graphics.fill(x + 2, topPos + 54, x + 22, topPos + 90, 0xB0100D16);
            if (menu.outputFluid() == i && unlocked)
                graphics.renderOutline(x - 3, topPos + 50, 30, 45, 0xFFFFB14A);
        }
        for (int slot = 0; slot < 4; slot++) drawSlot(graphics, leftPos + 81 + slot * 20, topPos + 125);
        for (int slot = 0; slot < 2; slot++) drawSlot(graphics, leftPos + 171 + slot * 20, topPos + 125);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawSlot(graphics, leftPos + 39 + column * 18, topPos + 166 + row * 18);
        for (int column = 0; column < 9; column++) drawSlot(graphics, leftPos + 39 + column * 18, topPos + 224);
    }
    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ArcaneFluidReservoirBlockEntity.Mode mode = ArcaneFluidReservoirBlockEntity.Mode.values()[
                Math.floorMod(menu.mode(), ArcaneFluidReservoirBlockEntity.Mode.values().length)];
        modeButton.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.mode_button",
                Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.mode_short." + mode.name().toLowerCase())));
        inputButton.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.input_selected",
                fluidName(menu.inputFluid())));
        outputButton.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.output_selected",
                menu.outputFluid() + 1));
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (int i = 0; i < 3; i++) {
            int x = leftPos + 76 + i * 34;
            if (mouseX >= x && mouseX < x + 24 && mouseY >= topPos + 45 && mouseY < topPos + 93) {
                graphics.renderTooltip(font, Component.translatable(
                        "screen.ars_arcane_matrix.arcane_fluid_reservoir.amount_exact",
                        i + 1, tankStatus(i), menu.amount(i), menu.capacity()), mouseX, mouseY);
            }
        }
    }
    private static Component fluidName(int index) {
        Fluid fluid = BuiltInRegistries.FLUID.byId(index);
        return fluid == Fluids.EMPTY
                ? Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.empty")
                : new FluidStack(fluid, 1).getHoverName();
    }
    private static void drawFluid(GuiGraphics graphics, int registryId, int x, int y, int width, int height) {
        if (height <= 0) return;
        Fluid fluid = BuiltInRegistries.FLUID.byId(registryId);
        if (fluid == Fluids.EMPTY) return;
        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation texture = properties.getStillTexture(new FluidStack(fluid, 1));
        int tint = properties.getTintColor(new FluidStack(fluid, 1));
        graphics.setColor(((tint >>> 16) & 255) / 255.0F, ((tint >>> 8) & 255) / 255.0F,
                (tint & 255) / 255.0F, ((tint >>> 24) & 255) / 255.0F);
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(texture);
        for (int offsetX = 0; offsetX < width; offsetX += 16) {
            for (int offsetY = 0; offsetY < height; offsetY += 16) {
                graphics.blit(x + offsetX, y + offsetY, 0,
                        Math.min(16, width - offsetX), Math.min(16, height - offsetY), sprite);
            }
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    private Component tankStatus(int tank) {
        if (tank >= menu.unlockedTankCount())
            return Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.locked");
        int type = menu.tankType(tank);
        return type < 0
                ? Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.empty")
                : fluidName(type);
    }
    private static String compactAmount(int amount) {
        if (amount >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", amount / 1_000_000.0D);
        if (amount >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", amount / 1_000.0D);
        return Integer.toString(amount);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 5, 0xE8D9FF, false);
        for (int i = 0; i < 3; i++) {
            Component name = tankStatus(i);
            graphics.drawCenteredString(font, name, 88 + i * 34, 43, 0xCBBCE3);
            graphics.drawCenteredString(font, compactAmount(menu.amount(i)), 88 + i * 34, 94, 0xD8B8FF);
        }
        ArcaneFluidReservoirBlockEntity.Mode mode = ArcaneFluidReservoirBlockEntity.Mode.values()[
                Math.floorMod(menu.mode(), ArcaneFluidReservoirBlockEntity.Mode.values().length)];
        graphics.drawCenteredString(font, Component.translatable(
                "screen.ars_arcane_matrix.arcane_fluid_reservoir.help." + mode.name().toLowerCase()),
                imageWidth / 2, 106, 0xB8A6D0);
        graphics.drawString(font, Component.translatable("state.ars_arcane_matrix.arcane_fluid_reservoir." +
                        ArcaneFluidReservoirBlockEntity.State.values()[Math.floorMod(menu.state(),
                                ArcaneFluidReservoirBlockEntity.State.values().length)].name().toLowerCase()),
                8, 116, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.upgrades"),
                8, 130, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.modules"),
                164, 116, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.targets",
                        menu.inputTargetCount(), menu.maxWirelessTargets(),
                        menu.outputTargetCount(), menu.maxWirelessTargets()),
                8, 143, 0xAFA0C8, false);
        Component wireless = Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.wireless",
                Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.wireless_tier."
                        + menu.wirelessTier()));
        graphics.drawString(font, wireless, imageWidth - 8 - font.width(wireless), 143, 0xAFA0C8, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xCBBCE3, false);
    }
}
