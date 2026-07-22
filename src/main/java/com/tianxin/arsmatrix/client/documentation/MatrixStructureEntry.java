package com.tianxin.arsmatrix.client.documentation;

import com.hollingsworth.arsnouveau.api.documentation.SinglePageCtor;
import com.hollingsworth.arsnouveau.api.documentation.SinglePageWidget;
import com.hollingsworth.arsnouveau.client.gui.documentation.BaseDocScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Graphical five-layer preview matching the Matrix Core's 42 checked frame positions. */
public final class MatrixStructureEntry extends SinglePageWidget {

    private static final int FRAME_COLOR = 0xFF9B63D7;
    private static final int CORE_COLOR = 0xFF40D9E6;
    private static final int EMPTY_COLOR = 0x22101018;
    private static final int GRID_COLOR = 0x55302040;

    private static final String[][] LAYERS = {
            {"..F..", "..F..", "FFFFF", "..F..", "..F.."},
            {"..F..", ".....", "F...F", ".....", "..F.."},
            {"FFFFF", "F...F", "F.C.F", "F...F", "FFFFF"},
            {"..F..", ".....", "F...F", ".....", "..F.."},
            {"..F..", "..F..", "FFFFF", "..F..", "..F.."}
    };

    private MatrixStructureEntry(BaseDocScreen parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }

    public static SinglePageCtor create() {
        return MatrixStructureEntry::new;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);

        Font font = Minecraft.getInstance().font;
        for (int layer = 0; layer < LAYERS.length; layer++) {
            int column = layer % 3;
            int row = layer / 3;
            int gridX = getX() + 7 + column * 37;
            int gridY = getY() + 12 + row * 48;
            drawLayer(graphics, font, layer, gridX, gridY);
        }

    }

    private void drawLayer(GuiGraphics graphics, Font font, int layer, int gridX, int gridY) {
        graphics.drawCenteredString(font, "Y=" + (layer - 2), gridX + 12, gridY - 9, 0xFF5B4670);

        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                int cellX = gridX + x * 5;
                int cellY = gridY + z * 5;
                char symbol = LAYERS[layer][z].charAt(x);
                int color = symbol == 'F' ? FRAME_COLOR : symbol == 'C' ? CORE_COLOR : EMPTY_COLOR;
                graphics.fill(cellX, cellY, cellX + 4, cellY + 4, GRID_COLOR);
                graphics.fill(cellX + 1, cellY + 1, cellX + 4, cellY + 4, color);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    }
}
