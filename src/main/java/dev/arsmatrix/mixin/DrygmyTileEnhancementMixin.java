package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.api.ANFakePlayer;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.block.tile.DrygmyTile;
import com.hollingsworth.arsnouveau.setup.config.Config;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sharpness on an adjacent pedestal tool accelerates Drygmy channel progress. */
@Mixin(value = DrygmyTile.class, remap = false)
public abstract class DrygmyTileEnhancementMixin {
    @Shadow public int progress;
    @Shadow public boolean needsMana;

    @Unique private int arsMatrix$sharpnessProgress;
    @Unique private int arsMatrix$progressBeforeChannel;

    @Inject(method = "giveProgress", at = @At("HEAD"), remap = false)
    private void arsMatrix$captureProgress(CallbackInfo callback) {
        arsMatrix$progressBeforeChannel = progress;
    }

    @Inject(method = "giveProgress", at = @At("TAIL"), remap = false)
    private void arsMatrix$applySharpnessProgress(CallbackInfo callback) {
        DrygmyTile tile = (DrygmyTile) (Object) this;
        if (!(tile.getLevel() instanceof ServerLevel level)
                || progress <= arsMatrix$progressBeforeChannel) {
            return;
        }

        ItemStack tool = arsMatrix$adjacentTool(level, tile);
        if (tool.isEmpty()) return;

        Holder<Enchantment> sharpness = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        int sharpnessLevel = Math.min(5, Math.max(0,
                EnchantmentHelper.getItemEnchantmentLevel(sharpness, tool)));
        if (sharpnessLevel <= 0) return;

        // Each level grants 10% of an extra channel. The integer accumulator
        // keeps the result deterministic and avoids random speed fluctuations.
        arsMatrix$sharpnessProgress += sharpnessLevel;
        if (arsMatrix$sharpnessProgress >= 10 && progress < tile.getMaxProgress()) {
            arsMatrix$sharpnessProgress -= 10;
            progress++;
            tile.updateBlock();
        }
    }

    @Inject(method = "generateItems", at = @At("HEAD"), remap = false)
    private void arsMatrix$equipLootingTool(CallbackInfo callback) {
        DrygmyTile tile = (DrygmyTile) (Object) this;
        if (!(tile.getLevel() instanceof ServerLevel level)) return;

        ItemStack tool = arsMatrix$adjacentTool(level, tile);
        ANFakePlayer.getPlayer(level).setItemInHand(
                InteractionHand.MAIN_HAND,
                tool.isEmpty() ? ItemStack.EMPTY : tool.copy()
        );
    }

    @Inject(method = "generateItems", at = @At("TAIL"), remap = false)
    private void arsMatrix$clearLootingTool(CallbackInfo callback) {
        DrygmyTile tile = (DrygmyTile) (Object) this;
        if (tile.getLevel() instanceof ServerLevel level) {
            ANFakePlayer.getPlayer(level).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            // Vanilla only retries every 80 ticks. Prepay the next operation
            // immediately so Sharpness acceleration does not introduce idle time
            // or a misleading transient "needs Source" status.
            if (needsMana && SourceUtil.takeSourceMultipleWithParticles(
                    tile.getBlockPos(),
                    level,
                    7,
                    Config.DRYGMY_MANA_COST.get()
            ) != null) {
                needsMana = false;
                tile.updateBlock();
            }
        }
    }

    @Unique
    private static ItemStack arsMatrix$adjacentTool(ServerLevel level, DrygmyTile tile) {
        // Direction.values() deliberately matches Ars Delight's pedestal choice.
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(tile.getBlockPos().relative(direction))
                    instanceof ArcanePedestalTile pedestal) {
                ItemStack stack = pedestal.getItem(0);
                if (!stack.isEmpty() && !stack.isStackable()) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
