package dev.arsmatrix.world;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** Owns persistent, fully ticking chunk tickets for Dimension Anchors. */
public final class ModChunkLoading {
    public static final TicketController DIMENSION_ANCHOR = new TicketController(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "dimension_anchor"),
            (level, helper) -> helper.getBlockTickets().keySet().forEach(owner -> {
                if (!level.getBlockState(owner).is(ModBlocks.DIMENSION_ANCHOR.get())) {
                    helper.removeAllTickets(owner);
                }
            }));

    private ModChunkLoading() {}

    public static void registerControllers(RegisterTicketControllersEvent event) {
        event.register(DIMENSION_ANCHOR);
    }
}
