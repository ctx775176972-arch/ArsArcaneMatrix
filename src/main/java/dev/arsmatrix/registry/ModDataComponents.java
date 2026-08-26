package dev.arsmatrix.registry;

import com.mojang.serialization.Codec;
import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persistent item components used by upgradeable single-block machines. */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ArsArcaneMatrix.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            PATTERN_PROVIDER_TIER = COMPONENTS.registerComponentType(
                    "pattern_provider_tier",
                    builder -> builder.persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            ORDER_PEDESTAL_TIER = COMPONENTS.registerComponentType(
                    "order_pedestal_tier",
                    builder -> builder.persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            LOGISTICS_HUB_TIER = COMPONENTS.registerComponentType(
                    "logistics_hub_tier",
                    builder -> builder.persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            STOCK_REQUESTER_TIER = COMPONENTS.registerComponentType(
                    "stock_requester_tier",
                    builder -> builder.persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
