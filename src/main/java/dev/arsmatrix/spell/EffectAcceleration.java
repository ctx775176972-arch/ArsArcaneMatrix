package dev.arsmatrix.spell;

import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/** Temporary extra ticks, independent of world time and without chunk loading. */
public final class EffectAcceleration extends AbstractEffect {
    public static final EffectAcceleration INSTANCE = new EffectAcceleration();
    private static final Map<ServerLevel, Map<BlockPos, Target>> TARGETS = new WeakHashMap<>();
    private static final int DURATION = 600;
    private static final Set<net.minecraft.server.level.ServerPlayer> HUD_VIEWERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        TARGETS.clear();
        HUD_VIEWERS.clear();
    }

    private EffectAcceleration() {
        super(ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "glyph_acceleration"), "Acceleration");
    }

    @Override public void onResolveBlock(BlockHitResult hit, Level level, LivingEntity shooter,
            SpellStats stats, SpellContext context, SpellResolver resolver) {
        if (!(level instanceof ServerLevel server) || !server.hasChunkAt(hit.getBlockPos())) return;
        BlockPos pos = hit.getBlockPos().immutable();
        BlockState state = server.getBlockState(pos);
        if (isBlacklisted(state.getBlock())) return;
        BlockEntity entity = server.getBlockEntity(pos);
        if (!state.isRandomlyTicking() && (entity == null || ticker(server, state, entity) == null)) return;
        int multiplier = 1 << (1 + Math.clamp(stats.getBuffCount(AugmentAmplify.INSTANCE), 0, 8));
        // One entry per position: recasting replaces the rate and refreshes duration, never multiplies it.
        TARGETS.computeIfAbsent(server, ignored -> new HashMap<>()).put(pos,
                new Target(state.getBlock(), entity, multiplier, server.getGameTime() + DURATION));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Map<BlockPos, Target> targets = TARGETS.get(level);
        if (targets == null || targets.isEmpty()) {
            syncLookedAtStatus(level, targets);
            return;
        }
        // Tickers can indirectly cast spells; don't iterate the live map during callbacks.
        for (var entry : new ArrayList<>(targets.entrySet())) {
            BlockPos pos = entry.getKey();
            Target target = entry.getValue();
            if (level.getGameTime() >= target.expires || !level.hasChunkAt(pos)
                    || isBlacklisted(target.block)) {
                targets.remove(pos, target);
                continue;
            }
            try {
                for (int i = 1; i < target.multiplier; i++) {
                    if (!level.hasChunkAt(pos)) { targets.remove(pos, target); break; }
                    BlockState state = level.getBlockState(pos);
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (state.getBlock() != target.block || entity != target.entity
                            || (entity != null && entity.isRemoved())) {
                        targets.remove(pos, target);
                        break;
                    }
                    if (entity != null) {
                        BlockEntityTicker<BlockEntity> ticker = ticker(level, state, entity);
                        if (ticker != null) ticker.tick(level, pos, state, entity);
                    } else if (state.isRandomlyTicking()) {
                        state.randomTick(level, pos, level.random);
                    }
                }
            } catch (Exception exception) {
                targets.remove(pos, target);
                ArsArcaneMatrix.LOGGER.error("Stopped acceleration at {} in {} after a target tick failed",
                        pos, level.dimension().location(), exception);
            }
        }
        syncLookedAtStatus(level, targets);
    }

    private static void syncLookedAtStatus(ServerLevel level, Map<BlockPos, Target> targets) {
        if (level.getGameTime() % 5 != 0) return;
        for (var player : level.players()) {
            var hit = player.pick(8.0D, 1.0F, false);
            BlockPos pos = hit instanceof BlockHitResult blockHit
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                    ? blockHit.getBlockPos() : null;
            Target target = pos == null || targets == null ? null : targets.get(pos);
            if (target != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new dev.arsmatrix.network.AccelerationStatusPayload(level.dimension().location(), pos,
                                target.multiplier, (int) Math.max(0, target.expires - level.getGameTime())));
                HUD_VIEWERS.add(player);
            } else if (HUD_VIEWERS.remove(player)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new dev.arsmatrix.network.AccelerationStatusPayload(level.dimension().location(),
                                BlockPos.ZERO, 0, 0));
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockEntityTicker<BlockEntity> ticker(Level level, BlockState state, BlockEntity entity) {
        return state.getTicker(level, (net.minecraft.world.level.block.entity.BlockEntityType) entity.getType());
    }

    private static boolean isBlacklisted(Block block) {
        return dev.arsmatrix.config.MatrixConfig.ACCELERATION_BLOCK_BLACKLIST.get().contains(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    @Override public SpellTier defaultTier() { return SpellTier.THREE; }
    @Override public int getDefaultManaCost() { return 4000; }
    @Override public Set<AbstractAugment> getCompatibleAugments() { return augmentSetOf(AugmentAmplify.INSTANCE); }
    @Override public void addAugmentDescriptions(Map<AbstractAugment, String> descriptions) {
        super.addAugmentDescriptions(descriptions);
        descriptions.put(AugmentAmplify.INSTANCE, "Doubles the acceleration multiplier, up to 512x.");
    }
    @Override protected void addDefaultAugmentLimits(Map<ResourceLocation, Integer> limits) {
        limits.put(AugmentAmplify.INSTANCE.getRegistryName(), 8);
    }
    @Override public Set<SpellSchool> getSchools() { return setOf(SpellSchools.MANIPULATION); }
    @Override public String getBookDescription() {
        return "Accelerates the targeted ticking block for 30 seconds at 2x speed. Each Amplify doubles the rate, up to 512x. Does not accept duration augments. Recasting refreshes the effect without stacking.";
    }
    private record Target(Block block, BlockEntity entity, int multiplier, long expires) {}
}
