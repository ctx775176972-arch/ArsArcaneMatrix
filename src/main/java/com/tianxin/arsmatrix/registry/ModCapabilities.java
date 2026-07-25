package com.tianxin.arsmatrix.registry;

import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Ars Nouveau capability registrations exposed by this mod. */
public final class ModCapabilities {

    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ModBlockEntities.MATRIX_CORE.get(),
                (blockEntity, direction) -> blockEntity.getSourceStorage()
        );
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ModBlockEntities.ARCANE_MINE_CORE.get(),
                (blockEntity, direction) -> blockEntity.getSourceStorage()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ARCANE_MINE_CORE.get(),
                (blockEntity, direction) -> blockEntity.getItemHandler(direction)
        );
    }
}
