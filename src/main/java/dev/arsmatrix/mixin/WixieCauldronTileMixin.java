package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.PotionJarTile;
import com.hollingsworth.arsnouveau.common.block.tile.WixieCauldronTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets a nearby potion jar act as the target sample for native Wixie brewing.
 * Native pedestal targets remain first in the rotation and potion-jar targets
 * are appended after them. The jar fluid itself is never consumed as a recipe
 * marker.
 */
@Mixin(value = WixieCauldronTile.class, remap = false)
public abstract class WixieCauldronTileMixin {
    @Redirect(method = "rotateCraft", at = @At(value = "INVOKE",
            target = "Ljava/util/List;isEmpty()Z", ordinal = 0), remap = false)
    private boolean arsMatrix$usePotionJarsAsTargets(List<ItemStack> targets) {
        WixieCauldronTile cauldron = (WixieCauldronTile) (Object) this;
        if (cauldron.getLevel() == null) {
            return true;
        }

        Set<PotionContents> addedPotions = new HashSet<>();
        // Match Ars Nouveau's native pedestal scan exactly: only the 3x3x3
        // cube immediately surrounding the cauldron defines crafting targets.
        // Jars farther away remain available as potion inputs/outputs without
        // accidentally becoming recipe samples.
        BlockPos leftBound = cauldron.getBlockPos().below().south().east();
        BlockPos rightBound = cauldron.getBlockPos().above().north().west();
        for (BlockPos pos : BlockPos.betweenClosed(leftBound, rightBound)) {
            if (!(cauldron.getLevel().getBlockEntity(pos) instanceof PotionJarTile jar)) {
                continue;
            }
            PotionContents contents = jar.getData();
            if (contents.equals(PotionContents.EMPTY)
                    || jar.getAmount() >= jar.getMaxFill()
                    || !addedPotions.add(contents)) {
                continue;
            }
            ItemStack potionTarget = new ItemStack(Items.POTION);
            potionTarget.set(DataComponents.POTION_CONTENTS, contents);
            targets.add(potionTarget);
        }
        return targets.isEmpty();
    }
}
