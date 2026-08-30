package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.ritual.RitualHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the Harvest ritual treat the two Sniffer crops like renewable crops. */
@Mixin(value = RitualHarvest.class, remap = false)
public abstract class RitualHarvestAncientCropMixin {
    private static final int ARS_MATRIX$HARVEST_RADIUS = 4;

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void arsMatrix$harvestAncientCrops(CallbackInfo ci) {
        RitualHarvest ritual = (RitualHarvest) (Object) this;
        Level level = ritual.getWorld();
        BlockPos origin = ritual.getPos();
        if (!(level instanceof ServerLevel) || origin == null || level.getGameTime() % 200L != 0L) return;

        boolean harvested = false;
        for (BlockPos cursor : BlockPos.betweenClosed(
                origin.offset(-ARS_MATRIX$HARVEST_RADIUS, -1, -ARS_MATRIX$HARVEST_RADIUS),
                origin.offset(ARS_MATRIX$HARVEST_RADIUS, 1, ARS_MATRIX$HARVEST_RADIUS))) {
            BlockPos pos = cursor.immutable();
            BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.TORCHFLOWER)) {
                ritual.processAndSpawnDrops(pos, state, level, false);
                level.setBlockAndUpdate(pos, Blocks.TORCHFLOWER_CROP.defaultBlockState()
                        .setValue(TorchflowerCropBlock.AGE, 1));
                harvested = true;
                continue;
            }

            if (!state.is(Blocks.PITCHER_CROP)
                    || state.getValue(PitcherCropBlock.HALF) != DoubleBlockHalf.LOWER
                    || state.getValue(PitcherCropBlock.AGE) < PitcherCropBlock.MAX_AGE) continue;

            ritual.processAndSpawnDrops(pos, state, level, false);
            level.setBlockAndUpdate(pos, Blocks.PITCHER_CROP.defaultBlockState()
                    .setValue(PitcherCropBlock.AGE, 1)
                    .setValue(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER));
            BlockPos upper = pos.above();
            if (level.getBlockState(upper).is(Blocks.PITCHER_CROP)) {
                level.removeBlock(upper, false);
            }
            harvested = true;
        }

        if (harvested) {
            level.playSound(null, origin, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }
}
