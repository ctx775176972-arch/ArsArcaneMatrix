package dev.arsmatrix.util;

import com.hollingsworth.arsnouveau.common.entity.ScryerCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Shared distance validation for block menus that may be used through a Scryer's Crystal. */
public final class RemoteMenuAccess {
    private RemoteMenuAccess() {
    }

    public static boolean isWithinUseRange(Player player, BlockPos target) {
        if (!player.level().hasChunkAt(target)) return false;
        // The client player remains at the original body position while Ars renders
        // through a Scryer Camera. The server owns the authoritative distance check.
        if (player.level().isClientSide) return true;
        Entity origin = player;
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.getCamera() instanceof ScryerCamera camera
                && camera.level() == player.level()) {
            origin = camera;
        }
        return origin.distanceToSqr(target.getCenter()) <= 64.0D;
    }
}
