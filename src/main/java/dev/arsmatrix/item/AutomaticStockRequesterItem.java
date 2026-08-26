package dev.arsmatrix.item;

import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Upgradeable stock requester item with a persistent amount tier. */
public final class AutomaticStockRequesterItem extends BlockItem {
    public AutomaticStockRequesterItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag
    ) {
        int tier = Math.max(0, Math.min(AutomaticStockRequesterBlockEntity.MAX_UPGRADE_TIER,
                stack.getOrDefault(ModDataComponents.STOCK_REQUESTER_TIER.get(), 0)));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.stock_requester.tier", tier,
                AutomaticStockRequesterBlockEntity.amountLimitForTier(tier)));
    }
}
