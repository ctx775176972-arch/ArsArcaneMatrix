package dev.arsmatrix.registry;

import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Ars Nouveau capability registrations exposed by this mod. */
public final class ModCapabilities {

    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        // The advanced chamber reuses Ars Nouveau's ImbuementTile. Register the
        // item capability on our block explicitly as well: adjacent automation
        // such as Functional Storage upgrades may resolve providers by block
        // before considering the native block-entity-type registration.
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, direction) ->
                        blockEntity instanceof ImbuementTile tile ? new InvWrapper(tile) : null,
                ModBlocks.ADVANCED_IMBUEMENT_CHAMBER.get()
        );
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
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ModBlockEntities.ARCANE_IMBUEMENT_CORE.get(),
                (blockEntity, direction) -> blockEntity.getSourceStorage()
        );
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ModBlockEntities.SUPER_SOURCE_JAR_CORE.get(),
                (blockEntity, direction) -> blockEntity.getSourceStorage()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SOURCE_STONE_GENERATOR.get(),
                (blockEntity, direction) -> blockEntity.getItemHandler()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DRYGMY_ARENA.get(),
                (blockEntity, direction) -> blockEntity.getItemHandler()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.AUTOMATIC_STOCK_REQUESTER.get(),
                (blockEntity, direction) -> blockEntity.getCatalystHandler()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.STARBUNCLE_LOGISTICS_HUB.get(),
                (blockEntity, direction) -> blockEntity.getInventory()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.STORAGE_GRID_DIRECTORY.get(),
                (blockEntity, direction) -> blockEntity.getStorage()
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.ARCANE_FLUID_RESERVOIR.get(),
                (blockEntity, direction) -> blockEntity.getFluidHandler(direction)
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.ARCANE_FLUID_TANK.get(),
                (blockEntity, direction) -> blockEntity.fluidHandler()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ARCANE_FLUID_RESERVOIR.get(),
                (blockEntity, direction) -> blockEntity.getUpgrades()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ARCANE_REACTION_VESSEL.get(),
                (blockEntity, direction) -> blockEntity.itemHandler(direction)
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.ARCANE_REACTION_VESSEL.get(),
                (blockEntity, direction) -> blockEntity.fluidHandler(direction)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ARCANE_VACUUM_HOPPER.get(),
                (blockEntity, direction) -> blockEntity.automationItems()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SOURCE_STONE_FURNACE.get(),
                (blockEntity, direction) -> blockEntity.itemHandler(direction)
        );
    }
}
