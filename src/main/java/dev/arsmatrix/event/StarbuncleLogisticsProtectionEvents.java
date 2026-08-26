package dev.arsmatrix.event;

import com.hollingsworth.arsnouveau.common.entity.Starbuncle;
import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;

/** One shared listener protects every currently hub-managed Starbuncle. */
public final class StarbuncleLogisticsProtectionEvents {
    private StarbuncleLogisticsProtectionEvents() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Starbuncle starbuncle)) return;
        long protectedUntil = starbuncle.getPersistentData().getLong(
                StarbuncleLogisticsHubBlockEntity.PROTECTED_UNTIL_TAG);
        if (protectedUntil >= starbuncle.level().getGameTime()) event.setCanceled(true);
    }

    /** Ars Nouveau plays this as a world sound, so Entity#setSilent cannot suppress it. */
    public static void onLevelSound(PlayLevelSoundEvent.AtPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.getSound() == null
                || event.getSound().value() != SoundEvents.ITEM_PICKUP) return;
        var position = event.getPosition();
        AABB exactArea = new AABB(position, position).inflate(0.35D);
        boolean managedStarbuncle = !level.getEntitiesOfClass(
                Starbuncle.class, exactArea, starbuncle ->
                        starbuncle.getPersistentData().getLong(
                                StarbuncleLogisticsHubBlockEntity.PROTECTED_UNTIL_TAG)
                                >= level.getGameTime()).isEmpty();
        if (managedStarbuncle) event.setCanceled(true);
    }
}
