package dev.arsmatrix.item;

import com.hollingsworth.arsnouveau.client.renderer.tile.GenericTileRenderer;
import com.hollingsworth.arsnouveau.common.items.RendererBlockItem;
import dev.arsmatrix.client.AdvancedImbuementChamberItemModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/** Uses Ars Nouveau's animated Imbuement Chamber item renderer. */
public final class AdvancedImbuementChamberItem extends RendererBlockItem {
    public AdvancedImbuementChamberItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public Supplier<BlockEntityWithoutLevelRenderer> getRenderer() {
        return GenericTileRenderer.getISTER(new AdvancedImbuementChamberItemModel());
    }
}
