package com.tianxin.arsmatrix.registry;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registrations for Ars Arcane Matrix. */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ArsArcaneMatrix.MOD_ID);

    public static final DeferredItem<BlockItem> MATRIX_CORE =
            ITEMS.registerSimpleBlockItem("matrix_core", ModBlocks.MATRIX_CORE, new Item.Properties());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
