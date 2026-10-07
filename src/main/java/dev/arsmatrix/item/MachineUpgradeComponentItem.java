package dev.arsmatrix.item;

import dev.arsmatrix.blockentity.ArcaneOrderPedestalBlockEntity;
import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.function.IntConsumer;

/** A craftable, automation-friendly alternative to dismantling a device for apparatus upgrades. */
public final class MachineUpgradeComponentItem extends Item {
    private final int targetTier;

    public MachineUpgradeComponentItem(Properties properties, int targetTier) {
        super(properties);
        this.targetTier = targetTier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        TierAccess access = tierAccess(blockEntity);
        if (access == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;

        if (access.tier() >= access.maxTier()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.machine_upgrade_component.max_tier",
                    blockEntity.getBlockState().getBlock().getName(), access.maxTier()
            ).withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }
        if (access.tier() != targetTier - 1) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.machine_upgrade_component.wrong_tier",
                    access.tier(), targetTier
            ).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        access.setter().accept(targetTier);
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        context.getLevel().playSound(null, context.getClickedPos(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.15F);
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.machine_upgrade_component.applied",
                blockEntity.getBlockState().getBlock().getName(), targetTier
        ).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.machine_upgrade_component", targetTier
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.machine_upgrade_component.use"
        ).withStyle(ChatFormatting.DARK_PURPLE));
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

    private record TierAccess(int tier, int maxTier, IntConsumer setter) {}
}
