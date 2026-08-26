package dev.arsmatrix.spell;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellSchool;
import com.hollingsworth.arsnouveau.api.spell.SpellSchools;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.common.ritual.RitualMobCapture;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Map;
import java.util.Set;

/** A portable, tier-three counterpart to the Containment ritual. */
public final class EffectCapture extends AbstractEffect {
    public static final EffectCapture INSTANCE = new EffectCapture();
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "capture_bosses")
    );

    private EffectCapture() {
        super(ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "glyph_capture"), "Capture");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level, LivingEntity shooter, SpellStats stats,
                                SpellContext context, SpellResolver resolver) {
        if (level.isClientSide || !(shooter instanceof ServerPlayer player)) {
            return;
        }
        if (!(hit.getEntity() instanceof LivingEntity hitTarget) || hitTarget instanceof Player
                || hitTarget == shooter || hitTarget.isDeadOrDying()) {
            notify(player, "message.ars_arcane_matrix.capture.invalid_target");
            return;
        }

        if (hasPlayerPassenger(hitTarget)) {
            notify(player, "message.ars_arcane_matrix.capture.riding");
            return;
        }
        LivingEntity target = topmostLivingPassenger(hitTarget);
        boolean capturedPassengerFirst = target != hitTarget;

        boolean bossMode = stats.hasBuff(AugmentAmplify.INSTANCE);
        boolean boss = target.getType().is(BOSSES);
        boolean validMode = bossMode ? boss : !boss;
        if (!validMode) {
            notify(player, bossMode
                    ? "message.ars_arcane_matrix.capture.requires_boss"
                    : "message.ars_arcane_matrix.capture.requires_augment");
            return;
        }
        if (!new RitualMobCapture().canJar(target)) {
            notify(player, "message.ars_arcane_matrix.capture.blacklisted");
            return;
        }
        if (!target.getPassengers().isEmpty()) {
            notify(player, "message.ars_arcane_matrix.capture.riding");
            return;
        }

        JarSlot jarSlot = findEmptyJar(player.getInventory());
        if (jarSlot == null) {
            notify(player, "message.ars_arcane_matrix.capture.no_jar");
            return;
        }
        if (jarSlot.stack().getCount() > 1 && player.getInventory().getFreeSlot() < 0) {
            notify(player, "message.ars_arcane_matrix.capture.no_space");
            return;
        }

        target.stopRiding();
        detachWorldState(target);
        CompoundTag entityTag = new CompoundTag();
        if (!target.save(entityTag) || entityTag.isEmpty()) {
            notify(player, "message.ars_arcane_matrix.capture.save_failed");
            return;
        }

        ItemStack filledJar = jarSlot.stack().copyWithCount(1);
        filledJar.set(DataComponentRegistry.MOB_JAR.get(), new MobJarData(entityTag, new CompoundTag()));
        if (jarSlot.stack().getCount() == 1) {
            player.getInventory().setItem(jarSlot.index(), filledJar);
        } else {
            jarSlot.stack().shrink(1);
            player.getInventory().add(filledJar);
        }
        target.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable(
                capturedPassengerFirst
                        ? "message.ars_arcane_matrix.capture.passenger_success"
                        : "message.ars_arcane_matrix.capture.success",
                target.getDisplayName()), true);
    }

    /** Selects one creature at a time, peeling a riding stack from its topmost passenger. */
    private static LivingEntity topmostLivingPassenger(LivingEntity root) {
        for (Entity passenger : root.getPassengers()) {
            LivingEntity nested = topmostLivingPassenger(passenger);
            if (nested != null) {
                return nested;
            }
        }
        return root;
    }

    private static LivingEntity topmostLivingPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            LivingEntity nested = topmostLivingPassenger(passenger);
            if (nested != null) {
                return nested;
            }
        }
        return entity instanceof LivingEntity living ? living : null;
    }

    private static boolean hasPlayerPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player || hasPlayerPassenger(passenger)) {
                return true;
            }
        }
        return false;
    }

    private static JarSlot findEmptyJar(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(BlockRegistry.MOB_JAR.asItem())) {
                continue;
            }
            MobJarData data = stack.get(DataComponentRegistry.MOB_JAR.get());
            if (data == null || data.entityTag().isEmpty() || data.entityTag().get().isEmpty()) {
                return new JarSlot(slot, stack);
            }
        }
        return null;
    }

    private static void detachWorldState(LivingEntity target) {
        if (target instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash(true, true);
        }
        if (target instanceof Raider raider && raider.hasActiveRaid()) {
            raider.getCurrentRaid().removeFromRaid(raider, false);
        }
        if (target instanceof Villager villager) {
            villager.releasePoi(MemoryModuleType.HOME);
            villager.releasePoi(MemoryModuleType.JOB_SITE);
            villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
            villager.releasePoi(MemoryModuleType.MEETING_POINT);
        }
    }

    private static void notify(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    public SpellTier defaultTier() {
        return SpellTier.THREE;
    }

    @Override
    public int getDefaultManaCost() {
        return 250;
    }

    @Override
    public Set<AbstractAugment> getCompatibleAugments() {
        return augmentSetOf(AugmentAmplify.INSTANCE);
    }

    @Override
    public void addAugmentDescriptions(Map<AbstractAugment, String> map) {
        super.addAugmentDescriptions(map);
        map.put(AugmentAmplify.INSTANCE, "Only captures bosses.");
    }

    @Override
    public Set<SpellSchool> getSchools() {
        return setOf(SpellSchools.CONJURATION, SpellSchools.MANIPULATION);
    }

    @Override
    public String getBookDescription() {
        return "Captures a touched non-boss creature into an empty Containment Jar in the caster's inventory. Amplify switches the glyph to boss-only capture mode.";
    }

    private record JarSlot(int index, ItemStack stack) {
    }
}
