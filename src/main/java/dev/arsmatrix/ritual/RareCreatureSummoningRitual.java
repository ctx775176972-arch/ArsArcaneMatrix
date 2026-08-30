package dev.arsmatrix.ritual;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** One-shot ritual that summons one difficult-to-renew creature and then ends. */
public final class RareCreatureSummoningRitual extends AbstractRitual {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "rare_creature_summoning");

    @Override
    protected void tick() {
        if (!(getWorld() instanceof ServerLevel serverLevel) || getPos() == null || getProgress() > 0) return;
        if (!takeSourceNow()) return;

        EntityType<?> type = selectedType();
        BlockPos spawnPos = findSpawnPos(serverLevel, getPos().above());
        var entity = type.spawn(serverLevel, spawnPos, MobSpawnType.MOB_SUMMONED);
        if (entity == null) {
            setFinished();
            return;
        }
        incrementProgress();
        setFinished();
    }

    private EntityType<?> selectedType() {
        if (didConsumeItem(Blocks.REINFORCED_DEEPSLATE)) return EntityType.WARDEN;
        if (didConsumeItem(Blocks.PRISMARINE_BRICKS)) return EntityType.ELDER_GUARDIAN;
        if (didConsumeItem(Blocks.GILDED_BLACKSTONE)) return EntityType.PIGLIN_BRUTE;
        if (didConsumeItem(Blocks.PURPUR_BLOCK)) return EntityType.SHULKER;
        return EntityType.BREEZE;
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos origin) {
        for (int radius = 0; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos candidate = origin.offset(x, 0, z);
                    if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
                        return candidate;
                    }
                }
            }
        }
        return origin;
    }

    @Override
    public boolean canConsumeItem(ItemStack stack) {
        if (!getConsumedItems().isEmpty()) return false;
        return stack.is(Blocks.REINFORCED_DEEPSLATE.asItem())
                || stack.is(Blocks.PRISMARINE_BRICKS.asItem())
                || stack.is(Blocks.GILDED_BLACKSTONE.asItem())
                || stack.is(Blocks.PURPUR_BLOCK.asItem());
    }

    @Override public int getSourceCost() { return 10_000; }
    @Override public ResourceLocation getRegistryName() { return ID; }
    @Override public String getLangName() { return "Ritual of Rare Summoning"; }
    @Override public String getLangDescription() {
        return "Summons one rare creature sample and immediately ends.";
    }
}
