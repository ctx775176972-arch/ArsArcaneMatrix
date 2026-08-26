package dev.arsmatrix.item;

import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** One provider item whose persistent component selects its installed capacity. */
public final class WixiePatternProviderItem extends BlockItem {
    public WixiePatternProviderItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag
    ) {
        int tier = Math.max(0, Math.min(WixiePatternProviderBlockEntity.MAX_UPGRADE_TIER,
                stack.getOrDefault(ModDataComponents.PATTERN_PROVIDER_TIER.get(), 0)));
        int slots = WixiePatternProviderBlockEntity.GUIDE_SLOTS_PER_TIER * (tier + 1);
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.pattern_provider.tier", tier, slots));
    }
}
