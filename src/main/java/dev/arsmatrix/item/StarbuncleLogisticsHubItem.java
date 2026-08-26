package dev.arsmatrix.item;

import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** One logistics hub item whose component selects its shared throughput tier. */
public final class StarbuncleLogisticsHubItem extends BlockItem {
    public StarbuncleLogisticsHubItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        int tier = Math.max(0, Math.min(StarbuncleLogisticsHubBlockEntity.MAX_UPGRADE_TIER,
                stack.getOrDefault(ModDataComponents.LOGISTICS_HUB_TIER.get(), 0)));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.starbuncle_hub.tier",
                tier, StarbuncleLogisticsHubBlockEntity.throughputForTier(tier)));
    }
}
