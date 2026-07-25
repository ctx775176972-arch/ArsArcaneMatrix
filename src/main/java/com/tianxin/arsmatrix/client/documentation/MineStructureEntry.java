package com.tianxin.arsmatrix.client.documentation;

import com.hollingsworth.arsnouveau.api.documentation.SinglePageCtor;
import com.hollingsworth.arsnouveau.api.documentation.SinglePageWidget;
import com.hollingsworth.arsnouveau.client.gui.documentation.BaseDocScreen;
import com.tianxin.arsmatrix.config.MatrixConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Compact configured-layer preview for the inverted-beacon Arcane Mine. */
public final class MineStructureEntry extends SinglePageWidget {

    private static final int FRAME_COLOR = 0xFF7B5AB5;
    private static final int NODE_COLOR = 0xFF40D9E6;
    private static final int GRID_COLOR = 0x55302040;

    private MineStructureEntry(BaseDocScreen parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }

    public static SinglePageCtor create() {
        return MineStructureEntry::new;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        List<Integer> sizes = MatrixConfig.mineLayerSizes();
        for (int layer = 0; layer < sizes.size(); layer++) {
            int column = layer % 2;
            int row = layer / 2;
            drawLayer(graphics, font, layer, sizes.get(layer),
                    getX() + 12 + column * 62, getY() + 17 + row * 55);
        }
    }

    private void drawLayer(GuiGraphics graphics, Font font, int layer, int size, int gridX, int gridY) {
        int cell = Math.max(2, Math.min(4, 32 / size));
        int radius = size / 2;
        graphics.drawString(font, "L" + (layer + 1) + " " + size + "x" + size,
                gridX, gridY - 11, 0xFF5B4670, false);
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                boolean node = x == 0 && z == 0
                        || Math.abs(x) == radius && Math.abs(z) == radius;
                int cellX = gridX + (x + radius) * cell;
                int cellY = gridY + (z + radius) * cell;
                graphics.fill(cellX, cellY, cellX + cell, cellY + cell, GRID_COLOR);
                graphics.fill(cellX + 1, cellY + 1, cellX + cell, cellY + cell,
                        node ? NODE_COLOR : FRAME_COLOR);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    }
}
