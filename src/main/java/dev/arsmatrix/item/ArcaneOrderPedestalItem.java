package dev.arsmatrix.item;

import dev.arsmatrix.blockentity.ArcaneOrderPedestalBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** One order pedestal item whose component selects dispatch speed and parallelism. */
public final class ArcaneOrderPedestalItem extends BlockItem {
    public ArcaneOrderPedestalItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag
    ) {
        int tier = Math.max(0, Math.min(ArcaneOrderPedestalBlockEntity.MAX_UPGRADE_TIER,
                stack.getOrDefault(ModDataComponents.ORDER_PEDESTAL_TIER.get(), 0)));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.order_pedestal.tier",
                tier,
                ArcaneOrderPedestalBlockEntity.maxParallelLabel(tier),
                ArcaneOrderPedestalBlockEntity.dispatchIntervalTicks(tier) / 20.0D));
    }
}
