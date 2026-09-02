package dev.arsmatrix.item;

import dev.arsmatrix.blockentity.ArcaneOrderPedestalBlockEntity;
import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Creative-only helper for changing the persistent tier of upgradeable test devices. */
public final class DeviceTierDebugToolItem extends Item {
    public DeviceTierDebugToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        TierAccess access = tierAccess(blockEntity);
        if (access == null) {
            if (!context.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.device_tier_debug_tool.unsupported"
                ).withStyle(ChatFormatting.YELLOW), true);
            }
            // Always consume the click while this tool is held. It must never fall through
            // to a device GUI, binding action, mode toggle, or any other block interaction.
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;

        if (!player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.device_tier_debug_tool.creative_only"
            ).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        int delta = player.isShiftKeyDown() ? -1 : 1;
        int nextTier = Math.max(0, Math.min(access.maxTier(), access.tier() + delta));
        access.setTier(nextTier);
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.device_tier_debug_tool.changed",
                blockEntity.getBlockState().getBlock().getName(), nextTier, access.maxTier()
        ).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.device_tier_debug_tool.raise"
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.device_tier_debug_tool.lower"
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.device_tier_debug_tool.creative_only"
        ).withStyle(ChatFormatting.RED));
    }

    private static TierAccess tierAccess(BlockEntity blockEntity) {
        if (blockEntity instanceof ArcaneOrderPedestalBlockEntity pedestal) {
            return new TierAccess(pedestal.getUpgradeTier(), ArcaneOrderPedestalBlockEntity.MAX_UPGRADE_TIER,
                    pedestal::setUpgradeTier);
        }
        if (blockEntity instanceof WixiePatternProviderBlockEntity provider) {
            return new TierAccess(provider.getUpgradeTier(), WixiePatternProviderBlockEntity.MAX_UPGRADE_TIER,
                    provider::setUpgradeTier);
        }
        if (blockEntity instanceof AutomaticStockRequesterBlockEntity requester) {
            return new TierAccess(requester.getUpgradeTier(), AutomaticStockRequesterBlockEntity.MAX_UPGRADE_TIER,
                    requester::setUpgradeTier);
        }
        if (blockEntity instanceof StarbuncleLogisticsHubBlockEntity hub) {
            return new TierAccess(hub.getUpgradeTier(), StarbuncleLogisticsHubBlockEntity.MAX_UPGRADE_TIER,
                    hub::setUpgradeTier);
        }
        return null;
    }

    private record TierAccess(int tier, int maxTier, java.util.function.IntConsumer setter) {
        private void setTier(int tier) {
            setter.accept(tier);
        }
    }
}
