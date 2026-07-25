package com.tianxin.arsmatrix.registry;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative mode tab registrations for Ars Arcane Matrix. */
public final class ModCreativeTabs {

    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArsArcaneMatrix.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARS_ARCANE_MATRIX =
            CREATIVE_MODE_TABS.register("ars_arcane_matrix", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ars_arcane_matrix"))
                    .icon(() -> ModItems.MATRIX_CORE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MATRIX_CORE.get());
                        output.accept(ModItems.ARCANE_MINE_CORE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
